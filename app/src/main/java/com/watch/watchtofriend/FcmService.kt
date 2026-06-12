package com.watch.watchtofriend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.watch.watchtofriend.data.FirebaseBootstrap
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.watch.watchtofriend.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FcmService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        serviceScope.launch {
            try {
                FirebaseBootstrap.configureFirestore()
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("fcmToken", token).await()
            } catch (_: Exception) {}
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title ?: return
        val body = message.data["body"] ?: message.notification?.body ?: return
        val type = message.data["type"]
        val roomId = message.data["roomId"]
        val dmId = message.data["dmId"]
        val dmName = message.data["dmName"]
        NotificationHelper.showGeneral(
            context = this,
            title = title,
            body = body,
            type = type,
            roomId = roomId,
            dmId = dmId,
            dmName = dmName
        )
    }

    companion object {
        /** @deprecated Eski kanal kimliği; yeni kod [NotificationHelper] kanallarını kullanır. */
        @Deprecated("NotificationHelper.CHANNEL_GENERAL kullanın")
        const val CHANNEL_ID = NotificationHelper.CHANNEL_GENERAL

        fun refreshToken() {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    FirebaseBootstrap.configureFirestore()
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                    val token = FirebaseMessaging.getInstance().token.await()
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .set(
                            mapOf("fcmToken" to token),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                } catch (_: Exception) {}
            }
        }
    }
}
