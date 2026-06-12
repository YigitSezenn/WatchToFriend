package com.watch.watchtofriend.invite

import android.content.Context
import android.content.Intent
import android.net.Uri

object InviteLink {

    /** Firebase Hosting — watchtofriend.app özel alanı henüz yönlendirilmemiş olabilir */
    const val WEB_BASE = "https://watchtofriend.web.app"
    private val WEB_HOSTS = setOf("watchtofriend.web.app", "watchtofriend.app")

    /** Tek satırlık temiz davet URL'si — WhatsApp vb. çoklu URL birleştirmesin diye ?code= kullanır. */
    fun buildLink(roomId: String): String {
        val code = resolveCode(roomId) ?: return "$WEB_BASE/join"
        return "$WEB_BASE/join?code=$code"
    }

    fun buildDirectAppLink(roomId: String): String {
        val code = resolveCode(roomId) ?: roomId.uppercase().trim()
        return "watchtofriend://join/$code"
    }

    /** Panoya / paylaşıma yalnızca tek link + düz metin kod (ek scheme URL yok). */
    fun buildShareMessage(roomTitle: String?, roomId: String): String {
        val title = roomTitle?.takeIf { it.isNotBlank() } ?: "Watch with Friends"
        val code = resolveCode(roomId) ?: roomId.uppercase().trim()
        val link = buildLink(code)
        return "🎬 $title odasına katıl!\n$link\n\nOda kodu: $code"
    }

    fun buildCopyText(roomId: String): String = buildLink(roomId)

    /** Yapıştırılan davet linki veya düz metinden oda kodu çıkarır. */
    fun extractCodeFromInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        normalizeCode(trimmed)?.let { return it }
        runCatching { parseCode(Uri.parse(trimmed)) }.getOrNull()?.let { return it }
        return extractCodeFromBlob(trimmed)
    }

    fun parseCode(uri: Uri?): String? {
        if (uri == null) return null
        uri.getQueryParameter("code")?.let { extractCodeFromBlob(it) }?.let { return it }

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        when {
            scheme == "https" && host in WEB_HOSTS -> {
                parseJoinPath(uri.pathSegments)?.let { return it }
                uri.encodedPath?.let { extractCodeFromBlob(it) }?.let { return it }
            }
            scheme == "watchtofriend" && host == "join" -> {
                val path = uri.path?.trim('/')?.trim().orEmpty()
                if (path.isNotBlank()) return extractCodeFromBlob(path)
            }
        }
        uri.toString().let { extractCodeFromBlob(it) }?.let { return it }
        return null
    }

    private fun parseJoinPath(segments: List<String>): String? {
        if (segments.isEmpty() || !segments[0].equals("join", ignoreCase = true)) return null
        if (segments.size >= 2) return extractCodeFromBlob(segments[1])
        return null
    }

    /** Bozuk / birleştirilmiş URL'lerden ilk geçerli 6 haneli kodu çıkarır. */
    fun extractCodeFromBlob(raw: String): String? {
        if (raw.isBlank()) return null
        var text = raw
        repeat(3) {
            try {
                val decoded = Uri.decode(text)
                if (decoded == text) return@repeat
                text = decoded
            } catch (_: Exception) {
                return@repeat
            }
        }

        normalizeCode(text)?.let { return it }

        val patterns = listOf(
            Regex("""(?i)[?&]code=([A-Z0-9]{6})"""),
            Regex("""(?i)/join/([A-Z0-9]{6})"""),
            Regex("""(?i)watchtofriend:?/+join/+([A-Z0-9]{6})"""),
            Regex("""(?i)(?:Kod|Code|Oda kodu):\s*([A-Z0-9]{6})"""),
            Regex("""(?i)^([A-Z0-9]{6})""")
        )
        for (pattern in patterns) {
            pattern.find(text)?.groupValues?.getOrNull(1)?.let { normalizeCode(it)?.let { c -> return c } }
        }
        return null
    }

    fun normalizeCode(raw: String): String? {
        val code = raw.uppercase().trim()
        if (code.length != 6) return null
        if (!code.all { it.isLetterOrDigit() }) return null
        return code
    }

    private fun resolveCode(roomId: String): String? {
        return extractCodeFromBlob(roomId) ?: normalizeCode(roomId.uppercase().trim())
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
