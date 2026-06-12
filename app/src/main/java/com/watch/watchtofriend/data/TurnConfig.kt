package com.watch.watchtofriend.data

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * TURN sunucu yapılandırması — iki kaynak:
 *  1. Cloudflare TURN (short-lived credential, birincil)
 *  2. Firebase Remote Config (yedek / ücretsiz Open Relay)
 *
 * Cloudflare TURN API:
 *   POST https://rtc.live.cloudflare.com/v1/turn/keys/{tokenId}/credentials/generate
 *   Authorization: Bearer {apiToken}
 *   Body: {"ttl": 86400}
 *   Response: {"iceServers": {"urls": [...], "username": "...", "credential": "..."}}
 */
object TurnConfig {

    // ── Cloudflare TURN ──────────────────────────────────────────────────────
    // Firebase Remote Config parametre adları (değerler Console'dan gelir)
    private const val RC_KEY_TOKEN_ID  = "cf_turn_token_id"
    private const val RC_KEY_API_TOKEN = "cf_turn_api_token"

    private val CF_STUN_URLS = listOf("stun:stun.cloudflare.com:3478")
    private val CF_TURN_URLS = listOf(
        "turn:turn.cloudflare.com:3478?transport=udp",
        "turn:turn.cloudflare.com:3478?transport=tcp",
        "turns:turn.cloudflare.com:5349?transport=tcp"
    )

    // ── Varsayılan değerler (Remote Config / Open Relay fallback) ────────────
    private val DEFAULT_URLS = listOf(
        "turn:openrelay.metered.ca:80",
        "turn:openrelay.metered.ca:80?transport=tcp",
        "turn:openrelay.metered.ca:443",
        "turn:openrelay.metered.ca:443?transport=tcp",
        "turns:openrelay.metered.ca:443",
    )
    private const val DEFAULT_USERNAME = "openrelayproject"
    private const val DEFAULT_PASSWORD = "openrelayproject"

    private var initialized = false

    // Cloudflare'den alınan credential'lar (önbellek — TTL dolunca yenile)
    @Volatile private var cfUsername: String? = null
    @Volatile private var cfCredential: String? = null
    @Volatile private var cfFetchedAt: Long = 0L
    private const val CF_CACHE_MS = 23 * 60 * 60 * 1000L  // 23 saat (TTL 24h'den biraz kısa)

