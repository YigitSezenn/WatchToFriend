package com.watch.watchtofriend.data.model

// Odada o an aktif olan (canlı) kullanıcı. lastSeen ~periyodik güncellenir.
data class Presence(
    val uid: String = "",
    val name: String = "",
    val lastSeen: Long = 0L
)
