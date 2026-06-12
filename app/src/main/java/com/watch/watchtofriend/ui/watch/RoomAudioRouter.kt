package com.watch.watchtofriend.ui.watch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.AudioTrack
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val ROUTER_TAG = "RoomAudioRouter"

/**
 * Oda içi ses yönlendirme — ekran paylaşımı + sesli sohbet aynı anda çalışırken
 * tek AudioDeviceModule ve tutarlı AudioManager modu kullanır.
 *
 * Kulaklık / Bluetooth bağlıysa çıkış oraya yönlendirilir; harici cihaz yokken
 * dahili hoparlör tercih edilir (tüm cihazlarda aynı davranış).
 * Yönlendirme hataları yakalanır — sesli sohbete katılımı engellemez.
 */
object RoomAudioRouter {

    private const val REMOTE_TRACK_VOLUME = 8.0
    private const val XIAOMI_REMOTE_TRACK_VOLUME = 10.0

    @Volatile private var adm: JavaAudioDeviceModule? = null
    private var admRefCount = 0
    private val lock = Any()

    @Volatile private var voiceActive = false
    @Volatile private var screenShareActive = false

    private var focusRequest: AudioFocusRequest? = null
    private var lastContext: Context? = null

    private val reapplyHandler = Handler(Looper.getMainLooper())
    private var reapplyToken = 0

    /** Kullanıcının ayarladığı seviye — MIUI yeniden yönlendirmede ezilmesin */
    private val trackUserLevels = IdentityHashMap<AudioTrack, Float>()

    private var deviceCallback: AudioDeviceCallback? = null

    @Volatile private var factoryInitialized = false

    private val micRmsListeners = CopyOnWriteArrayList<(Double) -> Unit>()

    private var rmsSkipCounter = 0

