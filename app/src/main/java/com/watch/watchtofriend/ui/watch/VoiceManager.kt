package com.watch.watchtofriend.ui.watch

/**
 * VoiceManager — Mesh P2P WebRTC sesli sohbet
 *
 * Topoloji: Full-mesh (N kullanıcı, her biri diğer herkesle doğrudan bağlantı)
 * Watch party odaları genellikle 2-5 kişi → mesh mükemmel, SFU gereksiz.
 * Audio-only: ~40-60 Kbps/bağlantı, 5 kişi = ~240 Kbps upload (tamamen yönetilebilir).
 *
 * Signaling (Firestore):
 *   rooms/{roomId}/voicePeers/{uid}           → { displayName, muted, joinedAt }
 *   rooms/{roomId}/voiceConn/{uid1_uid2}/      → { offer: {sdp,type}, answer: {sdp,type} }
 *     offerCandidates/{id}                    → { sdpMid, sdpMLineIndex, sdp }
 *     answerCandidates/{id}                   → { sdpMid, sdpMLineIndex, sdp }
 *
 * Caller/callee: uid1 < uid2 alfabetik → uid1 her zaman offerer (deterministik).
 */

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.google.firebase.firestore.ListenerRegistration
import com.watch.watchtofriend.data.FirebaseBootstrap
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.watch.watchtofriend.data.TurnConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

private const val VOICE_TAG = "VoiceManager"

