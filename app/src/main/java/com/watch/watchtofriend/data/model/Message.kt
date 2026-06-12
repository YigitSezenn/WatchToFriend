package com.watch.watchtofriend.data.model

data class Message(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderPhoto: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    // Sistem mesajı mı? (örn. "X odadan ayrıldı") — ortalı, gri gösterilir.
    val system: Boolean = false,
    val color: Long? = null,
    // Mesaja emoji tepkileri: uid -> emoji (her kullanıcı en fazla bir emoji)
    val reactions: Map<String, String> = emptyMap(),
    // Windows tarafı bu alanı mesajlara ekliyor; null-safe default ile geriye dönük uyumlu.
    val roomId: String = ""
)