    private val samplesReadyCallback = JavaAudioDeviceModule.SamplesReadyCallback { samples ->
        // Ses iş parçacığında hafif tut — her 2. kare, 4× alt örnekleme
        rmsSkipCounter++
        if (rmsSkipCounter % 2 != 0) return@SamplesReadyCallback
        val data = samples.data ?: return@SamplesReadyCallback
        if (data.size < 8) return@SamplesReadyCallback
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < data.size) {
            val lo = data[i].toInt() and 0xFF
            val hi = data[i + 1].toInt()
            var sample = lo or (hi shl 8)
            if (sample > 32767) sample -= 65536
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 8
        }
        if (count == 0) return@SamplesReadyCallback
        val rms = kotlin.math.sqrt(sum / count)
        micRmsListeners.forEach { it(rms) }
    }

    fun registerMicRmsListener(listener: (Double) -> Unit) {
        micRmsListeners.add(listener)
    }

    fun unregisterMicRmsListener(listener: (Double) -> Unit) {
        micRmsListeners.remove(listener)
    }

    fun ensurePeerConnectionFactoryInitialized(context: Context) {
        if (factoryInitialized) return
        synchronized(lock) {
            if (factoryInitialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            factoryInitialized = true
        }
    }

    fun acquireAudioDeviceModule(context: Context): JavaAudioDeviceModule {
        synchronized(lock) {
            if (adm == null) {
                val builder = JavaAudioDeviceModule.builder(context.applicationContext)
                    .setUseHardwareAcousticEchoCanceler(false)
                    .setUseHardwareNoiseSuppressor(false)
                // MIUI'da mono giriş bazen sessiz kalır — stereo gerekli
                if (isXiaomiFamily()) {
                    builder.setUseStereoOutput(true)
                    builder.setUseStereoInput(true)
                }
                builder.setSamplesReadyCallback(samplesReadyCallback)
                adm = builder.createAudioDeviceModule()
            }
            admRefCount++
            return adm!!
        }
    }

    fun releaseAudioDeviceModule() {
        synchronized(lock) {
            admRefCount = (admRefCount - 1).coerceAtLeast(0)
            if (admRefCount == 0) {
                adm?.release()
                adm = null
            }
        }
    }

    fun setVoiceActive(context: Context, active: Boolean) {
        voiceActive = active
        if (!active && !screenShareActive) clearTrackLevels()
        runCatching { applyAudioRoute(context.applicationContext) }
            .onFailure { Log.w(ROUTER_TAG, "setVoiceActive($active): ${it.message}") }
    }

    fun setScreenShareActive(context: Context, active: Boolean) {
        screenShareActive = active
        if (!active) {
            pruneEndedTracks()
            if (!voiceActive) clearTrackLevels()
        }
        runCatching { applyAudioRoute(context.applicationContext) }
            .onFailure { Log.w(ROUTER_TAG, "setScreenShareActive($active): ${it.message}") }
    }

    fun unregisterRemoteTrack(track: AudioTrack) {
        trackUserLevels.remove(track)
    }

    fun clearTrackLevels() {
        trackUserLevels.clear()
    }

    /** Kapatılmış / serbest bırakılmış WebRTC parçalarını listeden çıkar */
    fun pruneEndedTracks() {
        val stale = trackUserLevels.keys.filter { !isTrackLive(it) }
        stale.forEach { trackUserLevels.remove(it) }
    }

    fun boostRemoteTrack(track: AudioTrack) {
        if (!isTrackLive(track)) return
        applyRemoteTrackLevel(track, 1f)
    }

    /** Uzak ses parçası — temel yükseltme × kullanıcı seviyesi (0–1). */
    fun applyRemoteTrackLevel(track: AudioTrack, levelMultiplier: Float) {
        if (!isTrackLive(track)) return
        val level = levelMultiplier.coerceIn(0f, 1f)
        trackUserLevels[track] = level
        applyStoredLevel(track)
    }

    /** MIUI rota yenilemesinden sonra kayıtlı kullanıcı seviyelerini tekrar uygula */
    fun reapplyAllStoredTrackLevels() {
        val stale = mutableListOf<AudioTrack>()
        trackUserLevels.keys.toList().forEach { track ->
            if (!isTrackLive(track)) stale.add(track) else applyStoredLevel(track)
        }
        stale.forEach { trackUserLevels.remove(it) }
    }

    private fun isTrackLive(track: AudioTrack): Boolean {
        return try {
            track.state() == MediaStreamTrack.State.LIVE
        } catch (_: Exception) {
            false
        }
    }

    private fun applyStoredLevel(track: AudioTrack) {
        if (!isTrackLive(track)) {
            trackUserLevels.remove(track)
            return
        }
        val level = trackUserLevels[track] ?: return

        if (level <= 0f) {
            runCatching {
                track.setVolume(0.0)
                track.setEnabled(false)
            }.onFailure { trackUserLevels.remove(track) }
            return
        }

        runCatching { track.setEnabled(true) }
            .onFailure { trackUserLevels.remove(track); return }
        val base = if (isXiaomiFamily()) XIAOMI_REMOTE_TRACK_VOLUME else REMOTE_TRACK_VOLUME
        runCatching { track.setVolume(base * level) }
            .onFailure { trackUserLevels.remove(track) }
    }

    fun isCombinedMode(): Boolean = voiceActive && screenShareActive

    fun isXiaomiFamily(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m in XIAOMI_MANUFACTURERS || b in XIAOMI_MANUFACTURERS
    }

    /** Redmi Note 12 ailesi — MIUI'da en agresif ses profili */
    fun isRedmiNote12Family(): Boolean {
        if (!isXiaomiFamily()) return false
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()
        if ("note 12" in model || "note12" in model) return true
        return device in REDMI_NOTE_12_CODENAMES
    }

    /** MIUI ses ipucu gösterilsin mi (Redmi Note 12) */
    fun shouldShowMiuiAudioTip(): Boolean = isRedmiNote12Family()

    private val XIAOMI_MANUFACTURERS = setOf("xiaomi", "redmi", "poco", "blackshark")

    private val REDMI_NOTE_12_CODENAMES = setOf(
        "tapas", "sunstone", "moonstone", "ruby", "redwood", "marble", "sea"
    )

    private fun applyAudioRoute(context: Context) {
        lastContext = context.applicationContext
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val combined = voiceActive || screenShareActive

        if (!combined) {
            cancelReapply()
            unregisterDeviceGuard(am)
            @Suppress("DEPRECATION")
            runCatching { am.isSpeakerphoneOn = false }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { am.clearCommunicationDevice() }
            }
            runCatching { am.mode = AudioManager.MODE_NORMAL }
            abandonFocus(am)
            return
        }

        pruneEndedTracks()
        runCatching { am.mode = AudioManager.MODE_IN_COMMUNICATION }
        applyCommunicationRoute(am)
        reapplyAllStoredTrackLevels()
        requestCombinedFocus(am)
        registerDeviceGuard(am)
        if (isXiaomiFamily()) {
            scheduleRouteReapply()
        }
    }

    private fun scheduleRouteReapply() {
        cancelReapply()
        val token = ++reapplyToken
        val ctx = lastContext ?: return
        val delays = if (isRedmiNote12Family()) {
            longArrayOf(300, 800, 2000, 5000, 8000)
        } else {
            longArrayOf(400, 1000, 2500)
        }
        for (delay in delays) {
            reapplyHandler.postDelayed({
                if (token != reapplyToken) return@postDelayed
                if (!voiceActive && !screenShareActive) return@postDelayed
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                runCatching { am.mode = AudioManager.MODE_IN_COMMUNICATION }
                applyCommunicationRoute(am)
                reapplyAllStoredTrackLevels()
            }, delay)
        }
    }

    private fun cancelReapply() {
        reapplyToken++
        reapplyHandler.removeCallbacksAndMessages(null)
    }

    private fun registerDeviceGuard(am: AudioManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        unregisterDeviceGuard(am)
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                enforcePreferredRoute(am)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                enforcePreferredRoute(am)
            }
        }
        deviceCallback = cb
        runCatching { am.registerAudioDeviceCallback(cb, reapplyHandler) }
        enforcePreferredRoute(am)
    }

    private fun unregisterDeviceGuard(am: AudioManager) {
        deviceCallback?.let {
            runCatching { am.unregisterAudioDeviceCallback(it) }
        }
        deviceCallback = null
    }

    private fun enforcePreferredRoute(am: AudioManager) {
        if (!voiceActive && !screenShareActive) return
        val preferred = findPreferredOutputDevice(am) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val current = am.communicationDevice
            if (current?.id != preferred.id) {
                applyCommunicationRoute(am)
            }
        } else {
            applyCommunicationRoute(am)
        }
    }

    /** Kulaklık > Bluetooth (SCO/BLE) > dahili hoparlör */
    private fun findPreferredOutputDevice(am: AudioManager): AudioDeviceInfo? {
        val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.availableCommunicationDevices.toList()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        } else {
            emptyList()
        }

        fun firstOf(vararg types: Int): AudioDeviceInfo? =
            outputs.firstOrNull { it.type in types }

        firstOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE
        )?.let { return it }

        // WebRTC sesli sohbet için SCO/BLE — A2DP medya kanalı, iletişimde sorun çıkarır
        firstOf(
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        )?.let { return it }

        return findBuiltinSpeaker(am)
    }

    private fun applyCommunicationRoute(am: AudioManager) {
        val preferred = findPreferredOutputDevice(am)
        when {
            preferred != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val ok = runCatching { am.setCommunicationDevice(preferred) }.isSuccess
                if (!ok) routeSpeakerLegacy(am)
                else {
                    @Suppress("DEPRECATION")
                    runCatching { am.isSpeakerphoneOn = preferred.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                }
            }
            preferred?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> routeSpeakerLegacy(am)
            preferred != null -> {
                @Suppress("DEPRECATION")
                runCatching { am.isSpeakerphoneOn = false }
            }
            else -> routeSpeakerLegacy(am)
        }
    }

    /** Hoparlör — Bluetooth API'lerine dokunmadan (izin gerektirmez) */
    @Suppress("DEPRECATION")
    private fun routeSpeakerLegacy(am: AudioManager) {
        val speaker = findBuiltinSpeaker(am)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && speaker != null) {
            val ok = runCatching { am.setCommunicationDevice(speaker) }.isSuccess
            if (!ok) runCatching { am.isSpeakerphoneOn = true }
            else runCatching { am.isSpeakerphoneOn = true }
        } else {
            runCatching { am.isSpeakerphoneOn = true }
        }
    }

    private fun findBuiltinSpeaker(am: AudioManager): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
        return null
    }

    private fun requestCombinedFocus(am: AudioManager) {
        abandonFocus(am)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(
                    when {
                        voiceActive && screenShareActive -> AudioAttributes.USAGE_MEDIA
                        voiceActive -> AudioAttributes.USAGE_VOICE_COMMUNICATION
                        else -> AudioAttributes.USAGE_MEDIA
                    }
                )
                .setContentType(
                    when {
                        voiceActive && screenShareActive -> AudioAttributes.CONTENT_TYPE_MOVIE
                        voiceActive -> AudioAttributes.CONTENT_TYPE_SPEECH
                        else -> AudioAttributes.CONTENT_TYPE_MOVIE
                    }
                )
                .build()
            val focusListener = AudioManager.OnAudioFocusChangeListener { /* rota zaten RoomAudioRouter'da yönetiliyor */ }
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusListener, reapplyHandler)
                .build()
            runCatching { am.requestAudioFocus(focusRequest!!) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }
    }

    private fun abandonFocus(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            runCatching { am.abandonAudioFocus(null) }
        }
    }
}
