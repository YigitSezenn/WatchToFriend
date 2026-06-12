package com.watch.watchtofriend.ui.watch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * Doğrudan video linki (.mp4 / .m3u8 (HLS) / .webm / .mkv) oynatıcısı.
 * WebView yerine ExoPlayer kullanır → Full HD + kare-kare net senkron (sitenin
 * cross-origin/iframe/reklam sorunları olmadan). Senkron mantığı WebView ile aynı:
 * host/yetkililer durumu yayar, herkes uygular.
 */

private val DIRECT_VIDEO_REGEX = Regex("\\.(m3u8|mp4|m4v|webm|mkv|mov|ts|mpd)(\\?.*)?$", RegexOption.IGNORE_CASE)

fun isDirectVideoUrl(url: String): Boolean {
    val u = url.trim().lowercase()
    if (!u.startsWith("http")) return false
    return DIRECT_VIDEO_REGEX.containsMatchIn(u)
}

/** Uzak komutları ExoPlayer'a uygulayan ve yankıyı bastıran kontrolcü. */
class ExoController(val player: ExoPlayer) {
    @Volatile
    var suppressUntil: Long = 0L

    fun applyRemote(isPlaying: Boolean, sec: Double, doSeek: Boolean, force: Boolean) {
        suppressUntil = System.currentTimeMillis() + 1500
        if (doSeek) {
            val curSec = player.currentPosition / 1000.0
            val durMs = player.duration
            var target = sec
            if (durMs > 1000 && target > durMs / 1000.0 - 1) {
                target = (durMs / 1000.0 - 1).coerceAtLeast(0.0)
            }
            if (force || abs(curSec - target) > 1.5) {
                player.seekTo((target * 1000).toLong())
            }
        }
        player.playWhenReady = isPlaying
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DirectVideoPlayer(
    videoUrl: String,
    startPositionMs: Long,
    startPlaying: Boolean,
    canControl: () -> Boolean,
    handleAudioFocus: Boolean = true,
    onReady: () -> Unit,
    onPlayStateChanged: (Boolean, Long) -> Unit,
    onEnded: () -> Unit,
    onProgress: (Double, Double) -> Unit,
    onController: (ExoController?) -> Unit
) {
    val context = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(context)
            // Ses odağı yönetimi: başka uygulama ses açınca otomatik duraklat/kıs
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                handleAudioFocus
            )
            .build().apply {
                // Akış sırasında CPU/Wi-Fi uyanık kalsın (ağ kaynaklı takılmaları azaltır)
                setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            }
    }
    val controller = remember(exo) { ExoController(exo) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(false) }

    LaunchedEffect(handleAudioFocus) {
        exo.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            handleAudioFocus
        )
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorMsg = "Video açılamadı (link geçersiz veya desteklenmiyor olabilir)"
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (System.currentTimeMillis() < controller.suppressUntil) return
                if (canControl()) onPlayStateChanged(playWhenReady, exo.currentPosition)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Kullanıcı ileri/geri sardıysa konumu yay (seek senkronu)
                if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                    System.currentTimeMillis() >= controller.suppressUntil && canControl()
                ) {
                    onPlayStateChanged(exo.isPlaying, newPosition.positionMs)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) { errorMsg = null; onReady() }
                else if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        exo.addListener(listener)
        onController(controller)
        onDispose {
            onController(null)
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Video kaynağını ayarla + başlangıç konumu/oynatma
    LaunchedEffect(videoUrl) {
        exo.setMediaItem(MediaItem.fromUri(videoUrl))
        exo.prepare()
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.playWhenReady = startPlaying
    }

    // İlerleme/süre bildirimi (~1sn) — çubuk + sapma göstergesi
    LaunchedEffect(exo) {
        while (true) {
            val durSec = if (exo.duration > 0) exo.duration / 1000.0 else 0.0
            onProgress(exo.currentPosition / 1000.0, durSec)
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            }
        )
        if (buffering && errorMsg == null) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
        errorMsg?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
    }
}
