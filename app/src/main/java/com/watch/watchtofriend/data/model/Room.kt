package com.watch.watchtofriend.data.model

import com.google.firebase.firestore.PropertyName

data class Room(
    val roomId: String = "",
    val hostUid: String = "",
    // Firestore'da eski odalarda bulunur; yok sayılır (mapper uyarısını önler)
    val hostName: String = "",
    val videoUrl: String = "",
    // Firestore "is" önekini atıp alanı "playing" yapmasın diye sabitliyoruz.
    // Böylece update("isPlaying") ile okuma/yazma aynı alanı kullanır.
    @get:PropertyName("isPlaying")
    @set:PropertyName("isPlaying")
    var isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val updatedAt: Long = 0L,
    // Yalnızca video URL'si değişince güncellenir → WebView'i yeniden yüklemek için.
    // (play/pause'da değişmez ki sürekli reload olmasın.)
    val videoVersion: Long = 0L,
    val memberUids: List<String> = emptyList(),
    // Yetkili (yardımcı host) uid'leri — video kontrolüne sahip olurlar.
    val moderators: List<String> = emptyList(),
    val lastUpdatedBy: String = "",
    // Canlı katılımcılar (presence) — oda dokümanı içinde tutulur ki ekstra
    // Firestore kuralı gerekmesin. uid -> son görülme(ms), uid -> görünen ad.
    val presence: Map<String, Long> = emptyMap(),
    val presenceNames: Map<String, String> = emptyMap(),
    // Tıklama-yansıtma (film sitesi "oynat" düğmesini birlikte basmak için):
    // bir üye ana-sayfada tıklayınca seçici yayılır, diğerleri aynı öğeye tıklar.
    val clickSel: String = "",
    val clickAt: Long = 0L,
    val clickBy: String = "",
    // Oda adı/başlığı (oluştururken)
    val title: String = "",
    // Herkese açık mı? (Keşfet sekmesinde görünür)
    val discoverable: Boolean = false,
    // İsteğe bağlı oda şifresi (boşsa şifresiz) ve maksimum üye (0 = sınırsız)
    val password: String = "",
    val maxMembers: Int = 0,
    // Paylaşımlı sıra (playlist) — sıradaki videolar
    val queue: List<QueueItem> = emptyList(),
    // Senkron emoji tepkisi (her tepki üzerine yazılır; istemciler reactionAt değişince gösterir)
    val reaction: String = "",
    val reactionAt: Long = 0L,
    val reactionBy: String = "",
    // Yazıyor… göstergesi: uid -> son yazma zamanı(ms)
    val typing: Map<String, Long> = emptyMap(),
    // Kontrol isteği: yetkisi olmayan üye "durdur/oynat" rica eder; herkese banner gösterilir,
    // host/yetkililer tek dokunuşla uygular.
    val ctrlReqAt: Long = 0L,
    val ctrlReqBy: String = "",
    val ctrlReqName: String = "",
    val ctrlReqAction: String = "", // "pause" | "play"
    // Sabitlenmiş mesaj: host/yetkililer bir mesajı sabitler, chat üstünde banner gösterilir.
    val pinnedMessage: String = "",
    val pinnedMessageSenderName: String = "",
    // Oda zamanlama: 0 = hemen/başladı, >0 = ileride planlanmış (ms epoch)
    val scheduledAt: Long = 0L,
    // Oylama (Poll): host soru oluşturur, üyeler oy verir. pollVotes: optionIndex -> voteCount
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, Int> = emptyMap(),       // "0","1",... -> oy sayısı
    val pollVoterChoice: Map<String, Int> = emptyMap(), // uid -> seçilen option index
    // Ekran paylaşımı: paylaşan uid (boşsa paylaşım yok)
    val screenShareUid: String = ""
)