class VoiceManager(
    private val context: Context,
    private val roomId: String,
    private val myUid: String,
    private var myDisplayName: String,
    private var myPhotoBase64: String = "",
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val db = Firebase.firestore

    // ── Public state ──────────────────────────────────────────────────────
    private val _inVoice   = MutableStateFlow(false)
    val inVoice: StateFlow<Boolean> = _inVoice

    private val _isJoining = MutableStateFlow(false)
    val isJoining: StateFlow<Boolean> = _isJoining

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted

    /** Discord sağırlaştır — gelen sesi keser, mikrofonu da kapatır */
    private val _deafened = MutableStateFlow(false)
    val deafened: StateFlow<Boolean> = _deafened

    /** Bas-konuş modu */
    private val _pushToTalk = MutableStateFlow(false)
    val pushToTalk: StateFlow<Boolean> = _pushToTalk

    private val _pttActive = MutableStateFlow(false)
    val pttActive: StateFlow<Boolean> = _pttActive

    private val _speakingUids = MutableStateFlow<Set<String>>(emptySet())
    val speakingUids: StateFlow<Set<String>> = _speakingUids

    /** connectionState: "connecting" | "connected" | "reconnecting" | "self" */
    data class VoicePeer(
        val uid: String,
        val displayName: String,
        val photoBase64: String = "",
        val muted: Boolean,
        val listenOnly: Boolean = false,
        val connectionState: String = "connected",
    )

    /** Profil yüklendikten sonra isim/fotoğrafı güncelle (ses kanalındaysa Firestore'a yazar). */
    fun updateMyProfile(displayName: String, photoBase64: String) {
        if (displayName.isNotBlank()) myDisplayName = displayName
        myPhotoBase64 = photoBase64
        if (!_inVoice.value) return
        scope.launch {
            try {
                val patch = mutableMapOf<String, Any>("displayName" to myDisplayName)
                if (myPhotoBase64.isNotBlank()) patch["photoBase64"] = myPhotoBase64
                peerDoc(myUid).update(patch).await()
            } catch (_: Exception) { /* sessiz */ }
        }
    }
    private val _voicePeersList = MutableStateFlow<List<VoicePeer>>(emptyList())
    val voicePeersList: StateFlow<List<VoicePeer>> = _voicePeersList

    data class VoiceEvent(val type: String, val displayName: String) // type: "joined" | "left"
    private val _voiceEvents = MutableStateFlow<List<VoiceEvent>>(emptyList())
    val voiceEvents: StateFlow<List<VoiceEvent>> = _voiceEvents
    private val prevPeers = mutableMapOf<String, String>() // uid → displayName
    private val lastRenogAt = mutableMapOf<String, Long>()

    // ── WebRTC ────────────────────────────────────────────────────────────
    private var factory: PeerConnectionFactory? = null
    private var adm: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localTrackId: String? = null

    private val pcs = mutableMapOf<String, PeerConnection>()
    private val fsListeners = mutableListOf<ListenerRegistration>()
    // Peer başına listener'lar — reconnect'te eski listener sızıntısını önler
    private val peerListeners = mutableMapOf<String, MutableList<ListenerRegistration>>()
    private var speakJob: Job? = null

    // ICE adaylarını PC hazır olana kadar tamponla (race condition önler)
    private val pendingCandidates = mutableMapOf<String, MutableList<IceCandidate>>()
    /** Offer/answer Firestore'dan PC hazır olmadan gelirse burada bekler */
    private val pendingRemoteAnswers = mutableMapOf<String, SessionDescription>()
    private val pendingRemoteOffers = mutableMapOf<String, SessionDescription>()
    // Hangi peer'lar için setup başlatıldı — duplicate setup önler
    private val setupStarted = mutableSetOf<String>()
    // Peer başına bağlantı durumu (connecting/connected/reconnecting)
    private val peerStates = mutableMapOf<String, String>()
    // Yerel (sadece bu cihaz) katılımcı ses ayarları
    private val remoteTracks = mutableMapOf<String, MutableList<AudioTrack>>()
    private val localMutedPeerUids = mutableSetOf<String>()
    private val peerVolumeLevels = mutableMapOf<String, Float>()
    private val _peerLocalMuted = MutableStateFlow<Set<String>>(emptySet())
    val peerLocalMuted: StateFlow<Set<String>> = _peerLocalMuted
    private val _peerVolumes = MutableStateFlow<Map<String, Float>>(emptyMap())
    val peerVolumes: StateFlow<Map<String, Float>> = _peerVolumes
    private var micGainLevel = 1f
    private var savedMicGainLevel = 1f
    private var speakLevelThreshold = DEFAULT_SPEAK_THRESHOLD
    private val _micGain = MutableStateFlow(1f)
    val micGain: StateFlow<Float> = _micGain
    private val _speakThreshold = MutableStateFlow(DEFAULT_SPEAK_THRESHOLD)
    val speakThreshold: StateFlow<Double> = _speakThreshold

    /** Yerel mikrofon seviyesi 0–1 — konuşma halkası / gösterge için */
    private val _localMicLevel = MutableStateFlow(0f)
    val localMicLevel: StateFlow<Float> = _localMicLevel

    // Konuşma tespiti — titreşimi azaltmak için yumuşatma + histerezis
    private val smoothedAudioLevels = mutableMapOf<String, Double>()
    private val speakActiveFrames = mutableMapOf<String, Int>()
    private var volumeMaintainJob: Job? = null
    @Volatile private var localMicRms = 0.0
    private val micRmsListener: (Double) -> Unit = { localMicRms = it }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkOnline = true
    @Volatile private var cachedIceConfig: List<PeerConnection.IceServer>? = null

    init {
        micGainLevel = VoicePrefs.loadMicGain(context)
        _micGain.value = micGainLevel
        savedMicGainLevel = if (micGainLevel > 0.01f) micGainLevel else 1f
        setSpeakThreshold(uiToThreshold(VoicePrefs.loadSpeakRmsUi(context)))
        _pushToTalk.value = VoicePrefs.loadPushToTalk(context)
    }

    companion object {
        private const val DEFAULT_SPEAK_THRESHOLD = 0.02
        private const val THRESHOLD_MIN = 0.005
        private const val THRESHOLD_MAX = 0.45
        private const val UI_THRESHOLD_MIN = 2f
        private const val UI_THRESHOLD_MAX = 25f
        /** PCM int16 RMS → 0–1 normalize (normal konuşma ~0.15–0.6) */
        private const val LOCAL_RMS_NORM_DIVISOR = 1600.0

        fun thresholdToUi(threshold: Double): Float {
            val t = threshold.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
            val span = THRESHOLD_MAX - THRESHOLD_MIN
            val uiSpan = UI_THRESHOLD_MAX - UI_THRESHOLD_MIN
            return (UI_THRESHOLD_MIN + ((t - THRESHOLD_MIN) / span * uiSpan).toFloat())
                .coerceIn(UI_THRESHOLD_MIN, UI_THRESHOLD_MAX)
        }

        fun uiToThreshold(ui: Float): Double {
            val r = ui.coerceIn(UI_THRESHOLD_MIN, UI_THRESHOLD_MAX)
            val uiSpan = UI_THRESHOLD_MAX - UI_THRESHOLD_MIN
            val span = THRESHOLD_MAX - THRESHOLD_MIN
            return THRESHOLD_MIN + (r - UI_THRESHOLD_MIN) / uiSpan * span
        }
    }

    /** Mevcut voicePeersList içindeki bağlantı durumlarını günceller */
    private fun refreshPeerStates() {
        _voicePeersList.value = _voicePeersList.value.map { peer ->
            val state = peerStates[peer.uid] ?: peer.connectionState
            peer.copy(connectionState = state)
        }
    }

    private fun bufferCandidate(peerUid: String, c: IceCandidate) {
        val pc = pcs[peerUid]
        if (pc != null && pc.remoteDescription != null) {
            pc.addIceCandidate(c)
        } else {
            pendingCandidates.getOrPut(peerUid) { mutableListOf() }.add(c)
        }
    }

    private fun drainCandidates(peerUid: String) {
        val pc = pcs[peerUid] ?: return
        pendingCandidates.remove(peerUid)?.forEach { pc.addIceCandidate(it) }
    }

    /** Peer bağlantı kaynaklarını temizle; setupStarted korunur (yeniden kurulumda) */
    private fun teardownPeerConnection(peerUid: String) {
        peerListeners.remove(peerUid)?.forEach { it.remove() }
        pcs.remove(peerUid)?.close()
        pendingCandidates.remove(peerUid)
        pendingRemoteAnswers.remove(peerUid)
        pendingRemoteOffers.remove(peerUid)
        remoteTracks.remove(peerUid)?.forEach { RoomAudioRouter.unregisterRemoteTrack(it) }
    }

    /** Bir peer ile bağlantıyı tamamen temizle — çıkış / tam reconnect */
    private fun teardownPeer(peerUid: String) {
        teardownPeerConnection(peerUid)
        setupStarted.remove(peerUid)
    }

    private fun registerRemoteTrack(peerUid: String, track: AudioTrack) {
        remoteTracks.getOrPut(peerUid) { mutableListOf() }.add(track)
        runCatching {
            if (track.state() == org.webrtc.MediaStreamTrack.State.LIVE) track.setEnabled(true)
        }
        applyPeerVolume(peerUid)
    }

    private fun effectivePeerLevel(peerUid: String): Float {
        if (_deafened.value) return 0f
        if (peerUid in localMutedPeerUids) return 0f
        return (peerVolumeLevels[peerUid] ?: 1f).coerceIn(0f, 1f)
    }

    /** Mikrofon iletimi — susturma / sağırlaştırma / bas-konuş */
    private fun updateMicTransmission() {
        val track = localAudioTrack ?: return
        val transmit = !_muted.value && !_deafened.value && micGainLevel > 0.01f &&
            (!_pushToTalk.value || _pttActive.value)
        track.setEnabled(transmit)
    }

    private fun applyPeerVolume(peerUid: String) {
        val level = effectivePeerLevel(peerUid)
        remoteTracks[peerUid]?.forEach { RoomAudioRouter.applyRemoteTrackLevel(it, level) }
    }

    private fun reapplyAllPeerVolumes() {
        remoteTracks.keys.forEach { applyPeerVolume(it) }
    }

    fun setMicGain(level: Float) {
        micGainLevel = level.coerceIn(0f, VoicePrefs.MIC_GAIN_MAX)
        _micGain.value = micGainLevel
        VoicePrefs.saveMicGain(context, micGainLevel)
        localAudioTrack?.setVolume(micGainLevel.toDouble())
        // Gain 0 = susturma; track açık kalsın (yeniden açınca çalışsın)
        updateMicTransmission()
    }

    val hasLocalMic: Boolean get() = localAudioTrack != null

    /** İzin sonradan verildiyse veya dinleme modundan mikrofona geç */
    fun enableMicrophone(): Boolean {
        if (!_inVoice.value) return false
        val ok = ensureLocalAudioTrack(true)
        if (!ok) return false
        RoomAudioRouter.registerMicRmsListener(micRmsListener)
        scope.launch {
            try {
                peerDoc(myUid).update(
                    mapOf(
                        "muted" to _muted.value,
                        "listenOnly" to false,
                        "renogAt" to System.currentTimeMillis()
                    )
                ).await()
            } catch (_: Exception) {}
            renegotiateAllPeersForMic()
        }
        return true
    }

    fun setSpeakThreshold(level: Double) {
        speakLevelThreshold = level.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
        _speakThreshold.value = speakLevelThreshold
        VoicePrefs.saveSpeakRmsUi(context, thresholdToUi(speakLevelThreshold))
    }

    /** UI kaydırıcısı (2–25): düşük = hassas, yüksek = az hassas */
    fun setSpeakRmsThreshold(rms: Float) {
        setSpeakThreshold(uiToThreshold(rms))
    }

    fun togglePeerLocalMute(peerUid: String) {
        if (peerUid == myUid) {
            if (micGainLevel > 0.01f) {
                savedMicGainLevel = micGainLevel
                setMicGain(0f)
            } else {
                setMicGain(if (savedMicGainLevel > 0.01f) savedMicGainLevel else 1f)
            }
            return
        }
        if (peerUid in localMutedPeerUids) localMutedPeerUids.remove(peerUid) else localMutedPeerUids.add(peerUid)
        _peerLocalMuted.value = localMutedPeerUids.toSet()
        applyPeerVolume(peerUid)
    }

    fun setPeerVolume(peerUid: String, level: Float) {
        if (peerUid == myUid) {
            val gain = level.coerceIn(0f, VoicePrefs.MIC_GAIN_MAX)
            if (gain > 0.01f) savedMicGainLevel = gain
            setMicGain(gain)
            return
        }
        val vol = level.coerceIn(0f, 1f)
        if (vol > 0f) localMutedPeerUids.remove(peerUid)
        peerVolumeLevels[peerUid] = vol
        _peerLocalMuted.value = localMutedPeerUids.toSet()
        _peerVolumes.value = peerVolumeLevels.toMap()
        applyPeerVolume(peerUid)
    }

    // ── Firestore path helper'ları ────────────────────────────────────────
    private fun peersCol()           = db.collection("rooms").document(roomId).collection("voicePeers")
    private fun peerDoc(uid: String) = peersCol().document(uid)
    private fun connDoc(cid: String) = db.collection("rooms").document(roomId).collection("voiceConn").document(cid)
    private fun offerCandCol(cid: String) = connDoc(cid).collection("offerCandidates")
    private fun answerCandCol(cid: String) = connDoc(cid).collection("answerCandidates")

    private fun mkConnId(a: String, b: String) = if (a < b) "${a}_${b}" else "${b}_${a}"
    private fun amOfferer(peerUid: String) = myUid < peerUid

    private fun iceCandidateFromData(d: Map<*, *>): IceCandidate? {
        val sdp = d["sdp"] as? String ?: return null
        if (sdp.isBlank()) return null
        val mid = d["sdpMid"] as? String ?: ""
        val idx = when (val v = d["sdpMLineIndex"]) {
            is Long -> v.toInt()
            is Int -> v
            is Double -> v.toInt()
            else -> 0
        }
        return IceCandidate(mid, idx, sdp)
    }

    private fun sessionDescriptionFromMap(map: Map<*, *>?): SessionDescription? {
        if (map == null) return null
        val sdp = (map["sdp"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val typeStr = (map["type"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            SessionDescription(
                SessionDescription.Type.fromCanonicalForm(typeStr),
                VoiceSdpUtil.sanitizeForBrowserInterop(sdp)
            )
        } catch (e: Exception) {
            android.util.Log.w(VOICE_TAG, "SDP ayrıştırma hatası: ${e.message}")
            null
        }
    }

    /** Karşı tarafa gönderilecek tuned SDP — setLocalDescription için DEĞİL */
    private fun signalingPayload(type: SessionDescription.Type, rawSdp: String?): Map<String, String>? {
        val sdp = rawSdp?.trim().orEmpty()
        if (sdp.isEmpty()) return null
        val tuned = VoiceSdpUtil.tuneForVoiceChat(sdp)
        if (tuned.isBlank()) return null
        return mapOf("type" to type.canonicalForm(), "sdp" to tuned)
    }

    private fun activePc(peerUid: String): PeerConnection? {
        val pc = pcs[peerUid] ?: return null
        if (pc.connectionState() == PeerConnection.PeerConnectionState.CLOSED) return null
        return pc
    }

    private fun tryApplyAnswer(peerUid: String, desc: SessionDescription) {
        val sdpText = desc.description?.trim().orEmpty()
        if (sdpText.isEmpty()) {
            android.util.Log.w(VOICE_TAG, "[$peerUid] Boş answer SDP — atlandı")
            return
        }
        val pc = activePc(peerUid) ?: run {
            pendingRemoteAnswers[peerUid] = desc
            android.util.Log.d(VOICE_TAG, "[$peerUid] Answer tampona alındı (PC hazır değil)")
            return
        }
        if (pc.remoteDescription != null) return
        if (pc.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            pendingRemoteAnswers[peerUid] = desc
            android.util.Log.d(VOICE_TAG, "[$peerUid] Answer tampona alındı (state=${pc.signalingState()})")
            return
        }
        android.util.Log.d(VOICE_TAG, "[$peerUid] Answer uygulanıyor (state=${pc.signalingState()})")
        pc.setRemoteDescription(
            SimpleSdpObserver(tag = "$peerUid:answer", onSetOk = {
                pendingRemoteAnswers.remove(peerUid)
                android.util.Log.d(VOICE_TAG, "[$peerUid] Answer uygulandı ✓")
                drainCandidates(peerUid)
            }),
            desc
        )
    }

    /** Firestore listener kaçırırsa answer için yedek poll */
    private fun startAnswerPoll(peerUid: String, cid: String) {
        scope.launch {
            repeat(30) {
                delay(2000)
                if (!_inVoice.value) return@launch
                if (activePc(peerUid)?.remoteDescription != null) return@launch
                try {
                    val snap = connDoc(cid).get().await()
                    val hasAnswer = snap.get("answer") != null
                    if (hasAnswer) {
                        sessionDescriptionFromMap(snap.get("answer") as? Map<*, *>)?.let {
                            android.util.Log.d(VOICE_TAG, "[$peerUid] Answer poll ile bulundu")
                            tryApplyAnswer(peerUid, it)
                        }
                    }
                } catch (_: Exception) {}
                if (activePc(peerUid)?.remoteDescription != null) return@launch
            }
            if (_inVoice.value && activePc(peerUid)?.remoteDescription == null) {
                android.util.Log.w(VOICE_TAG, "[$peerUid] 60s içinde answer gelmedi — Windows ses kanalında mı?")
            }
        }
    }

    private fun processOffer(peerUid: String, remoteDesc: SessionDescription, cid: String) {
        val sdpText = remoteDesc.description?.trim().orEmpty()
        if (sdpText.isEmpty()) {
            android.util.Log.w(VOICE_TAG, "[$peerUid] Boş offer SDP — atlandı")
            return
        }
        val pc = activePc(peerUid) ?: run {
            pendingRemoteOffers[peerUid] = remoteDesc
            return
        }
        if (pc.remoteDescription != null) return
        if (pc.signalingState() != PeerConnection.SignalingState.STABLE) {
            pendingRemoteOffers[peerUid] = remoteDesc
            return
        }
        android.util.Log.d(VOICE_TAG, "[$peerUid] Offer uygulanıyor (state=${pc.signalingState()})")
        pc.setRemoteDescription(
            SimpleSdpObserver(tag = "$peerUid:offer", onSetOk = {
                pendingRemoteOffers.remove(peerUid)
                drainCandidates(peerUid)
                pc.createAnswer(SimpleSdpObserver(tag = peerUid, onSuccess = { answer ->
                    if (answer == null) return@SimpleSdpObserver
                    val rawSdp = answer.description?.trim().orEmpty()
                    if (rawSdp.isEmpty()) {
                        android.util.Log.e(VOICE_TAG, "[$peerUid] createAnswer boş SDP döndü")
                        return@SimpleSdpObserver
                    }
                    // Yerel: WebRTC'nin ürettiği orijinal SDP — munging setLocalDescription'da yapılmaz
                    pc.setLocalDescription(SimpleSdpObserver(tag = "$peerUid:local-answer", onSetOk = {
                        scope.launch {
                            try {
                                val payload = signalingPayload(answer.type, rawSdp) ?: return@launch
                                connDoc(cid).update(mapOf("answer" to payload)).await()
                                android.util.Log.d(VOICE_TAG, "[$peerUid] Answer Firestore'a yazıldı")
                            } catch (e: Exception) {
                                android.util.Log.w(VOICE_TAG, "[$peerUid] Answer yazılamadı: ${e.message}")
                            }
                        }
                    }), answer)
                }), MediaConstraints())
            }),
            remoteDesc
        )
    }

    // ── Basit SdpObserver (sadece ilgili callback override edilir) ────────
    private inner class SimpleSdpObserver(
        private val tag: String = "",
        private val onSuccess: ((SessionDescription?) -> Unit)? = null,
        private val onSetOk: (() -> Unit)? = null
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) { onSuccess?.invoke(sdp) }
        override fun onSetSuccess() { onSetOk?.invoke() }
        override fun onCreateFailure(e: String?) {
            android.util.Log.e(VOICE_TAG, "SDP create hatası${tagSuffix()}: $e")
        }
        override fun onSetFailure(e: String?) {
            android.util.Log.e(VOICE_TAG, "SDP set hatası${tagSuffix()}: $e")
        }
        private fun tagSuffix() = if (tag.isNotEmpty()) " [$tag]" else ""
    }

    // ── PeerConnectionFactory ─────────────────────────────────────────────
    private fun ensureFactoryCore() {
        if (factory != null) return
        RoomAudioRouter.ensurePeerConnectionFactoryInitialized(context)
        adm = RoomAudioRouter.acquireAudioDeviceModule(context)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm!!)
            .createPeerConnectionFactory()
    }

    /** Ses yakalamadan önce iletişim moduna geç (Android'de mic için kritik) */
    private fun ensureLocalAudioTrack(hasMicPermission: Boolean): Boolean {
        if (!hasMicPermission) {
            android.util.Log.d(VOICE_TAG, "Mikrofon izni yok → dinleme modu")
            return false
        }
        ensureFactoryCore()
        val f = factory ?: return false

        if (localAudioTrack != null) {
            updateMicTransmission()
            localAudioTrack?.setVolume(micGainLevel.toDouble())
            RoomAudioRouter.registerMicRmsListener(micRmsListener)
            return true
        }

        RoomAudioRouter.setVoiceActive(context, true)
        return try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            audioSource = f.createAudioSource(constraints)
            localAudioTrack = f.createAudioTrack("voice_audio", audioSource)
            localTrackId = localAudioTrack?.id()
            updateMicTransmission()
            localAudioTrack?.setVolume(micGainLevel.toDouble())
            RoomAudioRouter.registerMicRmsListener(micRmsListener)
            android.util.Log.d(VOICE_TAG, "Mikrofon track oluşturuldu: $localTrackId")
            true
        } catch (e: Exception) {
            android.util.Log.w(VOICE_TAG, "Mikrofon başlatılamadı: ${e.message}")
            audioSource?.dispose(); audioSource = null
            localAudioTrack = null
            localTrackId = null
            false
        }
    }

    private suspend fun clearVoiceSignaling(peerUid: String) {
        val cid = mkConnId(myUid, peerUid)
        try {
            offerCandCol(cid).get().await().documents.forEach { it.reference.delete() }
            answerCandCol(cid).get().await().documents.forEach { it.reference.delete() }
            connDoc(cid).delete().await()
        } catch (_: Exception) {}
    }

    private fun handlePeerRenog(peerUid: String, renogAt: Long) {
        val prev = lastRenogAt[peerUid] ?: 0L
        if (renogAt <= prev) return
        lastRenogAt[peerUid] = renogAt
        scope.launch {
            android.util.Log.d(VOICE_TAG, "[$peerUid] Mikrofon açıldı → yeniden bağlanıyor")
            teardownPeer(peerUid)
            setupStarted.remove(peerUid)
            clearVoiceSignaling(peerUid)
            if (setupStarted.add(peerUid)) {
                if (amOfferer(peerUid)) setupAsOfferer(peerUid) else setupAsAnswerer(peerUid)
            }
        }
    }

    private suspend fun renegotiateAllPeersForMic() = reconnectAllPeers()

    private suspend fun reconnectAllPeers() {
        val peerUids = pcs.keys.toList()
        if (peerUids.isEmpty()) return
        android.util.Log.d(VOICE_TAG, "${peerUids.size} peer yeniden bağlanıyor")
        peerUids.forEach { teardownPeer(it) }
        setupStarted.clear()
        peerUids.forEach { peerUid ->
            clearVoiceSignaling(peerUid)
            if (setupStarted.add(peerUid)) {
                if (amOfferer(peerUid)) setupAsOfferer(peerUid) else setupAsAnswerer(peerUid)
            }
        }
    }

    private fun startVoiceForeground() {
        VoiceChatService.roomLabel = myDisplayName.ifBlank { "Oda $roomId" }
        val intent = Intent(context, VoiceChatService::class.java).apply {
            putExtra(VoiceChatService.EXTRA_ROOM_LABEL, VoiceChatService.roomLabel)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.w(VOICE_TAG, "Ön plan servisi başlatılamadı: ${e.message}")
        }
    }

    private fun stopVoiceForeground() {
        try {
            context.stopService(Intent(context, VoiceChatService::class.java))
        } catch (_: Exception) {}
    }

    private fun registerNetworkWatcher() {
        unregisterNetworkWatcher()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkOnline = isNetworkValidated(cm)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (online && !networkOnline && _inVoice.value) {
                    scope.launch {
                        delay(1200)
                        if (!_inVoice.value) return@launch
                        android.util.Log.d(VOICE_TAG, "Ağ geri geldi → ses bağlantıları yenileniyor")
                        FirebaseBootstrap.onNetworkAvailable()
                        reconnectAllPeers()
                    }
                }
                if (online) FirebaseBootstrap.onNetworkAvailable()
                networkOnline = online
            }

            override fun onLost(network: Network) {
                networkOnline = isNetworkValidated(cm)
            }
        }
        networkCallback = cb
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            android.util.Log.w(VOICE_TAG, "Ağ izleyici kaydı başarısız: ${e.message}")
        }
    }

    private fun unregisterNetworkWatcher() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun isNetworkValidated(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Desktop recvonly transceiver — mikrofon yokken uzak sesi alabilmek için */
    private fun addAudioToPc(pc: PeerConnection) {
        val track = localAudioTrack
        if (track != null) {
            pc.addTrack(track, listOf("stream0"))
        } else {
            val init = RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
            )
            pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init)
        }
    }

    // ── ICE sunucu listesi ────────────────────────────────────────────────
    private suspend fun iceServers(): List<PeerConnection.IceServer> {
        cachedIceConfig?.let { return it }
        return withContext(Dispatchers.IO) {
            TurnConfig.fetchCloudflareCredentials() ?: TurnConfig.getIceServers()
        }.also { cachedIceConfig = it }
    }

    // ── PeerConnection oluştur (onIceCandidate callback parametre olarak alır) ──
    private suspend fun createPc(peerUid: String, onIce: (IceCandidate) -> Unit): PeerConnection? {
        val cfg = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics             = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy             = PeerConnection.BundlePolicy.MAXBUNDLE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize     = 0
        }
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate?)   { c?.let(onIce) }
            override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {
                (r?.track() as? AudioTrack)?.let {
                    android.util.Log.d(VOICE_TAG, "[$peerUid] Audio track alındı")
                    registerRemoteTrack(peerUid, it)
                }
            }
            override fun onAddStream(s: MediaStream?) {
                s?.audioTracks?.forEach { registerRemoteTrack(peerUid, it) }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {
                android.util.Log.d(VOICE_TAG, "[$peerUid] ConnectionState: $state")
                // Bağlantı durumunu güncelle → UI'da göster
                val stateStr = when (state) {
                    PeerConnection.PeerConnectionState.CONNECTING -> "connecting"
                    PeerConnection.PeerConnectionState.CONNECTED -> "connected"
                    PeerConnection.PeerConnectionState.DISCONNECTED, PeerConnection.PeerConnectionState.FAILED -> "reconnecting"
                    else -> "connecting"
                }
                peerStates[peerUid] = stateStr
                refreshPeerStates()
                when (state) {
                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.CLOSED -> {
                        scope.launch {
                            if (!_inVoice.value) return@launch
                            android.util.Log.d(VOICE_TAG, "[$peerUid] Bağlantı kesildi → yeniden kurulum…")
                            teardownPeer(peerUid)
                            delay(2000)
                            if (_inVoice.value) {
                                // setupStarted temizlendi — tekrar setup başlatılabilir
                                if (setupStarted.add(peerUid)) {
                                    if (amOfferer(peerUid)) setupAsOfferer(peerUid)
                                    else setupAsAnswerer(peerUid)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState?)              {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                android.util.Log.d(VOICE_TAG, "[$peerUid] IceState: $s")
                when (s) {
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        scope.launch {
                            delay(8000)
                            // 8 sn hâlâ disconnect ise tam yeniden kurulum
                            val pc = pcs[peerUid]
                            if (pc != null && _inVoice.value) {
                                val iceState = pc.iceConnectionState()
                                if (iceState == PeerConnection.IceConnectionState.DISCONNECTED ||
                                    iceState == PeerConnection.IceConnectionState.FAILED) {
                                    android.util.Log.d(VOICE_TAG, "[$peerUid] ICE hâlâ kopuk → tam yeniden kurulum")
                                    teardownPeer(peerUid)
                                    if (setupStarted.add(peerUid)) {
                                        if (amOfferer(peerUid)) setupAsOfferer(peerUid)
                                        else setupAsAnswerer(peerUid)
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(b: Boolean)                      {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?)      {}
            override fun onIceCandidatesRemoved(cs: Array<out IceCandidate>?)            {}
            override fun onDataChannel(dc: DataChannel?)                                  {}
            override fun onRenegotiationNeeded()                                          {}
            override fun onRemoveStream(s: MediaStream?)                                  {}
        }
        val pc = factory?.createPeerConnection(cfg, observer) ?: return null
        addAudioToPc(pc)
        pcs[peerUid] = pc
        return pc
    }

    // ── Offerer tarafı ─────────────────────────────────────────────────────
    private fun setupAsOfferer(peerUid: String) {
        teardownPeerConnection(peerUid)
        peerStates[peerUid] = "connecting"; refreshPeerStates()
        scope.launch {
            val cid = mkConnId(myUid, peerUid)
            clearVoiceSignaling(peerUid)

            val pc = createPc(peerUid) { candidate ->
                scope.launch {
                    offerCandCol(cid).add(
                        mapOf("sdpMid" to (candidate.sdpMid ?: ""),
                              "sdpMLineIndex" to candidate.sdpMLineIndex,
                              "sdp" to candidate.sdp)
                    ).await()
                }
            } ?: return@launch

            val connL = connDoc(cid).addSnapshotListener { snap, _ ->
                val hasAnswer = snap?.get("answer") != null
                if (hasAnswer) {
                    android.util.Log.d(VOICE_TAG, "[$peerUid] Firestore answer alındı")
                }
                val answerDesc = sessionDescriptionFromMap(snap?.get("answer") as? Map<*, *>) ?: return@addSnapshotListener
                tryApplyAnswer(peerUid, answerDesc)
            }
            val candL = answerCandCol(cid).addSnapshotListener { snap, _ ->
                snap?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.forEach { ch ->
                        iceCandidateFromData(ch.document.data)?.let { bufferCandidate(peerUid, it) }
                    }
            }
            peerListeners.getOrPut(peerUid) { mutableListOf() }.addAll(listOf(connL, candL))
            fsListeners += connL
            fsListeners += candL

            pc.createOffer(SimpleSdpObserver(tag = peerUid, onSuccess = { sdp ->
                if (sdp == null) return@SimpleSdpObserver
                val rawSdp = sdp.description?.trim().orEmpty()
                if (rawSdp.isEmpty()) {
                    android.util.Log.e(VOICE_TAG, "[$peerUid] createOffer boş SDP döndü")
                    return@SimpleSdpObserver
                }
                android.util.Log.d(VOICE_TAG, "[$peerUid] Local offer ayarlanıyor (${rawSdp.length} byte)")
                // Yerel: createOffer'ın döndürdüğü orijinal nesne — tuned kopya setLocalDescription'da NULL hatası verir
                pc.setLocalDescription(SimpleSdpObserver(tag = "$peerUid:local-offer", onSetOk = {
                    scope.launch {
                        try {
                            val payload = signalingPayload(sdp.type, rawSdp) ?: return@launch
                            connDoc(cid).set(mapOf("offer" to payload)).await()
                            android.util.Log.d(VOICE_TAG, "[$peerUid] Offer Firestore'a yazıldı")
                            val snap = connDoc(cid).get().await()
                            sessionDescriptionFromMap(snap.get("answer") as? Map<*, *>)?.let {
                                tryApplyAnswer(peerUid, it)
                            }
                            pendingRemoteAnswers[peerUid]?.let { tryApplyAnswer(peerUid, it) }
                            startAnswerPoll(peerUid, cid)
                        } catch (e: Exception) {
                            android.util.Log.w(VOICE_TAG, "[$peerUid] Offer yazılamadı: ${e.message}")
                        }
                    }
                }), sdp)
            }), MediaConstraints())
        }
    }

    // ── Answerer tarafı ────────────────────────────────────────────────────
    private fun setupAsAnswerer(peerUid: String) {
        teardownPeerConnection(peerUid)
        peerStates[peerUid] = "connecting"; refreshPeerStates()
        val cid = mkConnId(myUid, peerUid)

        // Offerer'ın ICE adaylarını ÖNCE dinle — PC henüz null olsa da tamponla
        val candL = offerCandCol(cid).addSnapshotListener { snap, _ ->
            snap?.documentChanges
                ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                ?.forEach { ch ->
                    iceCandidateFromData(ch.document.data)?.let { bufferCandidate(peerUid, it) }
                }
        }
        peerListeners.getOrPut(peerUid) { mutableListOf() }.add(candL)
        fsListeners += candL

        val connL = connDoc(cid).addSnapshotListener { snap, _ ->
            val offer = snap?.get("offer") as? Map<*, *> ?: return@addSnapshotListener
            if (snap.get("answer") != null) return@addSnapshotListener
            val remoteDesc = sessionDescriptionFromMap(offer) ?: return@addSnapshotListener

            scope.launch {
                var pc = pcs[peerUid]
                if (pc == null) {
                    pc = createPc(peerUid) { candidate ->
                        scope.launch {
                            answerCandCol(cid).add(
                                mapOf("sdpMid" to (candidate.sdpMid ?: ""),
                                      "sdpMLineIndex" to candidate.sdpMLineIndex,
                                      "sdp" to candidate.sdp)
                            ).await()
                        }
                    } ?: return@launch
                }
                processOffer(peerUid, remoteDesc, cid)
                pendingRemoteOffers[peerUid]?.let { pending ->
                    processOffer(peerUid, pending, cid)
                }
            }
        }

        peerListeners.getOrPut(peerUid) { mutableListOf() }.add(connL)
        fsListeners += connL
    }

    // ── Sesli sohbete katıl ────────────────────────────────────────────────
    fun join(hasMicPermission: Boolean = false) {
        if (_inVoice.value || _isJoining.value) return
        scope.launch {
            _isJoining.value = true
            _joinError.value = null
            try {
                withContext(Dispatchers.IO) {
                    TurnConfig.init()
                    cachedIceConfig = TurnConfig.fetchCloudflareCredentials() ?: TurnConfig.getIceServers()
                }
                android.util.Log.d(VOICE_TAG, "ICE sunucuları hazır (${cachedIceConfig?.size ?: 0} adet)")
                ensureFactoryCore()
                ensureLocalAudioTrack(hasMicPermission)
                // Ses yönlendirmesi — WebRTC hazır olduktan sonra; eski track'ler temizlenmiş olur
                RoomAudioRouter.setVoiceActive(context, true)

                val listenOnly = localAudioTrack == null
                val joinPayload = mutableMapOf<String, Any>(
                    "displayName" to myDisplayName,
                    "muted" to listenOnly,
                    "listenOnly" to listenOnly,
                    "joinedAt" to System.currentTimeMillis(),
                )
                if (myPhotoBase64.isNotBlank()) joinPayload["photoBase64"] = myPhotoBase64
                peerDoc(myUid).set(joinPayload).await()

                // Snapshot listener ilk tetiklendiğinde mevcut tüm belgeleri ADDED olarak verir —
                // ayrıca get().await() + forEach yapmaya GEREK YOK, duplicate setup olur.
                val peersL = peersCol().addSnapshotListener { snap, _ ->
                    // Bağlantı yönetimi
                    snap?.documentChanges?.forEach { ch ->
                        val peerUid = ch.document.id
                        if (peerUid == myUid) return@forEach
                        when (ch.type) {
                            com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                // setupStarted senkron güncellenir — coroutine race condition önlenir
                                if (!setupStarted.add(peerUid)) return@forEach
                                if (amOfferer(peerUid)) setupAsOfferer(peerUid) else setupAsAnswerer(peerUid)
                            }
                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                setupStarted.remove(peerUid)
                                pcs[peerUid]?.close()
                                pcs.remove(peerUid)
                                pendingCandidates.remove(peerUid)
                            }
                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                val renog = ch.document.getLong("renogAt") ?: 0L
                                if (renog > 0L) handlePeerRenog(peerUid, renog)
                            }
                            else -> {}
                        }
                    }
                    // Ses kanalındaki kullanıcı listesini güncelle (kendin dahil)
                    val peers = snap?.documents?.mapNotNull { doc ->
                        val name = doc.getString("displayName") ?: return@mapNotNull null
                        val photo = doc.getString("photoBase64").orEmpty()
                        val muted = doc.getBoolean("muted") ?: false
                        val listenOnly = doc.getBoolean("listenOnly") ?: false
                        val connState = if (doc.id == myUid) "self" else peerStates[doc.id] ?: "connecting"
                        VoicePeer(
                            uid = doc.id,
                            displayName = name,
                            photoBase64 = photo,
                            muted = muted,
                            listenOnly = listenOnly,
                            connectionState = connState,
                        )
                    } ?: emptyList()
                    _voicePeersList.value = peers

                    // Giriş/çıkış event'leri — kendi değişimlerini hariç tut
                    snap?.documentChanges?.forEach { change ->
                        if (change.document.id == myUid) return@forEach
                        val name = change.document.getString("displayName") ?: "Kullanıcı"
                        when (change.type) {
                            com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                if (!prevPeers.containsKey(change.document.id)) {
                                    _voiceEvents.value = _voiceEvents.value + VoiceEvent("joined", name)
                                }
                            }
                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                val n = prevPeers[change.document.id] ?: name
                                _voiceEvents.value = _voiceEvents.value + VoiceEvent("left", n)
                            }
                            else -> {}
                        }
                    }
                    prevPeers.clear()
                    peers.forEach { prevPeers[it.uid] = it.displayName }
                }
                fsListeners += peersL

                if (localAudioTrack != null) {
                    RoomAudioRouter.registerMicRmsListener(micRmsListener)
                }

                _inVoice.value = true
                startVoiceForeground()
                registerNetworkWatcher()
                startSpeakingDetection()
                startVolumeMaintenance()
                android.util.Log.d(
                    VOICE_TAG,
                    "Sesli sohbete katıldı: $roomId (mic=${localAudioTrack != null}, listenOnly=$listenOnly)"
                )
            } catch (e: Exception) {
                android.util.Log.e(VOICE_TAG, "join() hatası", e)
                releaseInternal()
                _joinError.value = e.message ?: "Bağlantı kurulamadı"
            } finally {
                _isJoining.value = false
            }
        }
    }

    private val _joinError = MutableStateFlow<String?>(null)
    val joinError: StateFlow<String?> = _joinError

    fun clearJoinError() { _joinError.value = null }

    /**
     * WebRTC stats'tan ses seviyeleri okuyarak konuşanları tespit eder.
     * Her 300ms'de tüm PeerConnection'ların inbound-rtp/outbound-rtp audioLevel değerini kontrol eder.
     * suspendCancellableCoroutine ile callback tabanlı getStats() async/await haline getirildi.
     */
    private fun startSpeakingDetection() {
        speakJob?.cancel()
        speakJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(300)
                tick++
                val speaking = mutableSetOf<String>()
                val releaseThreshold = speakLevelThreshold * 0.65

                val micTransmitting = localAudioTrack != null && !_muted.value && !_deafened.value &&
                    micGainLevel > 0.01f && (!_pushToTalk.value || _pttActive.value)
                if (micTransmitting) {
                    val level = (localMicRms / LOCAL_RMS_NORM_DIVISOR).coerceIn(0.0, 1.0)
                    _localMicLevel.value = level.toFloat()
                    if (updateSpeakState(myUid, level, speakLevelThreshold, releaseThreshold)) {
                        speaking.add(myUid)
                    }
                } else {
                    _localMicLevel.value = 0f
                    speakActiveFrames.remove(myUid)
                    smoothedAudioLevels.remove(myUid)
                }

                // getStats WebRTC iş parçacığını yorar — uzak konuşma göstergesi seyrek örnekle
                if (tick % 2 == 0) {
                    pcs.forEach { (peerUid, pc) ->
                        if (peerUid == myUid) return@forEach
                        val raw = pc.getAudioLevelInbound()
                        if (updateSpeakState(peerUid, raw, speakLevelThreshold, releaseThreshold)) {
                            speaking.add(peerUid)
                        }
                    }
                } else {
                    _speakingUids.value.forEach { uid ->
                        if (uid != myUid && speakActiveFrames[uid]?.let { it >= 2 } == true) {
                            speaking.add(uid)
                        }
                    }
                }

                if (speaking != _speakingUids.value) {
                    _speakingUids.value = speaking
                }
            }
        }
    }

    /** EMA + histerezis: konuşma göstergesinin titremesini azaltır */
    private fun updateSpeakState(
        uid: String,
        rawLevel: Double,
        onThreshold: Double,
        offThreshold: Double
    ): Boolean {
        val prev = smoothedAudioLevels[uid] ?: rawLevel
        val smoothed = prev * 0.65 + rawLevel * 0.35
        smoothedAudioLevels[uid] = smoothed

        val frames = speakActiveFrames[uid] ?: 0
        val wasSpeaking = frames >= 2
        val minFrames = if (uid == myUid) 1 else 2
        val next = when {
            !wasSpeaking && smoothed > onThreshold -> frames + 1
            wasSpeaking && smoothed < offThreshold -> maxOf(frames - 1, 0)
            wasSpeaking -> minFrames
            else -> 0
        }
        speakActiveFrames[uid] = next
        return next >= minFrames
    }

    /** MIUI bazen WebRTC ses seviyesini sıfırlar — kullanıcı ayarını koru */
    private fun startVolumeMaintenance() {
        volumeMaintainJob?.cancel()
        if (!RoomAudioRouter.isXiaomiFamily()) return
        volumeMaintainJob = scope.launch {
            while (isActive) {
                delay(1500)
                reapplyAllPeerVolumes()
                RoomAudioRouter.reapplyAllStoredTrackLevels()
            }
        }
    }

    /** Android'de outbound-rtp audioLevel sık sık 0 — media-source/track yedekleri */
    private fun RTCStatsReport.extractAudioLevel(outbound: Boolean, trackId: String? = null): Double {
        val rtpType = if (outbound) "outbound-rtp" else "inbound-rtp"
        var level = statsMap.values
            .filter { it.type == rtpType && it.members["kind"] == "audio" }
            .mapNotNull { (it.members["audioLevel"] as? Number)?.toDouble() }
            .maxOrNull() ?: 0.0
        if (level > 0) return level

        level = statsMap.values
            .filter { it.type == "media-source" && it.members["kind"] == "audio" }
            .mapNotNull { (it.members["audioLevel"] as? Number)?.toDouble() }
            .maxOrNull() ?: 0.0
        if (level > 0) return level

        return statsMap.values
            .filter { it.type == "track" && it.members["kind"] == "audio" }
            .filter { stat ->
                val tid = stat.members["trackIdentifier"] as? String
                trackId == null || tid == null || tid == trackId
            }
            .mapNotNull { (it.members["audioLevel"] as? Number)?.toDouble() }
            .maxOrNull() ?: 0.0
    }

    private suspend fun PeerConnection.getAudioLevelOutbound(): Double =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            getStats { report ->
                val level = report.extractAudioLevel(outbound = true, trackId = localTrackId)
                if (cont.isActive) cont.resumeWith(Result.success(level))
            }
        }

    private suspend fun PeerConnection.getAudioLevelInbound(): Double =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            getStats { report ->
                val level = report.extractAudioLevel(outbound = false)
                if (cont.isActive) cont.resumeWith(Result.success(level))
            }
        }

    // ── Sesli sohbetten çık ────────────────────────────────────────────────
    fun leave() {
        scope.launch {
            // Temizlemeden önce peer listesini kaydet
            val allPeers = pcs.keys.toList()
            releaseInternal()
            try { peerDoc(myUid).delete().await() } catch (_: Exception) {}
            // Tüm bağlantıların ICE alt koleksiyonlarını temizle
            // (Firestore parent silinince subcollection silinmez → stale ICE problem)
            allPeers.forEach { peerUid ->
                val cid = mkConnId(myUid, peerUid)
                try {
                    offerCandCol(cid).get().await().documents.forEach { it.reference.delete() }
                    answerCandCol(cid).get().await().documents.forEach { it.reference.delete() }
                } catch (_: Exception) {}
                if (myUid < peerUid) { // offerer'dim
                    try { connDoc(cid).delete().await() } catch (_: Exception) {}
                }
            }
        }
    }

    // ── Mikrofonu sustur / aç ──────────────────────────────────────────────
    fun toggleMute() {
        if (_deafened.value) toggleDeafen()
        val newMuted = !_muted.value
        _muted.value = newMuted
        updateMicTransmission()
        scope.launch {
            try { peerDoc(myUid).update("muted", newMuted).await() } catch (_: Exception) {}
        }
    }

    /** Discord sağırlaştır — kimseyi duymazsın, mikrofon da kapanır */
    fun toggleDeafen() {
        val newDeaf = !_deafened.value
        _deafened.value = newDeaf
        if (newDeaf) {
            if (!_muted.value) {
                _muted.value = true
                scope.launch {
                    try { peerDoc(myUid).update("muted", true).await() } catch (_: Exception) {}
                }
            }
        } else if (_muted.value) {
            _muted.value = false
            scope.launch {
                try { peerDoc(myUid).update("muted", false).await() } catch (_: Exception) {}
            }
        }
        updateMicTransmission()
        reapplyAllPeerVolumes()
    }

    fun setPushToTalk(enabled: Boolean) {
        _pushToTalk.value = enabled
        VoicePrefs.savePushToTalk(context, enabled)
        if (!enabled) _pttActive.value = false
        updateMicTransmission()
    }

    fun setPttActive(active: Boolean) {
        if (!_pushToTalk.value) return
        _pttActive.value = active
        updateMicTransmission()
    }

    // ── İç temizlik ───────────────────────────────────────────────────────
    private fun releaseInternal() {
        stopVoiceForeground()
        unregisterNetworkWatcher()
        RoomAudioRouter.unregisterMicRmsListener(micRmsListener)
        localMicRms = 0.0
        speakJob?.cancel(); speakJob = null
        volumeMaintainJob?.cancel(); volumeMaintainJob = null
        smoothedAudioLevels.clear()
        speakActiveFrames.clear()
        fsListeners.forEach { it.remove() }
        fsListeners.clear()
        peerListeners.values.forEach { list -> list.forEach { it.remove() } }
        peerListeners.clear()
        pcs.values.forEach { it.close() }
        pcs.clear()
        pendingCandidates.clear()
        pendingRemoteAnswers.clear()
        pendingRemoteOffers.clear()
        setupStarted.clear()
        localAudioTrack?.dispose();  localAudioTrack = null
        audioSource?.dispose();      audioSource = null
        localTrackId = null
        if (adm != null) {
            RoomAudioRouter.releaseAudioDeviceModule()
            adm = null
        }
        factory?.dispose();          factory = null
        cachedIceConfig = null
        RoomAudioRouter.setVoiceActive(context, false)
        _inVoice.value   = false
        _isJoining.value = false
        _muted.value     = false
        _deafened.value  = false
        _pushToTalk.value = VoicePrefs.loadPushToTalk(context)
        _pttActive.value = false
        _speakingUids.value = emptySet()
        _localMicLevel.value = 0f
        _voicePeersList.value = emptyList()
        _voiceEvents.value = emptyList()
        prevPeers.clear()
        lastRenogAt.clear()
    }

    fun destroy() {
        // destroy() scope'u iptal eder — leave()'in async Firestore temizliği tamamlanamaz.
        // Bu yüzden ses kanalındaysak önce leave() çağrılmalı (WatchViewModel.leaveRoom/deleteRoom).
        // Yine de güvenlik için senkron kaynakları serbest bırak, presence'ı ayrı scope'ta sil.
        val wasInVoice = _inVoice.value
        val allPeers = pcs.keys.toList()
        releaseInternal()
        if (wasInVoice) {
            CoroutineScope(Dispatchers.IO).launch {
                try { peerDoc(myUid).delete().await() } catch (_: Exception) {}
                allPeers.forEach { peerUid ->
                    val cid = mkConnId(myUid, peerUid)
                    try {
                        offerCandCol(cid).get().await().documents.forEach { it.reference.delete() }
                        answerCandCol(cid).get().await().documents.forEach { it.reference.delete() }
                    } catch (_: Exception) {}
                    if (myUid < peerUid) {
                        try { connDoc(cid).delete().await() } catch (_: Exception) {}
                    }
                }
            }
        }
        scope.cancel()
    }
}
