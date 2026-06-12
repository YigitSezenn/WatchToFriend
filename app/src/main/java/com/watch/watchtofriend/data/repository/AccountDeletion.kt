package com.watch.watchtofriend.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object AccountDeletion {

    suspend fun purgeUserData(uid: String, friendIds: List<String>) {
        if (uid.isBlank()) return
        val db = FirebaseFirestore.getInstance()
        val repo = RoomRepository()

        friendIds.forEach { fid ->
            runCatching {
                db.collection("users").document(fid)
                    .update("friendIds", FieldValue.arrayRemove(uid)).await()
            }
        }

        val dms = db.collection("dms").whereArrayContains("participantUids", uid).get().await()
        for (doc in dms.documents) {
            deleteDmDocument(db, doc.id)
        }

        val reqFrom = db.collection("requests").whereEqualTo("fromUid", uid).get().await()
        val reqTo = db.collection("requests").whereEqualTo("toUid", uid).get().await()
        val seenReq = mutableSetOf<String>()
        (reqFrom.documents + reqTo.documents).filter { seenReq.add(it.id) }.chunked(499).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        val rooms = db.collection("rooms").whereArrayContains("memberUids", uid).get().await()
        for (doc in rooms.documents) {
            handleRoom(db, repo, doc, uid)
        }

        val history = db.collection("users").document(uid).collection("history").get().await()
        history.documents.chunked(499).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        db.collection("users").document(uid).delete().await()
    }

    private suspend fun deleteDmDocument(db: FirebaseFirestore, dmId: String) {
        val msgs = db.collection("dms").document(dmId).collection("messages").get().await()
        msgs.documents.chunked(499).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        db.collection("dms").document(dmId).delete().await()
    }

    private suspend fun handleRoom(
        db: FirebaseFirestore,
        repo: RoomRepository,
        doc: DocumentSnapshot,
        uid: String
    ) {
        val roomId = doc.id
        val hostUid = doc.getString("hostUid").orEmpty()
        @Suppress("UNCHECKED_CAST")
        val members = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (hostUid == uid) {
            val others = members.filter { it != uid }
            if (others.isEmpty()) {
                runCatching { repo.deleteRoom(roomId) }
            } else {
                db.collection("rooms").document(roomId).update(
                    mapOf(
                        "hostUid" to others.first(),
                        "memberUids" to FieldValue.arrayRemove(uid),
                        "moderators" to FieldValue.arrayRemove(uid),
                        "presence.$uid" to FieldValue.delete(),
                        "presenceNames.$uid" to FieldValue.delete()
                    )
                ).await()
            }
        } else {
            db.collection("rooms").document(roomId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "moderators" to FieldValue.arrayRemove(uid),
                    "presence.$uid" to FieldValue.delete(),
                    "presenceNames.$uid" to FieldValue.delete()
                )
            ).await()
        }
    }
}
