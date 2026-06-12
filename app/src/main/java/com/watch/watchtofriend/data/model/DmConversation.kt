package com.watch.watchtofriend.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class DmConversation(
    val id: String = "",              // "{uid1}_{uid2}" küçük karşılaştırma sırasıyla
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageAt: Long = 0L,
    val lastSenderUid: String = "",
    // Okunmamış mesaj sayısı: uid -> count
    // Long kullanılıyor çünkü Firestore FieldValue.increment() sunucuda Long depolar;
    // Int olarak tanımlanırsa toObject() sırasında cast hatası oluşabilir.
    val unreadCount: Map<String, Long> = emptyMap()
)
