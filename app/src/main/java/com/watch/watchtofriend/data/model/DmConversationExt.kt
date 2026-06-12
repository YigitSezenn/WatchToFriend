package com.watch.watchtofriend.data.model

/** Firestore doc id veya participant uid çiftinden geçerli DM kimliği üretir. */
fun DmConversation.resolvedId(fallbackMyUid: String = ""): String {
    if (id.isNotBlank()) return id
    val uids = participantUids.filter { it.isNotBlank() }.distinct().sorted()
    if (uids.size >= 2) return uids.joinToString("_")
    if (uids.size == 1 && fallbackMyUid.isNotBlank() && fallbackMyUid != uids[0]) {
        return listOf(fallbackMyUid, uids[0]).sorted().joinToString("_")
    }
    return ""
}
