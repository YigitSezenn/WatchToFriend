package com.watch.watchtofriend.ui.watch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.Config
import com.watch.watchtofriend.data.model.Message
import com.watch.watchtofriend.data.model.QueueItem
import com.watch.watchtofriend.data.model.Room
import com.watch.watchtofriend.data.model.User
import com.watch.watchtofriend.data.model.WatchHistory
import com.watch.watchtofriend.data.model.YtSearchResult
import com.watch.watchtofriend.data.repository.RoomRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class WatchViewModel(
    private val roomId: String,
    private val appContext: Context? = null,
    internal val repo: RoomRepository = RoomRepository()
) : ViewModel() {
    val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val _displayName = MutableStateFlow("Sen")
    private var _myPhoto: String = ""
    val myPhoto: String get() = _myPhoto
    val myDisplayName: String get() = _displayName.value

    // ── Sesli sohbet ──────────────────────────────────────────────────────
    private var voiceManager: VoiceManager? = null

    val voiceInVoice get() = voiceManager?.inVoice
    val voiceMuted   get() = voiceManager?.muted
    val voiceSpeaking get() = voiceManager?.speakingUids

    fun initVoice(context: Context) {
        if (voiceManager != null) return
        voiceManager = VoiceManager(
            context.applicationContext,
            roomId,
            uid,
            _displayName.value.ifBlank {
                context.applicationContext.getString(R.string.common_user)
            },
            _myPhoto,
        )
    }

    fun joinVoice(context: Context) {
        initVoice(context)
        voiceManager?.join()
    }

    fun leaveVoice() { voiceManager?.leave() }

    fun toggleVoiceMute() { voiceManager?.toggleMute() }

    val room: StateFlow<Room?> = repo.observeRoom(roomId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = repo.observeMessages(roomId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isHost: Boolean get() = room.value?.hostUid == uid

    private val _friends = MutableStateFlow<List<User>>(emptyList())
    val friends: StateFlow<List<User>> = _friends

    // Gönderdiğim bekleyen arkadaşlık istekleri (alıcı uid'leri) — CANLI, odadan
    // çıkıp girince sıfırlanmaz; tekrar istek göndermeyi engeller.
    private val _sentFriendRequests = MutableStateFlow<Set<String>>(emptySet())
    val sentFriendRequests: StateFlow<Set<String>> = _sentFriendRequests

    private val _roomDeleted = MutableStateFlow(false)
    val roomDeleted: StateFlow<Boolean> = _roomDeleted

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast

    // Gönderen uid -> profil fotoğrafı önbelleği (mesaja gömmek yerine bir kez çekilir)
    private val _senderPhotos = MutableStateFlow<Map<String, String>>(emptyMap())
    val senderPhotos: StateFlow<Map<String, String>> = _senderPhotos
    // Fotoğraf önbelleği yazma kilidi — eş zamanlı collect'lerde race condition'ı önler.
    private val senderPhotosMutex = Mutex()

    // Sunucu-saati offset'i: odaya girince ölç, periyodik tazele (saat kaymasını giderir)
    fun serverNow(): Long = repo.serverNow()

    init {
        viewModelScope.launch {
            repo.fetchServerOffset(uid)
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(300000) // 5 dk'da bir tazele
                repo.fetchServerOffset(uid)
            }
        }
        viewModelScope.launch {
            try {
                repo.getUser(uid)?.let {
                    _displayName.value = it.displayName
                    _myPhoto = it.photoBase64
                    _blocked.value = it.blockedIds.toSet()
                    _senderPhotos.value = _senderPhotos.value + (uid to it.photoBase64)
                    // Gerçek ad yüklendi → presence'ı doğru adla hemen güncelle
                    heartbeat()
                }
            } catch (_: Exception) { /* ağ yoksa sessiz geç */ }
        }
        // Arkadaş listesini CANLI izle (davet + "zaten arkadaş" durumu için)
        viewModelScope.launch {
            try { repo.observeFriends(uid).collect { _friends.value = it } } catch (_: Exception) {}
        }
        // Gönderdiğim bekleyen arkadaşlık isteklerini CANLI izle
        viewModelScope.launch {
            try { repo.observeOutgoingFriendRequests(uid).collect { _sentFriendRequests.value = it } } catch (_: Exception) {}
        }
        // Mesajlardaki yeni gönderenlerin fotoğrafını bir kez çek + önbellekle.
        // Mutex ile korunur: eş zamanlı emit'lerde read-modify-write yarışı olmaz.
        viewModelScope.launch {
            messages.collect { msgs ->
                val need = msgs.map { it.senderUid }.toSet()
                    .filter { it.isNotBlank() && !_senderPhotos.value.containsKey(it) }
                if (need.isEmpty()) return@collect
                senderPhotosMutex.withLock {
                    // Kilit alındıktan sonra tekrar filtrele — başka coroutine zaten eklediyse atlat
                    val stillNeed = need.filter { !_senderPhotos.value.containsKey(it) }
                    if (stillNeed.isEmpty()) return@withLock
                    val fetched = mutableMapOf<String, String>()
                    stillNeed.forEach { sid ->
                        val photo = try { repo.getUser(sid)?.photoBase64 ?: "" } catch (_: Exception) { "" }
                        fetched[sid] = photo
                    }
                    _senderPhotos.value = _senderPhotos.value + fetched
                }
            }
        }
    }

    fun sendMessage(text: String, onResult: (Boolean) -> Unit = {}) {
        val t = text.trim()
        if (t.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ctx = appContext ?: return@launch
            val myUid = uid
            if (myUid.isBlank()) {
                _toast.tryEmit(ctx.getString(R.string.dm_err_session))
                onResult(false)
                return@launch
            }
            val ok = try {
                repo.sendMessage(
                    roomId,
                    Message(
                        senderUid = myUid,
                        senderName = _displayName.value,
                        text = t,
                        timestamp = repo.serverNow()
                    )
                )
                true
            } catch (_: Exception) {
                _toast.tryEmit(ctx.getString(R.string.watch_err_send))
                false
            }
            onResult(ok)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { runCatching { repo.deleteMessage(roomId, messageId) } }
    }

    fun updateVideoState(isPlaying: Boolean, positionMs: Long) {
        viewModelScope.launch {
            try {
                repo.updateVideoState(roomId, isPlaying, positionMs, uid)
            } catch (_: Exception) {
            }
        }
    }

    fun heartbeat() {
        viewModelScope.launch {
            try {
                repo.setPresence(roomId, uid, _displayName.value)
            } catch (_: Exception) {
            }
        }
    }

    fun clearMyPresence() {
        viewModelScope.launch {
            try {
                repo.clearPresence(roomId, uid)
            } catch (_: Exception) {
            }
        }
    }

    fun sendClick(selector: String) {
        if (selector.isBlank()) return
        viewModelScope.launch {
            try {
                repo.sendClick(roomId, selector, uid)
            } catch (_: Exception) {
            }
        }
    }

    fun navigateTo(url: String) {
        val u = url.trim()
        // Sadece http(s) URL'lerine izin ver — javascript: / data: gibi schemeler XSS açığı yaratır.
        if (u.isBlank() || (!u.startsWith("http://") && !u.startsWith("https://"))) return
        viewModelScope.launch {
            try {
                repo.navigateTo(roomId, u, uid)
            } catch (_: Exception) {
            }
        }
    }

    // ---- Paylaşımlı sıra ----
    fun addToQueue(url: String) {
        val u = url.trim()
        // Sadece http(s) URL'lerine izin ver — javascript: / data: gibi schemeler XSS açığı yaratır.
        if (u.isBlank() || (!u.startsWith("http://") && !u.startsWith("https://"))) return
        viewModelScope.launch {
            val title = fetchTitle(u)
            val item = QueueItem(
                id = UUID.randomUUID().toString(),
                url = u, title = title,
                addedBy = uid, addedByName = _displayName.value
            )
            try { repo.addToQueue(roomId, item) } catch (_: Exception) {}
        }
    }

    // Arama sonucunu doğrudan sıraya ekle (başlık zaten elde)
    fun addToQueueResult(r: YtSearchResult) {
        val item = QueueItem(
            id = UUID.randomUUID().toString(),
            url = "https://www.youtube.com/watch?v=${r.videoId}",
            title = r.title, addedBy = uid, addedByName = _displayName.value
        )
        viewModelScope.launch { try { repo.addToQueue(roomId, item) } catch (_: Exception) {} }
    }

    // ---- YouTube arama (Data API) ----
    private val _ytResults = MutableStateFlow<List<YtSearchResult>>(emptyList())
    val ytResults: StateFlow<List<YtSearchResult>> = _ytResults
    private val _ytSearching = MutableStateFlow(false)
    val ytSearching: StateFlow<Boolean> = _ytSearching

    fun clearYtResults() { _ytResults.value = emptyList() }

    fun searchYouTube(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _ytSearching.value = true
            _ytResults.value = withContext(Dispatchers.IO) { fetchYouTube(q) }
            _ytSearching.value = false
        }
    }

    private fun fetchYouTube(q: String): List<YtSearchResult> {
        return try {
            val api = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=20" +
                "&q=" + URLEncoder.encode(q, "UTF-8") + "&key=" + Config.YOUTUBE_API_KEY
            val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 6000
            }
            if (conn.responseCode != 200) return emptyList()
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(txt).optJSONArray("items") ?: return emptyList()
            val results = (0 until items.length()).mapNotNull { i ->
                val it = items.optJSONObject(i) ?: return@mapNotNull null
                val vid = it.optJSONObject("id")?.optString("videoId").orEmpty()
                val sn = it.optJSONObject("snippet")
                if (vid.isBlank() || sn == null) return@mapNotNull null
                val thumb = sn.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url").orEmpty()
                YtSearchResult(vid, sn.optString("title"), sn.optString("channelTitle"), thumb)
            }
            filterEmbeddableVideos(results)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** YouTube embed kapalı videoları arama sonuçlarından çıkar */
    private fun filterEmbeddableVideos(results: List<YtSearchResult>): List<YtSearchResult> {
        if (results.isEmpty()) return results
        return try {
            val ids = results.joinToString(",") { it.videoId }
            val api = "https://www.googleapis.com/youtube/v3/videos?part=status&id=$ids&key=${Config.YOUTUBE_API_KEY}"
            val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 6000
            }
            if (conn.responseCode != 200) return results
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(txt).optJSONArray("items") ?: return results
            val allowed = mutableSetOf<String>()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val id = it.optString("id")
                if (id.isBlank()) continue
                if (it.optJSONObject("status")?.optBoolean("embeddable", true) != false) {
                    allowed.add(id)
                }
            }
            results.filter { it.videoId in allowed }
        } catch (_: Exception) {
            results
        }
    }

    fun removeQueueItem(item: QueueItem) {
        viewModelScope.launch { try { repo.removeFromQueue(roomId, item) } catch (_: Exception) {} }
    }

    fun playQueueItem(item: QueueItem) {
        viewModelScope.launch { try { repo.playFromQueue(roomId, item, uid) } catch (_: Exception) {} }
    }

    // Video bitti → host sıradakine geçirir
    fun onVideoEnded() {
        if (room.value?.hostUid != uid) return
        viewModelScope.launch { try { repo.advanceQueue(roomId, uid) } catch (_: Exception) {} }
    }

    // YouTube oEmbed ile başlık çek (anahtarsız); olmazsa URL'yi kullan
    private suspend fun fetchTitle(url: String): String = withContext(Dispatchers.IO) {
        if (!url.contains("youtu")) return@withContext url
        try {
            val api = "https://www.youtube.com/oembed?url=" +
                URLEncoder.encode(url, "UTF-8") + "&format=json"
            val conn = (URL(api).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000; readTimeout = 4000
            }
            if (conn.responseCode == 200) {
                val txt = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(txt).optString("title").ifBlank { url }
            } else url
        } catch (e: Exception) {
            url
        }
    }

    // ---- Emoji tepkisi & yazıyor ----
    fun sendReaction(emoji: String) {
        viewModelScope.launch { try { repo.sendReaction(roomId, emoji, uid) } catch (_: Exception) {} }
    }

    fun setTyping() {
        viewModelScope.launch { try { repo.setTyping(roomId, uid) } catch (_: Exception) {} }
    }

    fun changeVideo(url: String) {
        val u = url.trim()
        // Sadece http(s) URL'lerine izin ver — javascript: / data: gibi schemeler XSS açığı yaratır.
        if (u.isBlank() || (!u.startsWith("http://") && !u.startsWith("https://"))) return
        viewModelScope.launch {
            try {
                repo.updateVideoUrl(roomId, u, uid)
            } catch (_: Exception) {
            }
        }
    }

    fun sendFriendRequest(toUid: String) {
        if (toUid.isBlank() || toUid == uid) return
        viewModelScope.launch {
            try { repo.sendFriendRequest(uid, _displayName.value, toUid) } catch (_: Exception) {}
        }
    }

    // Video kontrolüne yetkili mi? (host veya yetkili liste)
    fun canControl(checkUid: String = uid): Boolean {
        val r = room.value ?: return false
        return r.hostUid == checkUid || r.moderators.contains(checkUid)
    }

    fun setModerator(targetUid: String, makeModerator: Boolean) {
        if (targetUid.isBlank() || targetUid == uid) return
        viewModelScope.launch {
            try { repo.setModerator(roomId, targetUid, makeModerator) } catch (_: Exception) {}
        }
    }

    // Oda sahibini başka bir üyeye devret (yalnızca host UI'da gösterir)
    fun transferHost(newHostUid: String) {
        if (newHostUid.isBlank() || newHostUid == uid) return
        viewModelScope.launch {
            try { repo.transferHost(roomId, newHostUid) } catch (_: Exception) {}
        }
    }

    fun updateRoomSettings(title: String, discoverable: Boolean) {
        viewModelScope.launch {
            try { repo.updateRoomSettings(roomId, title.trim(), discoverable) } catch (_: Exception) {}
        }
    }

    // Katılımcının arkadaşım olup olmadığı (UI'da "ekle" düğmesi için)
    fun isFriend(otherUid: String): Boolean = _friends.value.any { it.uid == otherUid }

    fun blockUser(otherUid: String) {
        if (otherUid.isBlank() || otherUid == uid) return
        viewModelScope.launch {
            try {
                repo.blockUser(uid, otherUid)
                _blocked.value = _blocked.value + otherUid
            } catch (_: Exception) {}
        }
    }

    fun reportUser(aboutUid: String, reason: String) {
        viewModelScope.launch {
            try { repo.reportUser(uid, aboutUid, reason, roomId) } catch (_: Exception) {}
        }
    }

    fun inviteFriend(friendUid: String) {
        viewModelScope.launch {
            try {
                repo.sendRoomInvite(uid, _displayName.value, friendUid, roomId)
            } catch (_: Exception) {
            }
        }
    }

    private fun saveHistory() {
        val r = room.value ?: return
        val entry = WatchHistory(
            videoUrl = r.videoUrl,
            title = r.title.ifBlank { r.videoUrl.take(60) },
            roomId = roomId,
            memberCount = r.memberUids.size,
            watchedAt = repo.serverNow()
        )
        viewModelScope.launch { try { repo.addToHistory(uid, entry) } catch (_: Exception) {} }
    }

    fun deleteRoom() {
        leaveVoice()
        saveHistory()
        viewModelScope.launch {
            try { repo.deleteRoom(roomId) } catch (_: Exception) {}
            _roomDeleted.value = true
        }
    }

    fun leaveRoom() {
        leaveVoice()
        saveHistory()
        viewModelScope.launch {
            // Önce "ayrıldı" sistem mesajını gönder, sonra üyelikten çık
            try {
                repo.sendSystemMessage(
                    roomId,
                    com.watch.watchtofriend.ui.locale.AppStrings.get(
                        R.string.watch_user_left,
                        _displayName.value
                    )
                )
            } catch (_: Exception) {}

            try { repo.leaveRoom(roomId, uid) } catch (_: Exception) {}
            _roomDeleted.value = true
        }
    }

    // Yetkisi olmayan üye: host/yetkililerden durdur/oynat rica eder
    fun requestControl(action: String) {
        viewModelScope.launch {
            try { repo.requestControl(roomId, uid, _displayName.value, action) } catch (_: Exception) {}
        }
    }

    fun toggleMessageReaction(messageId: String, emoji: String) {
        if (messageId.isBlank()) return
        viewModelScope.launch {
            try { repo.toggleMessageReaction(roomId, messageId, uid, emoji) } catch (_: Exception) {}
        }
    }

    fun pinMessage(text: String, senderName: String) {
        viewModelScope.launch {
            try { repo.pinMessage(roomId, text, senderName) } catch (_: Exception) {}
        }
    }

    fun unpinMessage() {
        viewModelScope.launch {
            try { repo.unpinMessage(roomId) } catch (_: Exception) {}
        }
    }

    // ---- Oylama (Poll) ----
    fun createPoll(question: String, options: List<String>) {
        if (question.isBlank() || options.size < 2) return
        viewModelScope.launch {
            try { repo.createPoll(roomId, question, options) } catch (_: Exception) {}
        }
    }

    fun votePoll(optionIndex: Int) {
        viewModelScope.launch {
            try { repo.votePoll(roomId, uid, optionIndex) } catch (_: Exception) {}
        }
    }

    fun clearPoll() {
        viewModelScope.launch {
            try { repo.clearPoll(roomId) } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager?.destroy()
        voiceManager = null
    }
}

class WatchViewModelFactory(
    private val roomId: String,
    private val context: Context? = null,
    private val repo: RoomRepository = RoomRepository()
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        WatchViewModel(roomId, context, repo) as T
}
