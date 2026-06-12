package com.watch.watchtofriend.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.watch.watchtofriend.MainActivity
import com.watch.watchtofriend.R

object NotificationHelper {

    const val CHANNEL_FRIEND = "wtf_friend"
    const val CHANNEL_DM = "wtf_dm"
    const val CHANNEL_ROOM = "wtf_room"
    const val CHANNEL_GENERAL = "wtf_general"

    const val EXTRA_TYPE = "notif_type"
    const val EXTRA_ROOM_ID = "notif_roomId"
    const val EXTRA_DM_ID = "notif_dmId"
    const val EXTRA_DM_NAME = "notif_dmName"
    const val EXTRA_HOME_TAB = "notif_homeTab"

    const val TYPE_FRIEND = "friend"
    const val TYPE_ROOM_INVITE = "room_invite"
    const val TYPE_DM = "dm"
    const val TYPE_ROOM_MESSAGE = "room_message"
    const val TYPE_GENERAL = "general"

    @Volatile var isAppInForeground = false
    @Volatile var activeDmId: String? = null
    @Volatile var activeRoomId: String? = null

    private const val GROUP_DM = "wtf_dm_group"
    private const val ID_FRIEND = 100
    private const val ID_DM_SUMMARY = 101
    private const val ID_ROOM_BASE = 3000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(CHANNEL_FRIEND, context.getString(R.string.notif_channel_friend), context.getString(R.string.notif_channel_friend_desc)),
            Triple(CHANNEL_DM, context.getString(R.string.notif_channel_dm), context.getString(R.string.notif_channel_dm_desc)),
            Triple(CHANNEL_ROOM, context.getString(R.string.notif_channel_room), context.getString(R.string.notif_channel_room_desc)),
            Triple(CHANNEL_GENERAL, context.getString(R.string.notif_channel_general), context.getString(R.string.notif_channel_general))
        ).forEach { (id, name, desc) ->
            nm.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = desc
                    enableVibration(true)
                }
            )
        }
    }

    fun showFriendRequest(context: Context, fromName: String) {
        show(
            context = context,
            channelId = CHANNEL_FRIEND,
            notificationId = ID_FRIEND,
            title = context.getString(R.string.notif_friend_request),
            body = context.getString(R.string.notif_friend_request_body, fromName),
            type = TYPE_FRIEND,
            homeTab = 1
        )
    }

    fun showRoomInvite(context: Context, fromName: String, roomId: String) {
        show(
            context = context,
            channelId = CHANNEL_FRIEND,
            notificationId = ID_FRIEND + 1,
            title = context.getString(R.string.notif_room_invite),
            body = context.getString(R.string.notif_room_invite_body, fromName),
            type = TYPE_ROOM_INVITE,
            roomId = roomId.takeIf { it.isNotBlank() },
            homeTab = if (roomId.isBlank()) 1 else null
        )
    }

    fun showDm(context: Context, dmId: String, senderName: String, preview: String) {
        if (shouldSuppressDm(dmId)) return
        show(
            context = context,
            channelId = CHANNEL_DM,
            notificationId = dmNotificationId(dmId),
            title = senderName,
            body = preview.ifBlank { context.getString(R.string.notif_new_message) },
            type = TYPE_DM,
            dmId = dmId,
            dmName = senderName,
            groupKey = GROUP_DM
        )
        showDmSummary(context)
    }

    fun showRoomMessage(
        context: Context,
        roomId: String,
        roomTitle: String,
        senderName: String,
        preview: String
    ) {
        if (shouldSuppressRoom(roomId)) return
        show(
            context = context,
            channelId = CHANNEL_ROOM,
            notificationId = ID_ROOM_BASE + roomId.hashCode().and(0xFFFF),
            title = roomTitle.ifBlank { context.getString(R.string.notif_room_default) },
            body = "$senderName: ${preview.take(120)}",
            type = TYPE_ROOM_MESSAGE,
            roomId = roomId
        )
    }

    fun showGeneral(
        context: Context,
        title: String,
        body: String,
        type: String? = null,
        roomId: String? = null,
        dmId: String? = null,
        dmName: String? = null
    ) {
        show(
            context = context,
            channelId = CHANNEL_GENERAL,
            notificationId = (title + body).hashCode(),
            title = title,
            body = body,
            type = type ?: TYPE_GENERAL,
            roomId = roomId,
            dmId = dmId,
            dmName = dmName
        )
    }

    private fun shouldSuppressDm(dmId: String): Boolean =
        isAppInForeground && activeDmId == dmId

    private fun shouldSuppressRoom(roomId: String): Boolean =
        isAppInForeground && activeRoomId == roomId

    private fun showDmSummary(context: Context) {
        if (!canNotify(context)) return
        val intent = buildIntent(context, TYPE_DM, homeTab = 3)
        val summary = NotificationCompat.Builder(context, CHANNEL_DM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_new_messages))
            .setContentText(context.getString(R.string.notif_tap_messages))
            .setGroup(GROUP_DM)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent(context, ID_DM_SUMMARY, intent))
            .build()
        NotificationManagerCompat.from(context).notify(ID_DM_SUMMARY, summary)
    }

    private fun show(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        type: String,
        roomId: String? = null,
        dmId: String? = null,
        dmName: String? = null,
        homeTab: Int? = null,
        groupKey: String? = null
    ) {
        if (!canNotify(context)) return
        val intent = buildIntent(context, type, roomId, dmId, dmName, homeTab)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent(context, notificationId, intent))
        if (groupKey != null) {
            builder.setGroup(groupKey)
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun buildIntent(
        context: Context,
        type: String,
        roomId: String? = null,
        dmId: String? = null,
        dmName: String? = null,
        homeTab: Int? = null
    ): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_TYPE, type)
        roomId?.let { putExtra(EXTRA_ROOM_ID, it) }
        dmId?.let { putExtra(EXTRA_DM_ID, it) }
        dmName?.let { putExtra(EXTRA_DM_NAME, it) }
        homeTab?.let { putExtra(EXTRA_HOME_TAB, it) }
    }

    private fun pendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun canNotify(context: Context): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun dmNotificationId(dmId: String): Int = 2000 + dmId.hashCode().and(0x7FFF)
}

