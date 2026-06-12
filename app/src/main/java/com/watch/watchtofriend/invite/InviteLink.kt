package com.watch.watchtofriend.invite

import android.content.Context
import android.content.Intent
import android.net.Uri

object InviteLink {

    /** Firebase Hosting — watchtofriend.app özel alanı henüz yönlendirilmemiş olabilir */
    const val WEB_BASE = "https://watchtofriend.web.app"
    private val WEB_HOSTS = setOf("watchtofriend.web.app", "watchtofriend.app")

    private val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun buildLink(roomId: String): String {
        val code = normalizeCode(roomId) ?: roomId.uppercase().trim()
        return "$WEB_BASE/join/$code"
    }

    fun buildDirectAppLink(roomId: String): String {
        val code = roomId.uppercase().trim()
        return "watchtofriend://join/$code"
    }

    fun buildShareMessage(roomTitle: String?, roomId: String): String {
        val title = roomTitle?.takeIf { it.isNotBlank() } ?: "Watch with Friends"
        val code = roomId.uppercase().trim()
        val link = buildLink(code)
        val appLink = buildDirectAppLink(code)
        return buildString {
            appendLine("🎬 $title odasına katıl!")
            appendLine(link)
            appendLine()
            appendLine(appLink)
            appendLine()
            append("Kod: $code")
        }
    }

    /** Yapıştırılan davet linki veya düz metinden oda kodu çıkarır. */
    fun extractCodeFromInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        normalizeCode(trimmed)?.let { return it }
        runCatching { parseCode(Uri.parse(trimmed)) }.getOrNull()?.let { return it }
        Regex("""(?i)/join/([A-Z0-9]{6})""").find(trimmed)?.groupValues?.getOrNull(1)?.let {
            normalizeCode(it)?.let { c -> return c }
        }
        Regex("""(?i)watchtofriend://join/([A-Z0-9]{6})""").find(trimmed)?.groupValues?.getOrNull(1)?.let {
            normalizeCode(it)?.let { c -> return c }
        }
        return null
    }

    fun parseCode(uri: Uri?): String? {
        if (uri == null) return null
        uri.getQueryParameter("code")?.let { return normalizeCode(it) }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        when {
            scheme == "https" && host in WEB_HOSTS -> parseJoinPath(uri.pathSegments)
            scheme == "watchtofriend" && host == "join" -> {
                val path = uri.path?.trim('/')?.trim() ?: return null
                if (path.isNotBlank()) return normalizeCode(path)
            }
        }
        return null
    }

    private fun parseJoinPath(segments: List<String>): String? {
        if (segments.size >= 2 && segments[0].equals("join", ignoreCase = true)) {
            return normalizeCode(segments[1])
        }
        return null
    }

    fun normalizeCode(raw: String): String? {
        val code = raw.uppercase().trim()
        if (code.length != 6) return null
        if (!code.all { it.isLetterOrDigit() }) return null
        return code
    }

    fun share(context: Context, roomTitle: String?, roomId: String) {
        val message = buildShareMessage(roomTitle, roomId)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, "Davet linkini paylaş"))
    }
}
