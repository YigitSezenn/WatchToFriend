package com.watch.watchtofriend.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val friendIds: List<String> = emptyList(),
    // Discord tarzı benzersiz kısa kimlik (arkadaş eklemek için).
    val friendCode: String = "",
    // Profil fotoğrafı — küçük JPEG base64 (Firebase Storage gerekmesin diye doc içinde).
    val photoBase64: String = "",
    // Çevrimiçi göstergesi için son aktiflik zamanı (ms). Uygulama açıkken periyodik güncellenir.
    val lastActive: Long = 0L,
    // Engellenen kullanıcılar (mesajları gizlenir, eklenemez)
    val blockedIds: List<String> = emptyList(),
    // Firebase Cloud Messaging token — push bildirim göndermek için
    val fcmToken: String = ""
)
