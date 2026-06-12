package com.watch.watchtofriend.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.watch.watchtofriend.data.model.DmConversation
import com.watch.watchtofriend.data.model.Request
import com.watch.watchtofriend.data.model.Room
import com.watch.watchtofriend.data.model.resolvedId
import com.watch.watchtofriend.notifications.NotificationHelper
import com.watch.watchtofriend.data.model.User
import com.watch.watchtofriend.data.model.WatchHistory
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.repository.AuthRepository
import com.watch.watchtofriend.data.repository.RoomRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeViewModel @JvmOverloads constructor(
    app: Application,
    // Constructor injection: test ortamında mock RoomRepository geçirilebilir.
    internal val repo: RoomRepository = RoomRepository()
) : AndroidViewModel(app) {
    private val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val _myName = MutableStateFlow("")

    private val _myProfile = MutableStateFlow<User?>(null)
    val myProfile: StateFlow<User?> = _myProfile

    // Senaryo 8: Profil güncelleme loading state
    private val _isUpdatingProfile = MutableStateFlow(false)
    val isUpdatingProfile: StateFlow<Boolean> = _isUpdatingProfile
    private val _isRemovingPhoto = MutableStateFlow(false)
    val isRemovingPhoto: StateFlow<Boolean> = _isRemovingPhoto

    // Senaryo 1: Tüm sessiz hataları kullanıcıya iletmek için event akışı
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast

    // Senaryo 2: Çift tıklama koruması — istek işlemleri için kilit
    private val requestMutex = Mutex()

    // Daha önce görülen istek ID'leri — ilk yüklemede bildirim tetiklenmesin
    private val seenRequestIds = mutableSetOf<String>()
    private var initialLoad = true
    private val lastDmUnread = mutableMapOf<String, Long>()
    private var dmNotifInitialLoad = true

    private fun observeNewRequests() {
        viewModelScope.launch {
            requests.collect { list ->
                if (initialLoad) {
                    seenRequestIds.addAll(list.map { it.id })
                    initialLoad = false
                    return@collect
                }
                list.filter { it.id !in seenRequestIds }.forEach { req ->
                    seenRequestIds.add(req.id)
                    showRequestNotification(req)
                }
            }
        }
    }

    private fun observeDmNotifications() {
        viewModelScope.launch {
            dmConversations.collect { convs ->
                if (dmNotifInitialLoad) {
                    convs.forEach { lastDmUnread[it.resolvedId(uid)] = it.unreadCount[uid] ?: 0L }
                    dmNotifInitialLoad = false
                    return@collect
                }
                val ctx = getApplication<Application>()
                convs.forEach { conv ->
                    val convId = conv.resolvedId(uid)
                    if (convId.isBlank()) return@forEach
                    val unread = conv.unreadCount[uid] ?: 0L
                    val prev = lastDmUnread[convId] ?: 0L
                    if (unread > prev) {
                        val otherUid = conv.participantUids.firstOrNull { it != uid } ?: return@forEach
                        val name = conv.participantNames[otherUid] ?: "Biri"
                        NotificationHelper.showDm(ctx, convId, name, conv.lastMessage.take(100))
                    }
                    lastDmUnread[convId] = unread
                }
            }
        }
    }

    private fun showRequestNotification(req: Request) {
        val ctx = getApplication<Application>()
        when (req.type) {
            "friend" -> NotificationHelper.showFriendRequest(ctx, req.fromName)
            "room" -> NotificationHelper.showRoomInvite(ctx, req.fromName, req.roomId)
            else -> return
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                if (uid.isNotBlank()) AuthRepository().ensureCurrentUserDocument()
                // Eski hesaplara da friendCode ata (backfill)
                if (uid.isNotBlank()) repo.ensureFriendCode(uid)
                repo.getUser(uid)?.let {
                    _myName.value = it.displayName
                    _myProfile.value = it
                    loadBlockedUsers(it.blockedIds)
                }
            } catch (_: Exception) { /* ağ yoksa sessiz geç */ }
        }
    }

    fun refreshBlockedUsers() {
        viewModelScope.launch {
            try {
                repo.getUser(uid)?.let { loadBlockedUsers(it.blockedIds) }
            } catch (_: Exception) {}
        }
    }

    private suspend fun loadBlockedUsers(ids: List<String>) {
        if (ids.isEmpty()) { _blockedUsers.value = emptyList(); return }
        _blockedUsers.value = ids.mapNotNull {
            try { repo.getUser(it) } catch (_: Exception) { null }
        }
    }

    fun unblockUser(targetUid: String) {
        viewModelScope.launch {
            try {
                repo.unblockUser(uid, targetUid)
                _blockedUsers.value = _blockedUsers.value.filter { it.uid != targetUid }
                _myProfile.value = _myProfile.value?.copy(
                    blockedIds = _myProfile.value?.blockedIds?.filter { it != targetUid } ?: emptyList()
                )
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_unblock_failed))
            }
        }
    }

    // Çevrimiçi göstergesi: son aktifliği güncelle (Home açıkken periyodik çağrılır)
    fun touchActive() {
        viewModelScope.launch { repo.touchActive(uid) }
    }

    fun updatePhoto(base64: String) {
        if (_isUpdatingProfile.value) return
        viewModelScope.launch {
            _isUpdatingProfile.value = true
            try {
                repo.updateProfilePhoto(uid, base64)
                loadProfile()
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_photo_updated))
            } catch (e: Exception) {
                val msg = e.message?.lowercase() ?: ""
                val app = getApplication<Application>()
                _toast.tryEmit(
                    if (msg.contains("longer than") || msg.contains("photoBase64"))
                        app.getString(R.string.toast_photo_too_large)
                    else
                        app.getString(R.string.toast_photo_update_failed)
                )
            } finally {
                _isUpdatingProfile.value = false
            }
        }
    }

    fun removePhoto() {
        if (_isRemovingPhoto.value || _isUpdatingProfile.value) return
        viewModelScope.launch {
            _isRemovingPhoto.value = true
            try {
                repo.updateProfilePhoto(uid, "")
                repo.getUser(uid)?.let {
                    _myName.value = it.displayName
                    _myProfile.value = it
                } ?: run {
                    _myProfile.value = _myProfile.value?.copy(photoBase64 = "")
                }
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_photo_removed))
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_photo_remove_failed))
            } finally {
                _isRemovingPhoto.value = false
            }
        }
    }

    fun updateName(name: String) {
        if (name.isBlank() || _isUpdatingProfile.value) return
        viewModelScope.launch {
            _isUpdatingProfile.value = true
            try {
                repo.updateDisplayName(uid, name)
                loadProfile()
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_name_updated))
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_name_update_failed))
            } finally {
                _isUpdatingProfile.value = false
            }
        }
    }

    // Eagerly: Firestore dinleyicisi her zaman aktif kalır; oda silinince liste anında güncellenir.
    private val _deletedRoomIds = MutableStateFlow<Set<String>>(emptySet())

    val rooms: StateFlow<List<Room>> = repo.observeMyRooms(uid)
        .combine(_deletedRoomIds) { list: List<Room>, deleted: Set<String> ->
            list.filter { it.roomId !in deleted }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Room>())

    val friends: StateFlow<List<User>> = repo.observeFriends(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<User>())

    val requests: StateFlow<List<Request>> = repo.observeRequests(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Request>())

    val publicRooms: StateFlow<List<Room>> = repo.observePublicRooms()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Room>())

    val watchHistory: StateFlow<List<WatchHistory>> = repo.observeHistory(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<WatchHistory>())

    private val _blockedUsers = MutableStateFlow<List<User>>(emptyList<User>())
    val blockedUsers: StateFlow<List<User>> = _blockedUsers

    fun deleteHistory(historyId: String) {
        viewModelScope.launch { runCatching { repo.deleteHistory(uid, historyId) } }
    }

    fun clearAllHistory() {
        viewModelScope.launch { runCatching { repo.clearAllHistory(uid) } }
    }

    val dmConversations: StateFlow<List<DmConversation>> = repo.observeDms(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // init: tüm StateFlow property'leri initialize olduktan sonra çalışır
    init {
        loadProfile()
        observeNewRequests()
        observeDmNotifications()
    }

    fun openOrCreateDm(otherUser: User, onReady: (dmId: String, otherName: String) -> Unit) {
        viewModelScope.launch {
            try {
                val me = _myProfile.value ?: repo.getUser(uid) ?: return@launch
                val dmId = repo.getOrCreateDm(
                    myUid = uid, otherUid = otherUser.uid,
                    myName = me.displayName, otherName = otherUser.displayName,
                    myPhoto = me.photoBase64, otherPhoto = otherUser.photoBase64
                )
                onReady(dmId, otherUser.displayName)
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_dm_open_failed))
            }
        }
    }

    // Açık odaya katıl; başarılıysa roomId döner (UI navigasyonu için)
    fun joinPublicRoom(roomId: String, password: String = "", onJoined: (String) -> Unit) {
        viewModelScope.launch {
            try {
                when (val result = repo.joinRoomChecked(roomId, uid, password)) {
                    is com.watch.watchtofriend.data.repository.RoomRepository.JoinResult.Success -> onJoined(roomId)
                    is com.watch.watchtofriend.data.repository.RoomRepository.JoinResult.NotFound ->
                        _toast.tryEmit(getApplication<Application>().getString(R.string.toast_room_not_found))
                    is com.watch.watchtofriend.data.repository.RoomRepository.JoinResult.WrongPassword ->
                        _toast.tryEmit(getApplication<Application>().getString(R.string.toast_wrong_password))
                    is com.watch.watchtofriend.data.repository.RoomRepository.JoinResult.Full ->
                        _toast.tryEmit(getApplication<Application>().getString(R.string.toast_room_full))
                }
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_join_failed))
            }
        }
    }

    private val _searchResult = MutableStateFlow<User?>(null)
    val searchResult: StateFlow<User?> = _searchResult

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info

    // ID (friendCode) veya e-posta ile ara. "@" varsa e-posta, yoksa ID kabul edilir.
    fun searchUser(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _searchResult.value = null
            _searchError.value = null
            try {
                val user = if (q.contains("@")) repo.searchUser(q) else repo.searchUserByCode(q)
                val app = getApplication<Application>()
                if (user == null) _searchError.value = app.getString(R.string.toast_user_not_found)
                else if (user.uid == uid) _searchError.value = app.getString(R.string.toast_cannot_add_self)
                else _searchResult.value = user
            } catch (_: Exception) {
                _searchError.value = getApplication<Application>().getString(R.string.toast_search_failed)
            }
        }
    }

    fun sendFriendRequest(friendUid: String) {
        viewModelScope.launch {
            try {
                val ok = repo.sendFriendRequest(uid, _myName.value, friendUid)
                val app = getApplication<Application>()
                _info.value = if (ok) app.getString(R.string.toast_friend_request_sent)
                else app.getString(R.string.toast_friend_request_already)
                _searchResult.value = null
            } catch (e: Exception) {
                val app = getApplication<Application>()
                _searchError.value = app.getString(
                    R.string.toast_request_send_failed,
                    e.localizedMessage ?: app.getString(R.string.common_unknown_error)
                )
            }
        }
    }

    // Senaryo 2: Mutex ile çift tıklama koruması
    fun acceptRequest(request: Request) {
        viewModelScope.launch {
            if (!requestMutex.tryLock()) return@launch   // zaten işlemde
            try {
                repo.acceptRequest(request)
            } catch (e: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_accept_failed))
            } finally {
                requestMutex.unlock()
            }
        }
    }

    fun rejectRequest(request: Request) {
        viewModelScope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                repo.rejectRequest(request.id)
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_reject_failed))
            } finally {
                requestMutex.unlock()
            }
        }
    }

    fun clearSearch() {
        _searchResult.value = null
        _searchError.value = null
        _info.value = null
    }

    fun deleteRoom(roomId: String) {
        _deletedRoomIds.value = _deletedRoomIds.value + roomId
        viewModelScope.launch {
            try {
                repo.deleteRoom(roomId)
            } catch (_: Exception) {
                _deletedRoomIds.value = _deletedRoomIds.value - roomId
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_delete_room_failed))
            }
        }
    }

    fun leaveRoomLocal(roomId: String) {
        _deletedRoomIds.value = _deletedRoomIds.value + roomId
    }

    fun removeFriend(friendUid: String) {
        viewModelScope.launch {
            try {
                repo.removeFriend(uid, friendUid)
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.toast_remove_friend_failed))
            }
        }
    }
}

// Constructor injection için factory — test ortamında mock RoomRepository geçirilebilir.
class HomeViewModelFactory(
    private val app: Application,
    private val repo: RoomRepository = RoomRepository()
) : ViewModelProvider.AndroidViewModelFactory(app) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(app, repo) as T
}
