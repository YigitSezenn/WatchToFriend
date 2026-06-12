package com.watch.watchtofriend.ui.watch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.TurnConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.ByteArrayOutputStream

/**
 * Ekran paylaşımı — İKİ MOD:
 *
 * MOD 1 (RTDB Frame Relay — varsayılan / fallback):
 *   Paylaşan: MediaProjection ile ekranı yakalar → JPEG sıkıştırır → Firebase RTDB'ye yazar
 *   İzleyenler: RTDB'yi dinler → frame gelince Bitmap olarak gösterir
 *
 * MOD 2 (WebRTC — Cloudflare TURN ile):
 *   Paylaşan: MediaProjection → VideoSource → VideoTrack → PeerConnection
 *   İzleyenler: Firestore sinyal kanalı üzerinden offer/answer/ICE → VideoTrack alır
 *   Sinyal kanalı: rooms/{roomId}/webrtc/{uid}/ altında
 *
 * Fallback: WebRTC başarısız olursa RTDB moduna geçilir.
 *
 * RTDB yapısı:
 *   screenShare/{roomId}/frame  → base64 JPEG
 *   screenShare/{roomId}/ts     → Long timestamp
 *   screenShare/{roomId}/online → Boolean (onDisconnect false)
 *
 * Firestore WebRTC sinyal yapısı (Windows ile uyumlu):
 *   rooms/{roomId}/webrtc/offer                    → { sdp, type, fromUid } (paylaşan tarafından yazılır)
 *   rooms/{roomId}/webrtc/answer                   → { sdp, type, fromUid } (izleyen tarafından yazılır)
 *   rooms/{roomId}/webrtc/{uid}/candidates/{id}    → ICE candidate JSON
 *   rooms/{roomId}/webrtc/meta                     → { mode, sharerUid } (mod bilgisi)
 */
