package com.watch.watchtofriend.ui.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun WebRtcVideoRenderer(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    if (videoTrack == null || eglBaseContext == null) return

    key(videoTrack.id()) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(eglBaseContext, null)
                    setMirror(mirror)
                    setEnableHardwareScaler(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    videoTrack.addSink(this)
                }
            },
            modifier = modifier,
            onRelease = { renderer ->
                videoTrack.removeSink(renderer)
                renderer.release()
            }
        )
    }
}
