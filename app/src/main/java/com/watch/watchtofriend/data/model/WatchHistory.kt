package com.watch.watchtofriend.data.model

data class WatchHistory(
    val id: String = "",
    val videoUrl: String = "",
    val title: String = "",
    val roomId: String = "",
    val memberCount: Int = 0,
    val watchedAt: Long = 0L
)
