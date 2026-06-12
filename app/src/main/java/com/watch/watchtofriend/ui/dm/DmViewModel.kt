package com.watch.watchtofriend.ui.dm

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.watch.watchtofriend.R
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.watch.watchtofriend.data.model.Message
import com.watch.watchtofriend.data.repository.RoomRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DmViewModel(
    app: Application,
    private val dmId: String,
    internal val repo: RoomRepository = RoomRepository()
) : AndroidViewModel(app) {
    val uid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    // Senaryo 3: async Deferred — sendMessage displayName hazır olmadan çağrılsa bile bekler
    private val displayNameDeferred: Deferred<String> = viewModelScope.async {
        try { repo.getUser(uid)?.displayName.orEmpty() } catch (_: Exception) { "" }
    }

    // Senaryo 3: Çift gönderme koruması
    private val sendMutex = Mutex()

    // Hata event'leri
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast

    init {
        viewModelScope.launch {
            val myUid = uid
            if (myUid.isBlank()) return@launch
            try { repo.ensureDmDocumentForSend(dmId, myUid) } catch (e: Exception) {
                Log.w(TAG, "ensureDmDocumentForSend failed", e)
            }
            try { repo.clearDmUnread(dmId, myUid) } catch (_: Exception) {}
        }
        // Sunucu saati offset'ini ölç — mesaj zaman damgaları cross-platform tutarlı olsun.
        viewModelScope.launch {
            try { repo.fetchServerOffset(uid) } catch (_: Exception) {}
        }
    }

    val messages: StateFlow<List<Message>> = repo.observeDmMessages(dmId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                repo.toggleDmMessageReaction(dmId, messageId, uid, emoji)
            } catch (_: Exception) {
                _toast.tryEmit(getApplication<Application>().getString(R.string.dm_err_reaction))
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { runCatching { repo.deleteDmMessage(dmId, messageId) } }
    }

    fun sendMessage(text: String, onResult: (Boolean) -> Unit = {}) {
        val t = text.trim()
        if (t.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = sendMessageInternal(t)
            onResult(ok)
        }
    }

    private suspend fun sendMessageInternal(text: String): Boolean {
        val myUid = uid
        if (myUid.isBlank()) {
            _toast.tryEmit(getApplication<Application>().getString(R.string.dm_err_session))
            return false
        }
        if (!sendMutex.tryLock()) return false
        return try {
            val name = displayNameDeferred.await()
            val photo = runCatching { repo.getUser(myUid)?.photoBase64.orEmpty() }.getOrDefault("")
            repo.sendDmMessage(
                dmId,
                Message(
                    senderUid = myUid,
                    senderName = name,
                    senderPhoto = photo,
                    text = text,
                    timestamp = repo.serverNow()
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendDmMessage failed dmId=$dmId", e)
            _toast.tryEmit(getApplication<Application>().getString(R.string.dm_err_send))
            false
        } finally {
            sendMutex.unlock()
        }
    }

    companion object {
        private const val TAG = "DmViewModel"
    }
}

class DmViewModelFactory(
    private val app: Application,
    private val dmId: String,
    private val repo: RoomRepository = RoomRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DmViewModel(app, dmId, repo) as T
}
