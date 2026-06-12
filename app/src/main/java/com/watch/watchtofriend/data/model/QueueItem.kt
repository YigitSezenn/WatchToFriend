package com.watch.watchtofriend.data.model

// Paylaşımlı sıradaki (playlist) bir öğe.
data class QueueItem(
    val id: String = "",
    val url: String = "",
    val title: String = "",
    val addedBy: String = "",
    val addedByName: String = ""
)
