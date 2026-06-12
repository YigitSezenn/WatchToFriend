package com.watch.watchtofriend.data.model

/**
 * Arkadaşlık veya odaya davet isteği.
 * type: "friend" -> arkadaşlık isteği, "room" -> odaya davet isteği.
 * İstek kabul edilince ilgili işlem yapılır ve doküman silinir; red edilince sadece silinir.
 */
data class Request(
    val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val toUid: String = "",
    val type: String = "friend",
    val roomId: String = "",
    val timestamp: Long = 0L
)
