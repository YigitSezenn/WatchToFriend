package com.watch.watchtofriend.ui.watch

/**
 * WebRTC Opus SDP — mevcut fmtp satırına ekleme yapar; stereo/codec uyumluluğunu bozmaz.
 */
object VoiceSdpUtil {

    /** Konuşma: DTX kapalı + kısa paket (gecikme), stereo'ya dokunma */
    fun tuneForVoiceChat(sdp: String): String {
        val extra = mapOf(
            "minptime" to "10",
            "useinbandfec" to "1",
            "usedtx" to "0",
            "maxaveragebitrate" to "96000",
            "maxplaybackrate" to "48000"
        )
        return sanitizeForBrowserInterop(mergeOpusFmtp(sdp, extra))
    }

    /**
     * Android WebRTC a=ssrc satırları Chrome/Electron'da parse hatası verir
     * (ör. "msid:voice_stream voice_audio Invalid SDP line").
     * Unified Plan'da msid/mid yeterli — ssrc attribute satırları kaldırılır.
     */
    fun sanitizeForBrowserInterop(sdp: String): String {
        val lines = sdpLines(sdp).filter { line ->
            !line.startsWith("a=ssrc:") && !line.startsWith("a=ssrc-group:")
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    fun tuneForScreenShareAudio(sdp: String): String {
        val bitrate = if (RoomAudioRouter.isCombinedMode()) 160_000 else 510_000
        val extra = mapOf(
            "minptime" to "10",
            "useinbandfec" to "1",
            "stereo" to "1",
            "sprop-stereo" to "1",
            "maxaveragebitrate" to bitrate.toString(),
            "maxplaybackrate" to "48000",
            "cbr" to "1",
            "usedtx" to "0"
        )
        return mergeOpusFmtp(sdp, extra)
    }

    private fun sdpLines(sdp: String): MutableList<String> =
        sdp.replace("\r\n", "\n").split("\n").filter { it.isNotEmpty() }.toMutableList()

    private fun mergeOpusFmtp(sdp: String, extra: Map<String, String>): String {
        val lines = sdpLines(sdp)
        val fmtpIdx = lines.indexOfFirst { it.startsWith("a=fmtp:111 ") }
        if (fmtpIdx >= 0) {
            val existing = lines[fmtpIdx].removePrefix("a=fmtp:111 ").split(";")
                .mapNotNull { part ->
                    val kv = part.trim().split("=", limit = 2)
                    if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
                }.toMap().toMutableMap()
            existing.putAll(extra)
            val merged = existing.entries.joinToString(";") { "${it.key}=${it.value}" }
            lines[fmtpIdx] = "a=fmtp:111 $merged"
        } else {
            val rtpmapIdx = lines.indexOfFirst { it.startsWith("a=rtpmap:111 ") }
            if (rtpmapIdx >= 0) {
                val merged = extra.entries.joinToString(";") { "${it.key}=${it.value}" }
                lines.add(rtpmapIdx + 1, "a=fmtp:111 $merged")
            }
        }
        return lines.joinToString("\r\n")
    }
}