    suspend fun init() {
        if (initialized) return
        try {
            val rc = Firebase.remoteConfig
            rc.setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
            ).await()
            rc.setDefaultsAsync(
                mapOf(
                    "turn_urls"          to DEFAULT_URLS.joinToString(","),
                    "turn_username"      to DEFAULT_USERNAME,
                    "turn_password"      to DEFAULT_PASSWORD,
                    RC_KEY_TOKEN_ID      to "",
                    RC_KEY_API_TOKEN     to "",
                )
            ).await()
            rc.fetchAndActivate().await()
            initialized = true
        } catch (_: Exception) {
            initialized = true
        }
    }

    /**
     * Cloudflare TURN için short-lived credential üret.
     * Token bilgileri Firebase Remote Config'den okunur (hardcoded değil).
     * Başarılı olursa ICE server listesi döndürür.
     * Başarısız olursa null döner (fallback devreye girer).
     */
    suspend fun fetchCloudflareCredentials(): List<PeerConnection.IceServer>? {
        return withContext(Dispatchers.IO) {
            try {
                // Önbellekte geçerli credential varsa yeniden fetch etme
                val now = System.currentTimeMillis()
                if (cfUsername != null && cfCredential != null && now - cfFetchedAt < CF_CACHE_MS) {
                    return@withContext buildCloudflareIceServers(cfUsername!!, cfCredential!!)
                }

                // Remote Config'den token bilgilerini al
                val rc = Firebase.remoteConfig
                val tokenId  = rc.getString(RC_KEY_TOKEN_ID).trim().ifBlank { null }
                val apiToken = rc.getString(RC_KEY_API_TOKEN).trim().ifBlank { null }
                if (tokenId == null || apiToken == null) {
                    android.util.Log.w(
                        "TurnConfig",
                        "Cloudflare TURN Remote Config boş (cf_turn_token_id / cf_turn_api_token), fallback kullanılıyor"
                    )
                    return@withContext null
                }

                android.util.Log.d("TurnConfig", "Cloudflare TURN token Remote Config'den okundu (keyId=${tokenId.take(8)}…)")

                val apiUrl = "https://rtc.live.cloudflare.com/v1/turn/keys/$tokenId/credentials/generate"
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiToken")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                OutputStreamWriter(conn.outputStream).use { it.write("""{"ttl": 86400}""") }

                if (conn.responseCode != 200 && conn.responseCode != 201) {
                    val errBody = runCatching {
                        conn.errorStream?.bufferedReader()?.readText()
                    }.getOrNull().orEmpty()
                    android.util.Log.w(
                        "TurnConfig",
                        "Cloudflare TURN HTTP ${conn.responseCode}: $errBody"
                    )
                    return@withContext null
                }

                val body = conn.inputStream.bufferedReader().readText()
                val root = JSONObject(body)
                val parsed = parseCloudflareIceServers(root) ?: return@withContext null

                // Önbelleğe al
                cfUsername = parsed.username
                cfCredential = parsed.credential
                cfFetchedAt = now

                android.util.Log.d("TurnConfig", "Cloudflare TURN credential alındı")
                buildCloudflareIceServers(parsed.username, parsed.credential, parsed.urls)
            } catch (e: Exception) {
                android.util.Log.e("TurnConfig", "Cloudflare TURN fetch hatası: ${e.message}")
                null
            }
        }
    }

    private data class CfIceCreds(val username: String, val credential: String, val urls: List<String>)

    /** Cloudflare API yanıtı: dizi (güncel) veya tek nesne (eski) formatını destekler. */
    private fun parseCloudflareIceServers(root: JSONObject): CfIceCreds? {
        val iceServers = root.opt("iceServers") ?: return null

        return when (iceServers) {
            is JSONArray -> {
                var username: String? = null
                var credential: String? = null
                val urls = mutableListOf<String>()
                for (i in 0 until iceServers.length()) {
                    val server = iceServers.optJSONObject(i) ?: continue
                    urls += jsonUrls(server.opt("urls"))
                    if (server.has("username")) username = server.getString("username")
                    if (server.has("credential")) credential = server.getString("credential")
                }
                if (username.isNullOrBlank() || credential.isNullOrBlank()) null
                else CfIceCreds(username, credential, urls.distinct())
            }
            is JSONObject -> {
                val username = iceServers.optString("username").ifBlank { null }
                val credential = iceServers.optString("credential").ifBlank { null }
                if (username == null || credential == null) null
                else CfIceCreds(username, credential, jsonUrls(iceServers.opt("urls")))
            }
            else -> null
        }
    }

    private fun jsonUrls(value: Any?): List<String> = when (value) {
        is JSONArray -> buildList {
            for (i in 0 until value.length()) add(value.getString(i))
        }
        is String -> listOf(value)
        else -> emptyList()
    }

    private fun buildCloudflareIceServers(
        username: String,
        credential: String,
        apiUrls: List<String> = emptyList()
    ): List<PeerConnection.IceServer> {
        val urls = apiUrls.ifEmpty { CF_STUN_URLS + CF_TURN_URLS }
        val stunUrls = urls.filter { it.startsWith("stun:", ignoreCase = true) }
        val turnUrls = urls.filter { it.startsWith("turn", ignoreCase = true) }

        return buildList {
            val stuns = stunUrls.ifEmpty { CF_STUN_URLS }
            stuns.forEach { url ->
                add(PeerConnection.IceServer.builder(url).createIceServer())
            }
            val turns = turnUrls.ifEmpty { CF_TURN_URLS }
            add(
                PeerConnection.IceServer.builder(turns)
                    .setUsername(username)
                    .setPassword(credential)
                    .createIceServer()
            )
        }
    }

    /** Firebase Remote Config'den (veya Open Relay varsayılan) ICE sunucuları döndür. */
    fun getIceServers(): List<PeerConnection.IceServer> {
        val rc = Firebase.remoteConfig

        val urlsCsv = rc.getString("turn_urls").ifBlank {
            DEFAULT_URLS.joinToString(",")
        }
        val username = rc.getString("turn_username").ifBlank { DEFAULT_USERNAME }
        val password = rc.getString("turn_password").ifBlank { DEFAULT_PASSWORD }

        val turnUrls = urlsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }

        return buildList {
            // Google STUN
            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())
            // TURN — Remote Config'den gelen veya varsayılan
            turnUrls.forEach { url ->
                add(
                    PeerConnection.IceServer.builder(url)
                        .setUsername(username)
                        .setPassword(password)
                        .createIceServer()
                )
            }
        }
    }
}