data class PendingNavigation(
    val type: String,
    val roomId: String? = null,
    val dmId: String? = null,
    val dmName: String? = null,
    val homeTab: Int? = null
)

object NotificationRouter {
    @Volatile var pending: PendingNavigation? = null
    @Volatile private var pendingHomeTab: Int? = null

    fun consumeFromIntent(intent: Intent?) {
        val type = intent?.getStringExtra(NotificationHelper.EXTRA_TYPE)?.takeIf { it.isNotBlank() }
            ?: return
        pending = PendingNavigation(
            type = type,
            roomId = intent.getStringExtra(NotificationHelper.EXTRA_ROOM_ID),
            dmId = intent.getStringExtra(NotificationHelper.EXTRA_DM_ID),
            dmName = intent.getStringExtra(NotificationHelper.EXTRA_DM_NAME),
            homeTab = intent.getIntExtra(NotificationHelper.EXTRA_HOME_TAB, -1).takeIf { it >= 0 }
        )
        pending?.homeTab?.let { pendingHomeTab = it }
        intent.removeExtra(NotificationHelper.EXTRA_TYPE)
        intent.removeExtra(NotificationHelper.EXTRA_ROOM_ID)
        intent.removeExtra(NotificationHelper.EXTRA_DM_ID)
        intent.removeExtra(NotificationHelper.EXTRA_DM_NAME)
        intent.removeExtra(NotificationHelper.EXTRA_HOME_TAB)
    }

    fun consume(): PendingNavigation? {
        val nav = pending
        pending = null
        return nav
    }

    fun consumeHomeTab(): Int? {
        val tab = pendingHomeTab
        pendingHomeTab = null
        return tab
    }
}
