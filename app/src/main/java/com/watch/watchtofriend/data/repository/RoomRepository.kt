package com.watch.watchtofriend.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.watch.watchtofriend.data.model.DmConversation
import com.watch.watchtofriend.data.model.Message
import com.watch.watchtofriend.data.model.QueueItem
import com.watch.watchtofriend.data.model.Request
import com.watch.watchtofriend.data.model.Room
import com.watch.watchtofriend.data.model.User
import com.watch.watchtofriend.data.model.WatchHistory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RoomRepository {
    private val db = FirebaseFirestore.getInstance()

    // --- Sunucu saati telafisi (profesyonel senkron için) ---
    // VM, fetchServerOffset ile bunu doldurur; tüm zaman damgaları sunucu-saatine yaklaşır.
    @Volatile
    var serverOffset: Long = 0L

    fun serverNow(): Long = System.currentTimeMillis() + serverOffset

    // Firestore sunucu zaman damgası yazıp okuyarak yerel saat ile sunucu arasındaki
    // farkı (offset) ölçer. Round-trip ortalaması alınarak ~ms hassasiyetinde tahmin.
    suspend fun fetchServerOffset(uid: String): Long {
        if (uid.isBlank()) return 0L
        return try {
            val ref = db.collection("users").document(uid)
            val t0 = System.currentTimeMillis()
            ref.update("clockProbe", FieldValue.serverTimestamp()).await()
            val snap = ref.get(com.google.firebase.firestore.Source.SERVER).await()
            val t1 = System.currentTimeMillis()
            val server = snap.getTimestamp("clockProbe")?.toDate()?.time ?: return serverOffset
            (server - (t0 + t1) / 2).also { serverOffset = it }
        } catch (e: Exception) {
            serverOffset
        }
    }

    suspend fun createRoom(
        hostUid: String, videoUrl: String, title: String = "",
        discoverable: Boolean = false, password: String = "", maxMembers: Int = 0,
        scheduledAt: Long = 0L
    ): String {
        val roomId = generateRoomCode()
        val room = Room(
            roomId = roomId,
            hostUid = hostUid,
            videoUrl = videoUrl,
            title = title.trim(),
            discoverable = discoverable,
            password = password.trim(),
            maxMembers = maxMembers,
            isPlaying = true,
            currentPositionMs = 0L,
            updatedAt = serverNow(),
            videoVersion = serverNow(),
            memberUids = listOf(hostUid),
            lastUpdatedBy = hostUid,
            scheduledAt = scheduledAt
        )
        db.collection("rooms").document(roomId).set(room).await()
        return roomId
    }

    // ---- Oylama (Poll) ----

    suspend fun createPoll(roomId: String, question: String, options: List<String>) {
        db.collection("rooms").document(roomId).update(
            mapOf(
                "pollQuestion" to question,
                "pollOptions" to options,
                "pollVotes" to options.indices.associate { "$it" to 0 },
                "pollVoterChoice" to emptyMap<String, Int>()
            )
        ).await()
    }

    suspend fun votePoll(roomId: String, uid: String, optionIndex: Int) {
        // Transaction kullanılıyor: iki eş zamanlı oy okuma+yazma race condition'ını önler.
        val ref = db.collection("rooms").document(roomId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val room = snap.toObject(Room::class.java) ?: return@runTransaction
            val prevChoice = room.pollVoterChoice[uid]
            val votes = room.pollVotes.toMutableMap()
            // Önceki oy varsa düş
            if (prevChoice != null) {
                val prev = "$prevChoice"
                votes[prev] = maxOf(0, (votes[prev] ?: 0) - 1)
            }
            // Yeni oy ekle
            val key = "$optionIndex"
            votes[key] = (votes[key] ?: 0) + 1
            tx.update(
                ref,
                mapOf(
                    "pollVotes" to votes,
                    "pollVoterChoice.$uid" to optionIndex
                )
            )
        }.await()
    }

    suspend fun clearPoll(roomId: String) {
        db.collection("rooms").document(roomId).update(
            mapOf(
                "pollQuestion" to "",
                "pollOptions" to emptyList<String>(),
                "pollVotes" to emptyMap<String, Int>(),
                "pollVoterChoice" to emptyMap<String, Int>()
            )
        ).await()
    }

    // Katılma sonucu: hata mesajı null ise başarılı.
    sealed class JoinResult {
        data class Success(val room: Room) : JoinResult()
        object NotFound : JoinResult()
        object WrongPassword : JoinResult()
        object Full : JoinResult()
    }

    suspend fun joinRoom(roomId: String, uid: String, password: String = ""): Room? {
        // Geriye dönük uyumluluk için Room? döndüren sürüm (şifresiz/limitsiz akış)
        val res = joinRoomChecked(roomId, uid, password)
        return (res as? JoinResult.Success)?.room
    }

    suspend fun joinRoomChecked(roomId: String, uid: String, password: String = ""): JoinResult {
        val doc = db.collection("rooms").document(roomId).get().await()
        val room = doc.toObject(Room::class.java) ?: return JoinResult.NotFound
        val wasMember = room.memberUids.contains(uid)
        // Zaten üyeyse kontrolleri atla
        if (!wasMember) {
            if (room.password.isNotBlank() && room.password != password.trim()) return JoinResult.WrongPassword
            if (room.maxMembers > 0 && room.memberUids.size >= room.maxMembers) return JoinResult.Full
        }
        db.collection("rooms").document(roomId)
            .update("memberUids", FieldValue.arrayUnion(uid)).await()
        // Yalnızca gerçekten yeni katılımda "katıldı" sistem mesajı gönder
        if (!wasMember) {
            val name = getUser(uid)?.displayName?.ifBlank {
                com.watch.watchtofriend.ui.locale.AppStrings.get(com.watch.watchtofriend.R.string.common_some_user)
            } ?: com.watch.watchtofriend.ui.locale.AppStrings.get(com.watch.watchtofriend.R.string.common_some_user)
            try {
                sendSystemMessage(
                    roomId,
                    com.watch.watchtofriend.ui.locale.AppStrings.get(
                        com.watch.watchtofriend.R.string.watch_user_joined,
                        name
                    )
                )
            } catch (_: Exception) {}
        }
        return JoinResult.Success(room)
    }

    // Oda sahibini başka bir üyeye devret (yalnızca mevcut host UI'da çağırır).
    suspend fun transferHost(roomId: String, newHostUid: String) {
        if (roomId.isBlank() || newHostUid.isBlank()) return
        db.collection("rooms").document(roomId)
            .update("hostUid", newHostUid).await()
    }

    // Üyeyi yetkili yap / yetkisini al (yalnızca host UI'da çağırır).
    suspend fun setModerator(roomId: String, targetUid: String, makeModerator: Boolean) {
        if (roomId.isBlank() || targetUid.isBlank()) return
        val change = if (makeModerator)
            FieldValue.arrayUnion(targetUid)
        else
            FieldValue.arrayRemove(targetUid)
        db.collection("rooms").document(roomId).update("moderators", change).await()
    }

    suspend fun updateRoomSettings(roomId: String, title: String, discoverable: Boolean) {
        db.collection("rooms").document(roomId).update(
            mapOf("title" to title.trim(), "discoverable" to discoverable)
        ).await()
    }

    // ---- Engelleme / Şikayet ----
    suspend fun blockUser(myUid: String, otherUid: String) {
        if (myUid.isBlank() || otherUid.isBlank()) return
        db.collection("users").document(myUid)
            .update("blockedIds", FieldValue.arrayUnion(otherUid)).await()
        // Engellenen kişiyi arkadaşlıktan da çıkar (çift yönlü)
        try { removeFriend(myUid, otherUid) } catch (_: Exception) {}
    }

    suspend fun unblockUser(myUid: String, otherUid: String) {
        if (myUid.isBlank() || otherUid.isBlank()) return
        db.collection("users").document(myUid)
            .update("blockedIds", FieldValue.arrayRemove(otherUid)).await()
    }

    suspend fun reportUser(fromUid: String, aboutUid: String, reason: String, roomId: String) {
        val ref = db.collection("reports").document()
        ref.set(
            mapOf(
                "id" to ref.id, "fromUid" to fromUid, "aboutUid" to aboutUid,
                "reason" to reason, "roomId" to roomId,
                "timestamp" to serverNow()
            )
        ).await()
    }

    fun observeRoom(roomId: String): Flow<Room?> = callbackFlow {
        if (roomId.isBlank()) { trySend(null); close(); return@callbackFlow }
        val reg: ListenerRegistration = db.collection("rooms").document(roomId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(Room::class.java))
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateVideoState(roomId: String, isPlaying: Boolean, positionMs: Long, actorUid: String) {
        db.collection("rooms").document(roomId).update(
            mapOf(
                "isPlaying" to isPlaying,
                "currentPositionMs" to positionMs,
                "updatedAt" to serverNow(),
                "lastUpdatedBy" to actorUid
            )
        ).await()
    }

    suspend fun updateVideoUrl(roomId: String, url: String, actorUid: String) {
        db.collection("rooms").document(roomId).update(
            mapOf(
                "videoUrl" to url,
                "currentPositionMs" to 0L,
                "isPlaying" to true,
                "updatedAt" to serverNow(),
                "videoVersion" to serverNow(),
                "lastUpdatedBy" to actorUid
            )
        ).await()
    }

    // Tarayıcı-gibi gezinme: sadece geçerli sayfa URL'sini yayar (videoVersion'a
    // dokunmaz → WebView yeniden kurulmaz, host kendi sayfasını çift yüklemez).
    suspend fun navigateTo(roomId: String, url: String, actorUid: String) {
        db.collection("rooms").document(roomId).update(
            mapOf(
                "videoUrl" to url,
                "currentPositionMs" to 0L,
                "isPlaying" to false,
                "updatedAt" to serverNow(),
                "lastUpdatedBy" to actorUid
            )
        ).await()
    }

    // ---- Paylaşımlı sıra (playlist) ----
    suspend fun addToQueue(roomId: String, item: QueueItem) {
        db.collection("rooms").document(roomId)
            .update("queue", FieldValue.arrayUnion(item)).await()
    }

    suspend fun removeFromQueue(roomId: String, item: QueueItem) {
        db.collection("rooms").document(roomId)
            .update("queue", FieldValue.arrayRemove(item)).await()
    }

    // Sıradaki bir öğeyi hemen oynat: video alanlarını ayarla + öğeyi sıradan çıkar
    suspend fun playFromQueue(roomId: String, item: QueueItem, actorUid: String) {
        db.collection("rooms").document(roomId).update(
            mapOf(
                "videoUrl" to item.url,
                "currentPositionMs" to 0L,
                "isPlaying" to true,
                "updatedAt" to serverNow(),
                "videoVersion" to serverNow(),
                "lastUpdatedBy" to actorUid,
                "queue" to FieldValue.arrayRemove(item)
            )
        ).await()
    }

    // Video bitince sıradakine geç (host çağırır). Sıra boşsa false döner.
    suspend fun advanceQueue(roomId: String, actorUid: String): Boolean {
        val room = db.collection("rooms").document(roomId).get().await()
            .toObject(Room::class.java) ?: return false
        val next = room.queue.firstOrNull() ?: return false
        playFromQueue(roomId, next, actorUid)
        return true
    }

    // ---- Senkron emoji tepkisi ----
    suspend fun sendReaction(roomId: String, emoji: String, by: String) {
        if (roomId.isBlank()) return
        db.collection("rooms").document(roomId).update(
            mapOf(
                "reaction" to emoji,
                "reactionAt" to serverNow(),
                "reactionBy" to by
            )
        ).await()
    }

    // ---- Yazıyor… ----
    suspend fun setTyping(roomId: String, uid: String) {
        if (roomId.isBlank() || uid.isBlank()) return
        db.collection("rooms").document(roomId)
            .update("typing.$uid", serverNow()).await()
    }

    // Tıklama-yansıtma: bir üyenin ana-sayfa tıklamasını odaya yayar.
    suspend fun sendClick(roomId: String, selector: String, actorUid: String) {
        db.collection("rooms").document(roomId).update(
            mapOf(
                "clickSel" to selector,
                "clickAt" to serverNow(),
                "clickBy" to actorUid
            )
        ).await()
    }

    // ---- Presence (odada canlı kullanıcılar) — oda dokümanında map alanları ----
    suspend fun setPresence(roomId: String, uid: String, name: String) {
        if (roomId.isBlank() || uid.isBlank()) return
        db.collection("rooms").document(roomId).update(
            mapOf(
                "presence.$uid" to serverNow(),
                "presenceNames.$uid" to name
            )
        ).await()
    }

    suspend fun clearPresence(roomId: String, uid: String) {
        if (roomId.isBlank() || uid.isBlank()) return
        db.collection("rooms").document(roomId).update(
            mapOf(
                "presence.$uid" to FieldValue.delete(),
                "presenceNames.$uid" to FieldValue.delete()
            )
        ).await()
    }

    suspend fun removeFriend(myUid: String, friendUid: String) {
        db.collection("users").document(myUid)
            .update("friendIds", FieldValue.arrayRemove(friendUid)).await()
        db.collection("users").document(friendUid)
            .update("friendIds", FieldValue.arrayRemove(myUid)).await()
    }

    suspend fun sendMessage(roomId: String, message: Message) {
        val ref = db.collection("rooms").document(roomId).collection("messages").document()
        db.collection("rooms").document(roomId).collection("messages")
            .document(ref.id).set(message.copy(id = ref.id)).await()
    }

    suspend fun deleteMessage(roomId: String, messageId: String) {
        if (roomId.isBlank() || messageId.isBlank()) return
        db.collection("rooms").document(roomId).collection("messages").document(messageId).delete().await()
    }

    // Sistem mesajı (örn. "X odadan ayrıldı")
    suspend fun sendSystemMessage(roomId: String, text: String) {
        if (roomId.isBlank() || text.isBlank()) return
        sendMessage(roomId, Message(senderUid = "system", text = text,timestamp = serverNow(), system = true))
    }

    // Kontrol isteği: yetkisi olmayan üye host/yetkililerden durdur/oynat rica eder.
    suspend fun toggleMessageReaction(roomId: String, messageId: String, uid: String, emoji: String) {
        if (roomId.isBlank() || messageId.isBlank() || uid.isBlank()) return
        val ref = db.collection("rooms").document(roomId).collection("messages").document(messageId)
        val snap = ref.get().await()
        val msg = snap.toObject(Message::class.java) ?: return
        val updated = msg.reactions.toMutableMap()
        if (updated[uid] == emoji) updated.remove(uid) else updated[uid] = emoji
        ref.update("reactions", updated).await()
    }

    suspend fun pinMessage(roomId: String, text: String, senderName: String) {
        if (roomId.isBlank()) return
        db.collection("rooms").document(roomId).update(
            mapOf("pinnedMessage" to text, "pinnedMessageSenderName" to senderName)
        ).await()
    }

    suspend fun unpinMessage(roomId: String) {
        if (roomId.isBlank()) return
        db.collection("rooms").document(roomId).update(
            mapOf("pinnedMessage" to "", "pinnedMessageSenderName" to "")
        ).await()
    }

    suspend fun requestControl(roomId: String, byUid: String, byName: String, action: String) {
        if (roomId.isBlank() || byUid.isBlank()) return
        db.collection("rooms").document(roomId).update(
            mapOf(
                "ctrlReqAt" to serverNow(),
                "ctrlReqBy" to byUid,
                "ctrlReqName" to byName,
                "ctrlReqAction" to action
            )
        ).await()
    }

    fun observeMessages(roomId: String): Flow<List<Message>> = callbackFlow {
        if (roomId.isBlank()) { trySend(emptyList()); close(); return@callbackFlow }
        val reg: ListenerRegistration = db.collection("rooms").document(roomId)
            .collection("messages")
            .orderBy("timestamp")
            // Son 200 mesajla sınırla: uzun ömürlü odalarda her değişimde tüm geçmişi
            // indirip parse etmeyi önler (performans + bellek).
            .limitToLast(200)
            .addSnapshotListener { snap, _ ->
                val messages = snap?.documents?.mapNotNull { it.toObject(Message::class.java) } ?: emptyList()
                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getUsersInRoom(memberUids: List<String>): List<User> {
        if (memberUids.isEmpty()) return emptyList()
        return db.collection("users").whereIn("uid", memberUids).get().await()
            .documents.mapNotNull { it.toObject(User::class.java) }
    }

    // Herkese açık odalar (Keşfet). Eşitlik filtresi → composite index gerekmez;
    // sıralama istemci tarafında yapılır.
    fun observePublicRooms(): Flow<List<Room>> = callbackFlow {
        val reg: ListenerRegistration = db.collection("rooms")
            .whereEqualTo("discoverable", true)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject(Room::class.java) }
                    ?.sortedByDescending { it.updatedAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeMyRooms(uid: String): Flow<List<Room>> = callbackFlow {
        val reg: ListenerRegistration = db.collection("rooms")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snap, _ ->
                val rooms = snap?.documents?.mapNotNull { it.toObject(Room::class.java) } ?: emptyList()
                trySend(rooms)
            }
        awaitClose { reg.remove() }
    }

    suspend fun searchUser(email: String): User? {
        val snap = db.collection("users").whereEqualTo("email", email).get().await()
        return snap.documents.firstOrNull()?.toObject(User::class.java)
    }

    // Discord tarzı ID (friendCode) ile kullanıcı ara.
    suspend fun searchUserByCode(code: String): User? {
        val snap = db.collection("users")
            .whereEqualTo("friendCode", code.trim().uppercase()).get().await()
        return snap.documents.firstOrNull()?.toObject(User::class.java)
    }

    suspend fun updateProfilePhoto(uid: String, photoBase64: String) {
        if (uid.isBlank()) return
        db.collection("users").document(uid).update("photoBase64", photoBase64).await()
    }

    suspend fun updateDisplayName(uid: String, name: String) {
        if (uid.isBlank() || name.isBlank()) return
        db.collection("users").document(uid).update("displayName", name.trim()).await()
    }

    // Eski hesaplarda friendCode yoksa üret ve kaydet; mevcut/yeni kodu döndür.
    suspend fun ensureFriendCode(uid: String): String {
        if (uid.isBlank()) return ""
        val user = getUser(uid) ?: return ""
        if (user.friendCode.isNotBlank()) return user.friendCode
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..6).map { chars.random() }.joinToString("")
        db.collection("users").document(uid).update("friendCode", code).await()
        return code
    }

    // --- İSTEK / DAVET SİSTEMİ ---

    /** Arkadaşlık isteği gönder. Belirleyici doc ID kullanır — index gerekmez, idempotent.
     *  Zaten arkadaşsa false döner. */
    suspend fun sendFriendRequest(fromUid: String, fromName: String, toUid: String): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val authUid = user.uid
        if (authUid.isBlank() || toUid.isBlank() || authUid == toUid) return false
        if (fromUid.isNotBlank() && fromUid != authUid) return false
        // Süresi dolmuş token → Firestore PERMISSION_DENIED verir
        try { user.getIdToken(true).await() } catch (_: Exception) { return false }
        val me = getUser(authUid)
        if (me != null && me.friendIds.contains(toUid)) return false
        val docId = "${toUid}_${authUid}_friend"
        val ref = db.collection("requests").document(docId)
        val payload = mapOf(
            "id" to docId,
            "fromUid" to authUid,
            "fromName" to fromName,
            "toUid" to toUid,
            "type" to "friend",
            "roomId" to "",
            "timestamp" to serverNow()
        )
        // Önce sil (delete artık auth gerekmeden izin veriliyor), sonra yeni oluştur
        try { ref.delete().await() } catch (_: Exception) {}
        ref.set(payload).await()
        return true
    }

    /** Odaya davet isteği gönder. Belirleyici doc ID (toUid_roomId) kullanır — index gerekmez,
     *  aynı daveti tekrar göndermek üzerine yazar (idempotent). */
    suspend fun sendRoomInvite(fromUid: String, fromName: String, toUid: String, roomId: String): Boolean {
        val authUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (authUid.isBlank() || toUid.isBlank() || roomId.isBlank()) return false
        if (fromUid.isNotBlank() && fromUid != authUid) return false
        val docId = "${toUid}_${roomId}"
        db.collection("requests").document(docId).set(
            Request(
                id = docId, fromUid = authUid, fromName = fromName,
                toUid = toUid, type = "room", roomId = roomId,
                timestamp = serverNow()
            )
        ).await()
        return true
    }

    /** Bana gelen tüm istekleri dinle. */
    fun observeRequests(uid: String): Flow<List<Request>> = callbackFlow {
        val reg: ListenerRegistration = db.collection("requests")
            .whereEqualTo("toUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // İzin hatası vb. -> çökmeyi önle, boş liste gönder
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(Request::class.java) } ?: emptyList()
                trySend(list.sortedByDescending { it.timestamp })
            }
        awaitClose { reg.remove() }
    }

    /** Benim GÖNDERDİĞİM bekleyen arkadaşlık isteklerinin alıcı uid'lerini canlı dinle. */
    fun observeOutgoingFriendRequests(fromUid: String): Flow<Set<String>> = callbackFlow {
        if (fromUid.isBlank()) { trySend(emptySet()); close(); return@callbackFlow }
        val reg: ListenerRegistration = db.collection("requests")
            .whereEqualTo("fromUid", fromUid)
            .whereEqualTo("type", "friend")
            .addSnapshotListener { snap, _ ->
                val ids = snap?.documents?.mapNotNull { it.getString("toUid") }?.toSet() ?: emptySet()
                trySend(ids)
            }
        awaitClose { reg.remove() }
    }

    /** İsteği kabul et: batch write ile atomik — yarıda kopma riski yok. */
    suspend fun acceptRequest(request: Request) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        try { user.getIdToken(true).await() } catch (_: Exception) { return }
        val batch = db.batch()
        when (request.type) {
            "friend" -> {
                batch.update(
                    db.collection("users").document(request.toUid),
                    "friendIds", FieldValue.arrayUnion(request.fromUid)
                )
                batch.update(
                    db.collection("users").document(request.fromUid),
                    "friendIds", FieldValue.arrayUnion(request.toUid)
                )
            }
            "room" -> {
                if (request.roomId.isBlank()) return
                batch.update(
                    db.collection("rooms").document(request.roomId),
                    "memberUids", FieldValue.arrayUnion(request.toUid)
                )
            }
        }
        batch.delete(db.collection("requests").document(request.id))
        batch.commit().await()

        // Sistem mesajı batch dışında (yeni doc oluşturuyor, batch gerektirmez)
        if (request.type == "room") {
            val name = getUser(request.toUid)?.displayName?.ifBlank {
                com.watch.watchtofriend.ui.locale.AppStrings.get(com.watch.watchtofriend.R.string.common_some_user)
            } ?: com.watch.watchtofriend.ui.locale.AppStrings.get(com.watch.watchtofriend.R.string.common_some_user)
            try {
                sendSystemMessage(
                    request.roomId,
                    com.watch.watchtofriend.ui.locale.AppStrings.get(
                        com.watch.watchtofriend.R.string.watch_user_joined,
                        name
                    )
                )
            } catch (_: Exception) {}
        }
    }

    /** İsteği reddet: sadece isteği sil. */
    suspend fun rejectRequest(requestId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        try { user.getIdToken(true).await() } catch (_: Exception) { return }
        db.collection("requests").document(requestId).delete().await()
    }

    suspend fun getUser(uid: String): User? {
        if (uid.isBlank()) return null
        return db.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    suspend fun saveFcmToken(uid: String, token: String) {
        if (uid.isBlank() || token.isBlank()) return
        try {
            db.collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .await()
        } catch (_: Exception) {}
    }

    // Çevrimiçi göstergesi: son aktiflik zamanını güncelle (uygulama açıkken periyodik).
    suspend fun touchActive(uid: String) {
        if (uid.isBlank()) return
        try {
            db.collection("users").document(uid)
                .set(mapOf("lastActive" to serverNow()), SetOptions.merge())
                .await()
        } catch (_: Exception) {}
    }

    fun observeFriends(myUid: String): Flow<List<User>> = callbackFlow {
        if (myUid.isBlank()) { trySend(emptyList()); close(); return@callbackFlow }
        // Arkadaş dokümanlarını CANLI dinle (lastActive/çevrimiçi anlık güncellensin).
        val friendRegs = mutableListOf<ListenerRegistration>()
        val chunkResults = HashMap<Int, List<User>>()
        var lastIds: List<String>? = null
        val myReg: ListenerRegistration = db.collection("users").document(myUid)
            .addSnapshotListener { snap, _ ->
                val ids = snap?.toObject(User::class.java)?.friendIds ?: emptyList()
                // Arkadaş LİSTESİ değişmediyse mevcut dinleyiciler geçerli — yeniden kurma.
                // (Kendi dokümanımdaki lastActive/clockProbe değişimlerinde gereksiz yeniden
                //  kurulmayı önler.)
                if (ids == lastIds) return@addSnapshotListener
                lastIds = ids
                // Eski arkadaş dinleyicilerini kapat
                friendRegs.forEach { it.remove() }
                friendRegs.clear()
                chunkResults.clear()
                if (ids.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                // whereIn en fazla 10 değer → 10'luk parçalara böl, her parçayı canlı dinle
                ids.chunked(10).forEachIndexed { idx, chunk ->
                    val r = db.collection("users").whereIn("uid", chunk)
                        .addSnapshotListener { s, _ ->
                            chunkResults[idx] = s?.documents?.mapNotNull { it.toObject(User::class.java) } ?: emptyList()
                            trySend(chunkResults.values.flatten())
                        }
                    friendRegs.add(r)
                }
            }
        awaitClose {
            myReg.remove()
            friendRegs.forEach { it.remove() }
        }
    }

    suspend fun deleteRoom(roomId: String) {
        // Firestore batch limiti 500 doc — mesajları 499'luk parçalarda sil, en sonda oda dok'unu da ekle.
        val messages = db.collection("rooms").document(roomId)
            .collection("messages").get().await()
        val roomRef = db.collection("rooms").document(roomId)
        val allRefs = messages.documents.map { it.reference }
        // 499'luk parçalara böl (son parçada roomRef'i de eklemek için 1 yer bırak)
        val chunks = allRefs.chunked(499)
        if (chunks.isEmpty()) {
            // Mesaj yoksa tek batch ile oda dokümanını sil
            db.batch().also { it.delete(roomRef) }.commit().await()
        } else {
            chunks.forEachIndexed { index, chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                if (index == chunks.lastIndex) batch.delete(roomRef)
                batch.commit().await()
            }
        }
    }

    suspend fun leaveRoom(roomId: String, uid: String) {
        db.collection("rooms").document(roomId)
            .update("memberUids", FieldValue.arrayRemove(uid)).await()
    }

    // ---- Doğrudan Mesajlaşma (DM) ----

    private fun dmId(uid1: String, uid2: String): String =
        listOf(uid1, uid2).sorted().joinToString("_")

    /** dmId = sorted(uid1, uid2).join("_") — karşı tarafın uid'sini döndürür. */
    private fun otherUidFromDmId(dmId: String, myUid: String): String? =
        dmId.split("_").firstOrNull { it.isNotBlank() && it != myUid }

    private fun dmMessagePayload(message: Message, docId: String): HashMap<String, Any> {
        val data = hashMapOf<String, Any>(
            "id" to docId,
            "senderUid" to message.senderUid,
            "senderName" to message.senderName,
            "senderPhoto" to message.senderPhoto,
            "text" to message.text,
            "timestamp" to message.timestamp,
            "system" to message.system,
            "reactions" to message.reactions,
            "roomId" to message.roomId
        )
        message.color?.let { data["color"] = it }
        return data
    }

    /** DM belgesi yoksa veya eksik alanları varsa gönderimden önce onarır (Firestore kuralları için). */
    suspend fun ensureDmDocumentForSend(dmId: String, senderUid: String) {
        if (dmId.isBlank() || senderUid.isBlank()) {
            throw IllegalStateException("DM veya oturum geçersiz")
        }
        val ref = db.collection("dms").document(dmId)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val otherUid = otherUidFromDmId(dmId, senderUid)
                ?: throw IllegalStateException("Geçersiz sohbet kimliği")
            val me = getUser(senderUid)
            val other = getUser(otherUid)
            val conv = DmConversation(
                id = dmId,
                participantUids = listOf(senderUid, otherUid).sorted(),
                participantNames = mapOf(
                    senderUid to (me?.displayName.orEmpty()),
                    otherUid to (other?.displayName.orEmpty())
                ),
                participantPhotos = mapOf(
                    senderUid to (me?.photoBase64.orEmpty()),
                    otherUid to (other?.photoBase64.orEmpty())
                ),
                lastMessageAt = serverNow(),
                unreadCount = mapOf(senderUid to 0L, otherUid to 0L)
            )
            ref.set(conv).await()
            return
        }
        @Suppress("UNCHECKED_CAST")
        val participants = (snap.get("participantUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (senderUid !in participants) {
            ref.update("participantUids", FieldValue.arrayUnion(senderUid)).await()
        }
        if (snap.get("unreadCount") == null) {
            val otherUid = otherUidFromDmId(dmId, senderUid)
            val counts = mutableMapOf(senderUid to 0L)
            if (otherUid != null) counts[otherUid] = 0L
            ref.set(mapOf("unreadCount" to counts), SetOptions.merge()).await()
        }
    }

    suspend fun getOrCreateDm(myUid: String, otherUid: String, myName: String, otherName: String,
                               myPhoto: String = "", otherPhoto: String = ""): String {
        val id = dmId(myUid, otherUid)
        val ref = db.collection("dms").document(id)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val conv = DmConversation(
                id = id,
                participantUids = listOf(myUid, otherUid).sorted(),
                participantNames = mapOf(myUid to myName, otherUid to otherName),
                participantPhotos = mapOf(myUid to myPhoto, otherUid to otherPhoto),
                lastMessageAt = serverNow(),
                unreadCount = mapOf(myUid to 0L, otherUid to 0L)
            )
            ref.set(conv).await()
        } else if (snap.getString("id").isNullOrBlank()) {
            ref.update("id", id).await()
        }
        return id
    }

    fun observeDms(uid: String): Flow<List<DmConversation>> = callbackFlow {
        if (uid.isBlank()) { trySend(emptyList()); close(); return@callbackFlow }
        val reg: ListenerRegistration = db.collection("dms")
            .whereArrayContains("participantUids", uid)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(DmConversation::class.java)?.let { conv ->
                        conv.copy(id = doc.id.ifBlank { conv.id })
                    }
                }?.sortedByDescending { it.lastMessageAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeDmMessages(dmId: String): Flow<List<Message>> = callbackFlow {
        if (dmId.isBlank()) { trySend(emptyList()); close(); return@callbackFlow }
        val reg: ListenerRegistration = db.collection("dms").document(dmId)
            .collection("messages")
            .orderBy("timestamp")
            .limitToLast(200)
            .addSnapshotListener { snap, _ ->
                val msgs = snap?.documents?.mapNotNull { it.toObject(Message::class.java) } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendDmMessage(dmId: String, message: Message) {
        if (dmId.isBlank() || message.senderUid.isBlank()) {
            throw IllegalStateException("Mesaj gönderilemedi: oturum veya sohbet geçersiz")
        }
        ensureDmDocumentForSend(dmId, message.senderUid)

        val msgRef = db.collection("dms").document(dmId).collection("messages").document()
        val dmRef = db.collection("dms").document(dmId)
        val otherUid = otherUidFromDmId(dmId, message.senderUid)
        val batch = db.batch()
        batch.set(msgRef, dmMessagePayload(message, msgRef.id))
        val meta = mutableMapOf<String, Any>(
            "lastMessage" to message.text,
            "lastMessageAt" to message.timestamp,
            "lastSenderUid" to message.senderUid
        )
        if (otherUid != null) meta["unreadCount.$otherUid"] = FieldValue.increment(1)
        // update() belge yoksa başarısız olur; merge set her iki durumda da güvenli.
        batch.set(dmRef, meta, SetOptions.merge())
        batch.commit().await()
    }

    suspend fun deleteDmMessage(dmId: String, messageId: String) {
        if (dmId.isBlank() || messageId.isBlank()) return
        db.collection("dms").document(dmId).collection("messages").document(messageId).delete().await()
    }

    suspend fun clearDmUnread(dmId: String, uid: String) {
        if (dmId.isBlank() || uid.isBlank()) return
        try {
            db.collection("dms").document(dmId).update("unreadCount.$uid", 0).await()
        } catch (_: Exception) {}
    }

    suspend fun toggleDmMessageReaction(dmId: String, messageId: String, uid: String, emoji: String) {
        val ref = db.collection("dms").document(dmId).collection("messages").document(messageId)
        val snap = ref.get().await()
        @Suppress("UNCHECKED_CAST")
        val reactions = (snap.get("reactions") as? Map<String, String>)
            ?.toMutableMap() ?: mutableMapOf()
        if (reactions[uid] == emoji) reactions.remove(uid) else reactions[uid] = emoji
        ref.update("reactions", reactions).await()
    }

    // ---- İzleme Geçmişi ----
    suspend fun addToHistory(uid: String, entry: WatchHistory) {
        if (uid.isBlank()) return
        val ref = db.collection("users").document(uid).collection("history").document()
        ref.set(entry.copy(id = ref.id)).await()
    }

    suspend fun deleteHistory(uid: String, historyId: String) {
        db.collection("users").document(uid).collection("history").document(historyId).delete().await()
    }

    suspend fun clearAllHistory(uid: String) {
        // Firestore batch limiti 500 doc — chunked ile her partisyon ayrı commit edilir.
        val docs = db.collection("users").document(uid).collection("history").get().await()
        for (chunk in docs.documents.chunked(500)) {
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    fun observeHistory(uid: String): Flow<List<WatchHistory>> = callbackFlow {
        if (uid.isBlank()) { trySend(emptyList()); close(); return@callbackFlow }
        val reg: ListenerRegistration = db.collection("users").document(uid).collection("history")
            .orderBy("watchedAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject(WatchHistory::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
