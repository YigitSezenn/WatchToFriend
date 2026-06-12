package com.watch.watchtofriend.ui.watch

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.watch.watchtofriend.R

class ScreenShareService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_screen_share_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        startForeground(
            NOTIF_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_screen_share_active))
                .setContentText(getString(R.string.notif_screen_share))
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "wtf_screen_share"
        private const val NOTIF_ID = 42
    }
}