class ScreenShareManager(
    private val context: Context,
    private val roomId: String,
    val uid: String
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val db    = Firebase.firestore
    private val rtdb  by lazy {
        Firebase.database("https://watchtofriend-default-rtdb.firebaseio.com/").reference
    }
    private val roomRef      get() = db.collection("rooms").document(roomId)
    private val frameRef     get() = rtdb.child("screenShare").child(roomId)
    private val webrtcRef    get() = roomRef.collection("webrtc")

    // ── Mod bayrağı ─────────────────────────────────────────────────────────
    // true → WebRTC (Cloudflare TURN), false → RTDB frame relay
    private var useWebRtc = false

    // ── RTDB Sharer ──────────────────────────────────────────────────────────
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureJob: Job? = null
    private var lastUploadedHash = -1

    // ── RTDB Viewer ──────────────────────────────────────────────────────────
    private var frameListener: ValueEventListener? = null
    private var onlineListener: ValueEventListener? = null

    // ── WebRTC ───────────────────────────────────────────────────────────────
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrackInstance: VideoTrack? = null
    private var screenCapturerAndroid: ScreenCapturerAndroid? = null
    private var eglBase: EglBase? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private val webrtcSignalListeners = mutableListOf<ListenerRegistration>()
    private var iceCandidateIndex = 0
    private val addedIceCandidateIds = mutableSetOf<String>()
    private var lastShareResultCode: Int? = null
    private var lastShareProjectionData: Intent? = null

    companion object {
        private const val FRAME_INTERVAL_MS = 150L
        private const val JPEG_QUALITY      = 35
        private const val CAPTURE_WIDTH     = 540
        private const val TAG               = "ScreenShareManager"
    }

    private val listeners = mutableListOf<ListenerRegistration>()

    // ── Public state ──────────────────────────────────────────────────────────
    private val _localBitmap  = MutableStateFlow<Bitmap?>(null)
    val localBitmap: StateFlow<Bitmap?> = _localBitmap

    private val _remoteBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteBitmap: StateFlow<Bitmap?> = _remoteBitmap

    private val _isSharing    = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing

    private val _sharerUid    = MutableStateFlow<String?>(null)
    val sharerUid: StateFlow<String?> = _sharerUid

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    // WebRTC video track'leri — UI SurfaceViewRenderer ile gösterir
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack

    private val _event = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 4)
    val event: SharedFlow<ShareEvent> = _event

    sealed class ShareEvent {
        object ShareBlocked  : ShareEvent()
        object ShareStarted  : ShareEvent()
        object ShareStopped  : ShareEvent()
        object ZombieCleared : ShareEvent()
        data class Error(val msg: String) : ShareEvent()
        data class IceStatus(val status: String) : ShareEvent()
    }

    init { if (roomId.isNotBlank()) observeRoomScreenShare() }

    // ─── Oda dinleyicisi ─────────────────────────────────────────────────────
    private fun observeRoomScreenShare() {
        if (roomId.isBlank()) return
        val reg = roomRef.addSnapshotListener { snap, _ ->
            val sharer = snap?.getString("screenShareUid").orEmpty().ifBlank { null }
            _sharerUid.value = sharer

            // Zombie tespiti
            if (sharer != null && sharer != uid) {
                val presence = snap?.get("presence") as? Map<*, *>
                val lastSeen = (presence?.get(sharer) as? Long) ?: 0L
                if (System.currentTimeMillis() - lastSeen > 60_000L
                    && snap?.getString("hostUid") == uid) {
                    scope.launch {
                        runCatching { clearFirestore() }
                        _event.tryEmit(ShareEvent.ZombieCleared)
                    }
                    return@addSnapshotListener
                }
            }

            when {
                sharer != null && sharer != uid && !_isSharing.value ->
                    startListeningFrames(sharer)
                sharer == null ->
                    stopListeningFrames()
            }
        }
        listeners.add(reg)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHARER — Paylaşım Başlatma
    // ─────────────────────────────────────────────────────────────────────────

    fun startSharing(resultCode: Int, projectionData: Intent) {
        lastShareResultCode = resultCode
        lastShareProjectionData = projectionData
        scope.launch {
            try {
                // Atomik claim
                val claimed = db.runTransaction { tx ->
                    val current = tx.get(roomRef).getString("screenShareUid").orEmpty()
                    if (current.isNotBlank() && current != uid) return@runTransaction false
                    tx.update(roomRef, "screenShareUid", uid)
                    true
                }.await()

                if (!claimed) { _event.tryEmit(ShareEvent.ShareBlocked); return@launch }

                // WebRTC kredansiyel almayı dene
                val cfServers = withContext(Dispatchers.IO) {
                    runCatching { TurnConfig.fetchCloudflareCredentials() }.getOrNull()
                }

                if (cfServers != null) {
                    android.util.Log.d(TAG, "WebRTC modu başlatılıyor (Cloudflare TURN)")
                    useWebRtc = true
                    startSharingWebRtc(resultCode, projectionData, cfServers)
                } else {
                    android.util.Log.d(TAG, "RTDB frame relay moduna geçildi (WebRTC fallback)")
                    useWebRtc = false
                    startSharingRtdb(resultCode, projectionData)
                }

                // Modu Firestore'a yaz (izleyici okusun)
                runCatching {
                    webrtcRef.document("meta").set(
                        mapOf("mode" to if (useWebRtc) "webrtc" else "rtdb", "sharerUid" to uid)
                    ).await()
                }
            } catch (e: Exception) {
                stopSharing()
                _event.tryEmit(ShareEvent.Error(context.getString(R.string.share_err_start, e.message ?: "")))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHARER — WebRTC modu
    // ─────────────────────────────────────────────────────────────────────────

    private fun startSharingWebRtc(
        resultCode: Int,
        projectionData: Intent,
        iceServers: List<org.webrtc.PeerConnection.IceServer>
    ) {
        try {
            // EGL context
            eglBase = EglBase.create()

            // PeerConnectionFactory başlat
            RoomAudioRouter.ensurePeerConnectionFactoryInitialized(context)

            val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

            audioDeviceModule = RoomAudioRouter.acquireAudioDeviceModule(context)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule!!)
                .createPeerConnectionFactory()

            RoomAudioRouter.setScreenShareActive(context, true)

            // Ekran capture
            val dm = context.resources.displayMetrics
            val captureW = dm.widthPixels
            val captureH = dm.heightPixels

            localVideoSource = peerConnectionFactory!!.createVideoSource(/* isScreencast = */ true)

            screenCapturerAndroid = ScreenCapturerAndroid(
                projectionData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        scope.launch { stopSharing() }
                    }
                }
            )
            screenCapturerAndroid!!.initialize(
                SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext),
                context,
                localVideoSource!!.capturerObserver
            )
            screenCapturerAndroid!!.startCapture(captureW, captureH, 15) // 15 fps

            localVideoTrackInstance = peerConnectionFactory!!.createVideoTrack("screen_video_$uid", localVideoSource)
            localVideoTrackInstance!!.setEnabled(true)
            _localVideoTrack.value = localVideoTrackInstance

            // PeerConnection (offer tarafı — paylaşan)
            val rtcConfig = org.webrtc.PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = org.webrtc.PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = peerConnectionFactory!!.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        // ICE candidate'ı Firestore'a yaz (Windows uyumlu: "candidates" koleksiyonu)
                        scope.launch {
                            runCatching {
                                webrtcRef.document(uid)
                                    .collection("candidates")
                                    .document("${iceCandidateIndex++}")
                                    .set(mapOf(
                                        "sdpMid" to candidate.sdpMid,
                                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                                        "sdp" to candidate.sdp
                                    )).await()
                            }
                        }
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                        _event.tryEmit(ShareEvent.IceStatus(state.name))
                        android.util.Log.d(TAG, "ICE connection: $state")
                        if (state == PeerConnection.IceConnectionState.FAILED) {
                            scope.launch { fallbackSharerToRtdb() }
                        }
                    }

                    override fun onAddStream(stream: MediaStream?) {}
                    override fun onRemoveStream(stream: MediaStream?) {}
                    override fun onDataChannel(dc: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
                }
            )

            // Video track'i PeerConnection'a ekle
            val streamIds = listOf("stream_$uid")
            peerConnection!!.addTrack(localVideoTrackInstance!!, streamIds)

            // Offer oluştur
            peerConnection!!.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    val improvedOffer = SessionDescription(sdp.type, improveVideoSdp(improveAudioSdp(sdp.description)))
                    peerConnection!!.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            scope.launch {
                                runCatching {
                                    webrtcRef.document("offer").set(
                                        mapOf("sdp" to improvedOffer.description, "type" to "offer", "fromUid" to uid)
                                    ).await()
                                }
                                listenForAnswers()
                            }
                        }
                        override fun onSetFailure(error: String?) { android.util.Log.e(TAG, "setLocalDesc fail: $error") }
                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onCreateFailure(error: String?) {}
                    }, improvedOffer)
                }
                override fun onCreateFailure(error: String?) {
                    android.util.Log.e(TAG, "createOffer fail: $error")
                    scope.launch { _event.tryEmit(ShareEvent.Error(context.getString(R.string.share_err_offer))) }
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }, MediaConstraints())

            _isSharing.value = true
            _event.tryEmit(ShareEvent.ShareStarted)

            val serviceIntent = Intent(context, ScreenShareService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "WebRTC init hatası: ${e.message}")
            stopWebRtcPeer()
            // RTDB'ye geçiş için geçerli resultCode/projectionData gereklidir;
            // exception durumunda bu değerlere artık erişilemez → kullanıcıya hata bildir,
            // paylaşımı durdur. (Kullanıcı tekrar deneyebilir.)
            scope.launch {
                useWebRtc = false
                _event.tryEmit(ShareEvent.Error(context.getString(R.string.share_err_webrtc_init, e.message ?: "")))
                stopSharing()
            }
        }
    }

    /**
     * Firestore'dan gelen answer'ı bekle (paylaşan taraf).
     * Windows uyumlu: "answer" dokümanı { sdp, type, fromUid } formatında.
     */
    private fun listenForAnswers() {
        val reg = webrtcRef.document("answer").addSnapshotListener { snap, _ ->
            val answerSdp = snap?.getString("sdp") ?: return@addSnapshotListener
            val answererUid = snap.getString("fromUid") ?: return@addSnapshotListener

            if (answererUid == uid) return@addSnapshotListener // kendi answer'ımızı yoksay

            val pc = peerConnection ?: return@addSnapshotListener
            if (pc.remoteDescription != null) return@addSnapshotListener // zaten set edildi

            val sdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    android.util.Log.d(TAG, "Answer SDP set (izleyici: $answererUid)")
                    // İzleyicinin ICE candidates'larını ekle
                    listenForViewerIceCandidates(answererUid)
                }
                override fun onSetFailure(error: String?) { android.util.Log.e(TAG, "setRemoteDesc (answer) fail: $error") }
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, sdp)
        }
        webrtcSignalListeners.add(reg)
    }

    private fun listenForViewerIceCandidates(viewerUid: String) {
        val reg = webrtcRef.document(viewerUid).collection("candidates")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val doc = change.document
                    if (!addedIceCandidateIds.add(doc.id)) return@forEach
                    val sdpMid = doc.getString("sdpMid") ?: return@forEach
                    val sdpMLineIndex = (doc.getLong("sdpMLineIndex") ?: 0).toInt()
                    val sdp = doc.getString("sdp") ?: return@forEach
                    peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                }
            }
        webrtcSignalListeners.add(reg)
    }

    /** Paylaşan tarafında WebRTC başarısız olunca RTDB moduna geç (Desktop ile aynı davranış). */
    private suspend fun fallbackSharerToRtdb() {
        val rc = lastShareResultCode
        val data = lastShareProjectionData
        if (rc == null || data == null) {
            _event.tryEmit(ShareEvent.Error(context.getString(R.string.share_err_webrtc_connect)))
            stopSharing()
            return
        }
        android.util.Log.w(TAG, "Sharer WebRTC FAILED → RTDB fallback")
        stopWebRtcPeer()
        useWebRtc = false
        runCatching {
            webrtcRef.document("meta").set(mapOf("mode" to "rtdb", "sharerUid" to uid)).await()
            webrtcRef.document("offer").delete().await()
            webrtcRef.document("answer").delete().await()
        }
        withContext(Dispatchers.Main) {
            startSharingRtdb(rc, data)
        }
        _event.tryEmit(ShareEvent.Error(context.getString(R.string.share_err_rtdb_mode)))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHARER — RTDB modu
    // ─────────────────────────────────────────────────────────────────────────

    private fun startSharingRtdb(resultCode: Int, projectionData: Intent) {
        try {
            val mpManager = context.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, projectionData)

            mediaProjection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    scope.launch { stopSharing() }
                }
            }, null)

            val dm = context.resources.displayMetrics
            val captureW = CAPTURE_WIDTH
            val captureH = (captureW.toFloat() * dm.heightPixels / dm.widthPixels).toInt()

            imageReader = ImageReader.newInstance(
                captureW, captureH, PixelFormat.RGBA_8888, 2
            )
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "WTFShare", captureW, captureH, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )

            frameRef.child("online").setValue(true)
            frameRef.child("online").onDisconnect().setValue(false)

            _isSharing.value = true
            _event.tryEmit(ShareEvent.ShareStarted)

            val serviceIntent = Intent(context, ScreenShareService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            captureJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    captureAndUpload()
                    delay(FRAME_INTERVAL_MS)
                }
            }
        } catch (e: Exception) {
            stopSharing()
            _event.tryEmit(ShareEvent.Error(context.getString(R.string.share_err_rtdb_start, e.message ?: "")))
        }
    }

    private suspend fun captureAndUpload() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane  = image.planes[0]
            val rowPad = plane.rowStride - plane.pixelStride * image.width
            val bmpWidth = image.width + maxOf(0, rowPad / plane.pixelStride)
            val raw    = Bitmap.createBitmap(bmpWidth, image.height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(plane.buffer)
            val cropped = if (bmpWidth == image.width) {
                raw
            } else {
                val c = Bitmap.createBitmap(raw, 0, 0, image.width, image.height)
                raw.recycle()
                c
            }

            val frameHash = bitmapChangeHash(cropped)
            if (frameHash == lastUploadedHash) {
                cropped.recycle()
                return
            }
            lastUploadedHash = frameHash

            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                cropped.recycle()
                return
            }
            withContext(Dispatchers.Main) {
                val old = _localBitmap.value
                _localBitmap.value = cropped
                old?.recycle()
            }

            frameRef.child("frame").setValue(b64)
            frameRef.child("ts").setValue(System.currentTimeMillis())
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    private fun bitmapChangeHash(bmp: Bitmap): Int {
        val w = bmp.width; val h = bmp.height
        val total = w * h
        val step = maxOf(1, total / 256)
        val pixels = IntArray(total)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var hash = 0
        var i = 0
        while (i < total) {
            hash = hash * 31 + pixels[i]
            i += step
        }
        return hash
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIEWER — İzleme Başlatma
    // ─────────────────────────────────────────────────────────────────────────

    private fun startListeningFrames(sharerUid: String) {
        _isConnecting.value = true

        // Mod belirleme:
        // 1. "meta" dokümanı varsa ve mode="webrtc" → WebRTC modu (Android paylaşan)
        // 2. "meta" dokümanı yoksa, "offer" dokümanı var ve fromUid == sharerUid → WebRTC modu (Windows paylaşan)
        // 3. Aksi hâlde → RTDB frame relay modu
        val modeReg = webrtcRef.document("meta").addSnapshotListener { snap, _ ->
            val mode = snap?.getString("mode")
            if (mode == "webrtc" && snap?.getString("sharerUid") == sharerUid) {
                // Android kaynaklı WebRTC paylaşımı
                if (peerConnection == null) {
                    scope.launch { startViewingWebRtc(sharerUid) }
                }
            } else if (mode == null) {
                // meta dokümanı yok — Windows'un paylaşımı olabilir; offer dokümanını kontrol et
                webrtcRef.document("offer").get()
                    .addOnSuccessListener { offerSnap ->
                        val offerFromUid = offerSnap.getString("fromUid")
                        if (offerFromUid == sharerUid && peerConnection == null) {
                            scope.launch { startViewingWebRtc(sharerUid) }
                        } else if (offerFromUid != sharerUid && frameListener == null) {
                            startListeningRtdbFrames()
                        }
                    }
                    .addOnFailureListener {
                        if (frameListener == null) startListeningRtdbFrames()
                    }
            } else {
                // RTDB frame relay modu
                if (frameListener == null) {
                    startListeningRtdbFrames()
                }
            }
        }
        listeners.add(modeReg)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIEWER — WebRTC modu
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun startViewingWebRtc(sharerUid: String) {
        try {
            val cfServers = withContext(Dispatchers.IO) {
                runCatching { TurnConfig.fetchCloudflareCredentials() }.getOrNull()
            } ?: TurnConfig.getIceServers()

            // EGL context
            eglBase = EglBase.create()

            RoomAudioRouter.ensurePeerConnectionFactoryInitialized(context)

            val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

            audioDeviceModule = RoomAudioRouter.acquireAudioDeviceModule(context)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule!!)
                .createPeerConnectionFactory()

            RoomAudioRouter.setScreenShareActive(context, true)

            val rtcConfig = org.webrtc.PeerConnection.RTCConfiguration(cfServers).apply {
                sdpSemantics = org.webrtc.PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = peerConnectionFactory!!.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        // ICE candidate yaz (Windows uyumlu: "candidates" koleksiyonu)
                        scope.launch {
                            runCatching {
                                webrtcRef.document(uid)
                                    .collection("candidates")
                                    .document("${iceCandidateIndex++}")
                                    .set(mapOf(
                                        "sdpMid" to candidate.sdpMid,
                                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                                        "sdp" to candidate.sdp
                                    )).await()
                            }
                        }
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                        _event.tryEmit(ShareEvent.IceStatus(state.name))
                        android.util.Log.d(TAG, "Viewer ICE: $state")
                        if (state == PeerConnection.IceConnectionState.CONNECTED ||
                            state == PeerConnection.IceConnectionState.COMPLETED) {
                            _isConnecting.value = false
                        }
                        if (state == PeerConnection.IceConnectionState.FAILED) {
                            // WebRTC başarısız → RTDB fallback
                            scope.launch {
                                android.util.Log.w(TAG, "Viewer WebRTC FAILED, RTDB'ye geçiliyor")
                                stopWebRtcPeer()
                                startListeningRtdbFrames()
                            }
                        }
                    }

                    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                        val track = receiver?.track()
                        when (track) {
                            is VideoTrack -> {
                                android.util.Log.d(TAG, "Remote video track alındı")
                                _remoteVideoTrack.value = track
                                _isConnecting.value = false
                            }
                            is AudioTrack -> {
                                android.util.Log.d(TAG, "Remote audio track alındı — ses etkinleştiriliyor")
                                RoomAudioRouter.boostRemoteTrack(track)
                            }
                            else -> {}
                        }
                    }

                    override fun onAddStream(stream: MediaStream?) {
                        val videoTrack = stream?.videoTracks?.firstOrNull()
                        if (videoTrack != null) {
                            android.util.Log.d(TAG, "Remote stream video track alındı")
                            _remoteVideoTrack.value = videoTrack
                            _isConnecting.value = false
                        }
                        stream?.audioTracks?.forEach { RoomAudioRouter.boostRemoteTrack(it) }
                    }

                    override fun onRemoveStream(stream: MediaStream?) {}
                    override fun onDataChannel(dc: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                }
            )

            // Paylaşanın offer'ını dinle (Windows uyumlu: "offer" dokümanı { sdp, type, fromUid })
            val offerReg = webrtcRef.document("offer")
                .addSnapshotListener { snap, _ ->
                    val offerSdp = snap?.getString("sdp") ?: return@addSnapshotListener
                    val offerFromUid = snap.getString("fromUid") ?: return@addSnapshotListener
                    // Sadece bu oda'nın paylaşanının offer'ını işle
                    if (offerFromUid != sharerUid) return@addSnapshotListener

                    val pc = peerConnection ?: return@addSnapshotListener
                    if (pc.remoteDescription != null) return@addSnapshotListener

                    val sdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                    pc.setRemoteDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            // Answer oluştur
                            pc.createAnswer(object : SdpObserver {
                                override fun onCreateSuccess(answerSdp: SessionDescription) {
                                    val improvedAnswer = SessionDescription(
                                        answerSdp.type,
                                        improveVideoSdp(improveAudioSdp(answerSdp.description))
                                    )
                                    pc.setLocalDescription(object : SdpObserver {
                                        override fun onSetSuccess() {
                                            scope.launch {
                                                runCatching {
                                                    webrtcRef.document("answer").set(
                                                        mapOf("sdp" to improvedAnswer.description, "type" to "answer", "fromUid" to uid)
                                                    ).await()
                                                }
                                                listenForSharerIceCandidates(sharerUid)
                                            }
                                        }
                                        override fun onSetFailure(error: String?) { android.util.Log.e(TAG, "setLocalDesc (viewer) fail: $error") }
                                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                                        override fun onCreateFailure(error: String?) {}
                                    }, improvedAnswer)
                                }
                                override fun onCreateFailure(error: String?) { android.util.Log.e(TAG, "createAnswer fail: $error") }
                                override fun onSetSuccess() {}
                                override fun onSetFailure(error: String?) {}
                            }, MediaConstraints())
                        }
                        override fun onSetFailure(error: String?) { android.util.Log.e(TAG, "setRemoteDesc (offer) fail: $error") }
                        override fun onCreateSuccess(sdp: SessionDescription?) {}
                        override fun onCreateFailure(error: String?) {}
                    }, sdp)
                }
            webrtcSignalListeners.add(offerReg)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Viewer WebRTC init hatası: ${e.message}")
            stopWebRtcPeer()
            startListeningRtdbFrames()
        }
    }

    private fun listenForSharerIceCandidates(sharerUid: String) {
        val reg = webrtcRef.document(sharerUid).collection("candidates")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val doc = change.document
                    if (!addedIceCandidateIds.add(doc.id)) return@forEach
                    val sdpMid = doc.getString("sdpMid") ?: return@forEach
                    val sdpMLineIndex = (doc.getLong("sdpMLineIndex") ?: 0).toInt()
                    val sdp = doc.getString("sdp") ?: return@forEach
                    peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                }
            }
        webrtcSignalListeners.add(reg)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIEWER — RTDB modu
    // ─────────────────────────────────────────────────────────────────────────

    private fun startListeningRtdbFrames() {
        if (frameListener != null) return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val b64 = snapshot.child("frame").getValue(String::class.java) ?: return
                scope.launch(Dispatchers.IO) {
                    try {
                        val bytes  = Base64.decode(b64, Base64.NO_WRAP)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            withContext(Dispatchers.Main) {
                                val old = _remoteBitmap.value
                                _remoteBitmap.value = bitmap
                                old?.recycle()
                                _isConnecting.value = false
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _event.tryEmit(ShareEvent.Error(context.getString(R.string.share_err_capture, error.message ?: "")))
                _isConnecting.value = false
            }
        }

        frameRef.addValueEventListener(listener)
        frameListener = listener

        val onlineListen = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.getValue(Boolean::class.java) ?: return
                if (!online) {
                    scope.launch { runCatching { clearFirestore() } }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        frameRef.child("online").addValueEventListener(onlineListen)
        onlineListener = onlineListen
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DURDURMA
    // ─────────────────────────────────────────────────────────────────────────

    fun stopSharing() {
        captureJob?.cancel(); captureJob = null
        virtualDisplay?.release(); virtualDisplay = null
        mediaProjection?.stop(); mediaProjection = null
        imageReader?.close(); imageReader = null

        stopWebRtcPeer()

        _isSharing.value = false
        _localBitmap.value?.recycle()
        _localBitmap.value = null
        scope.launch {
            runCatching { frameRef.child("online").setValue(false).await() }
            runCatching { frameRef.removeValue().await() }
            runCatching {
                webrtcRef.document("meta").delete().await()
                webrtcRef.document("offer").delete().await()
                webrtcRef.document("answer").delete().await()
                webrtcRef.document(uid).delete().await()
            }
            runCatching { clearFirestore() }
            context.stopService(Intent(context, ScreenShareService::class.java))
            _event.tryEmit(ShareEvent.ShareStopped)
        }
    }

    fun forceStop() {
        scope.launch { runCatching { clearFirestore() } }
    }

    private fun stopWebRtcPeer() {
        webrtcSignalListeners.forEach { it.remove() }
        webrtcSignalListeners.clear()
        addedIceCandidateIds.clear()

        screenCapturerAndroid?.stopCapture()
        screenCapturerAndroid?.dispose()
        screenCapturerAndroid = null

        localVideoTrackInstance?.dispose()
        localVideoTrackInstance = null
        _localVideoTrack.value = null

        localVideoSource?.dispose()
        localVideoSource = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        if (audioDeviceModule != null) {
            RoomAudioRouter.releaseAudioDeviceModule()
            audioDeviceModule = null
        }
        RoomAudioRouter.setScreenShareActive(context, false)

        eglBase?.release()
        eglBase = null

        iceCandidateIndex = 0

        _remoteVideoTrack.value = null
    }

    private fun stopListeningFrames() {
        frameListener?.let { frameRef.removeEventListener(it) }
        frameListener = null
        onlineListener?.let { frameRef.child("online").removeEventListener(it) }
        onlineListener = null
        _remoteBitmap.value?.recycle()
        _remoteBitmap.value = null
        _isConnecting.value = false

        stopWebRtcPeer()
    }

    private suspend fun clearFirestore() {
        roomRef.update("screenShareUid", "").await()
        runCatching { frameRef.removeValue().await() }
    }

    // WatchRoomScreen uyumluluğu için
    fun getEglContext() = eglBase?.eglBaseContext

    /**
     * WebRTC SDP'ye Opus Discord-kalite parametreleri ekle.
     * 510 kbps + stereo + CBR + DTX-off → film/müzik sesi Discord seviyesinde gelir.
     * usedtx=0 → sessizlik anlarında bitrate düşmez, sabit kalite korunur.
     * cbr=1 → sabit bit hızı, ani ses geçişlerinde bozulma olmaz.
     */
    private fun improveAudioSdp(sdp: String): String = VoiceSdpUtil.tuneForScreenShareAudio(sdp)

    /**
     * Video bant genişliğini sınırlandırır — YouTube/film paylaşımında mobil donmasını engeller.
     * AS:2500 kbps + x-google-max-bitrate = 2500 kbps → akıcı 720p video.
     */
    private fun improveVideoSdp(sdp: String): String {
        val maxBitrate = 2500 // kbps
        val lines = sdp.split("\r\n").toMutableList()
        val result = mutableListOf<String>()
        var inVideoSection = false

        for (line in lines) {
            when {
                line.startsWith("m=video") -> {
                    inVideoSection = true
                    result.add(line)
                }
                line.startsWith("m=") -> {
                    inVideoSection = false
                    result.add(line)
                }
                inVideoSection && line.startsWith("c=") -> {
                    result.add(line)
                    // Bant genişliği satırı ekle (henüz yoksa)
                    if (result.none { it.startsWith("b=AS:") }) {
                        result.add("b=AS:$maxBitrate")
                    }
                }
                inVideoSection && line.startsWith("a=rtpmap:") -> {
                    result.add(line)
                    // VP8/VP9/H264 codec'i bul, fmtp ile max-bitrate ekle
                    val payloadType = line.substringAfter("a=rtpmap:").substringBefore(" ")
                    val codecName = line.substringAfter(" ").substringBefore("/").uppercase()
                    if (codecName in listOf("VP8", "VP9", "H264")) {
                        val fmtpLine = "a=fmtp:$payloadType x-google-max-bitrate=$maxBitrate;" +
                                "x-google-min-bitrate=300;x-google-start-bitrate=1000"
                        if (result.none { it.startsWith("a=fmtp:$payloadType ") }) {
                            result.add(fmtpLine)
                        }
                    }
                }
                else -> result.add(line)
            }
        }
        return result.joinToString("\r\n")
    }

    fun dispose() {
        listeners.forEach { it.remove() }
        listeners.clear()
        stopListeningFrames()

        captureJob?.cancel(); captureJob = null
        virtualDisplay?.release(); virtualDisplay = null
        mediaProjection?.stop(); mediaProjection = null
        imageReader?.close(); imageReader = null
        _isSharing.value = false
        _localBitmap.value?.recycle()
        _localBitmap.value = null

        RoomAudioRouter.setScreenShareActive(context, false)

        if (_sharerUid.value == uid) {
            // scope.cancel() henüz çağrılmadı; mevcut scope üzerinden ateşle
            scope.launch(Dispatchers.IO) {
                runCatching { frameRef.removeValue().await() }
                runCatching { roomRef.update("screenShareUid", "").await() }
            }
        }
        context.stopService(Intent(context, ScreenShareService::class.java))
        scope.cancel()
    }
}
