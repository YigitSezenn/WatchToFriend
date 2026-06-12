package com.watch.watchtofriend

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.watch.watchtofriend.notifications.NotificationHelper
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

// Arka planda 15 dakikada bir çalışır; yeni DM, istek ve oda mesajlarını kontrol eder.
class NotificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()
        val prefs = applicationContext.getSharedPreferences("notif_prefs", Context.MODE_PRIVATE)

        // ---- Yeni arkadaşlık/oda istekleri ----
        val lastReqCheck = prefs.getLong("last_req_check", System.currentTimeMillis())
        val seenReqIds = prefs.getStringSet("seen_req_ids", emptySet())!!.toMutableSet()
        try {
            val reqs = db.collection("requests")
                .whereEqualTo("toUid", uid)
                .get().await()
            reqs.documents.forEach { doc ->
                if (doc.id in seenReqIds) return@forEach
                val ts = doc.getLong("timestamp") ?: 0L
                if (ts <= lastReqCheck) return@forEach
                seenReqIds.add(doc.id)
                val type = doc.getString("type") ?: return@forEach
                val fromName = doc.getString("fromName") ?: "Biri"
                val roomId = doc.getString("roomId") ?: ""
                when (type) {
                    "friend" -> NotificationHelper.showFriendRequest(applicationContext, fromName)
                    "room" -> NotificationHelper.showRoomInvite(applicationContext, fromName, roomId)
                }
            }
            if (seenReqIds.size > 200) {
                val toRemove = seenReqIds.take(seenReqIds.size - 200)
                seenReqIds.removeAll(toRemove.toSet())
            }
        } catch (_: Exception) {}
        prefs.edit()
            .putLong("last_req_check", System.currentTimeMillis())
            .putStringSet("seen_req_ids", seenReqIds)
            .apply()

        // ---- Yeni DM mesajları (tekrar bildirim önlenir) ----
        try {
            val dms = db.collection("dms")
                .whereArrayContains("participantUids", uid)
                .get().await()
            dms.documents.forEach { convDoc ->
                val dmId = convDoc.id
                val unreadMap = convDoc.get("unreadCount") as? Map<*, *>
                val unread = (unreadMap?.get(uid) as? Long) ?: 0L
                val lastNotified = prefs.getLong("dm_unread_$dmId", 0L)
                when {
                    unread > lastNotified -> {
                        val names = convDoc.get("participantNames") as? Map<*, *>
                        val otherUid = (convDoc.get("participantUids") as? List<*>)
                            ?.firstOrNull { it != uid }?.toString() ?: ""
                        val senderName = names?.get(otherUid)?.toString() ?: "Biri"
                        val preview = convDoc.getString("lastMessage") ?: ""
                        NotificationHelper.showDm(applicationContext, dmId, senderName, preview)
                        prefs.edit().putLong("dm_unread_$dmId", unread).apply()
                    }
                    unread < lastNotified -> {
                        prefs.edit().putLong("dm_unread_$dmId", unread).apply()
                    }
                }
            }
        } catch (_: Exception) {}

        // ---- Oda sohbet mesajları ----
        try {
            val rooms = db.collection("rooms")
                .whereArrayContains("memberUids", uid)
                .get().await()
            rooms.documents.forEach { roomDoc ->
                val roomId = roomDoc.id
                val roomTitle = roomDoc.getString("title") ?: "Oda"
                val lastSeenTs = prefs.getLong("room_msg_$roomId", 0L)
                val msgs = db.collection("rooms").document(roomId).collection("messages")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get().await()
                val latest = msgs.documents.firstOrNull() ?: return@forEach
                val ts = latest.getLong("timestamp") ?: 0L
                val senderUid = latest.getString("senderUid") ?: ""
                val system = latest.getBoolean("system") ?: false
                if (system || senderUid == uid || ts <= lastSeenTs) return@forEach
                val senderName = latest.getString("senderName") ?: "Biri"
                val text = latest.getString("text") ?: ""
                NotificationHelper.showRoomMessage(
                    applicationContext, roomId, roomTitle, senderName, text
                )
                prefs.edit().putLong("room_msg_$roomId", ts).apply()
            }
        } catch (_: Exception) {}

        return Result.success()
    }

    companion object {
        private const val WORK_TAG = "wtf_bg_check"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
