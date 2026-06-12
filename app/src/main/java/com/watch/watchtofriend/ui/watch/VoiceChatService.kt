package com.watch.watchtofriend.ui.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.watch.watchtofriend.MainActivity
import com.watch.watchtofriend.R

/** Sesli sohbet sırasında arka planda mikrofonun kapanmasını önler. */
class VoiceChatService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sesli Sohbet", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ses kanalına bağlıyken gösterilir"
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(
            NOTIF_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Sesli sohbet aktif")
                .setContentText(roomLabel.ifBlank { "WatchToFriend odası" })
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_ROOM_LABEL)
        if (!label.isNullOrBlank()) roomLabel = label
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val CHANNEL_ID = "voice_chat"
        const val EXTRA_ROOM_LABEL = "room_label"
        private const val NOTIF_ID = 102
        @Volatile var roomLabel: String = ""
    }
}
