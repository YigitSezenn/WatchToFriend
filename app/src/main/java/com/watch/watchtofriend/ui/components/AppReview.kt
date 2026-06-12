package com.watch.watchtofriend.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

object RatingPrefs {
    private const val PREFS = "wtf_prefs"
    private const val KEY_SESSIONS = "rating_room_sessions"
    private const val KEY_PROMPTED = "rating_prompted"

    fun recordRoomSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PROMPTED, false)) return
        val n = prefs.getInt(KEY_SESSIONS, 0) + 1
        prefs.edit().putInt(KEY_SESSIONS, n).apply()
    }

    fun shouldAutoPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PROMPTED, false)) return false
        return prefs.getInt(KEY_SESSIONS, 0) >= 3
    }

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PROMPTED, true).apply()
    }
}

object AppReview {
    private const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.watch.watchtofriend"

    fun requestReview(activity: Activity, onDone: (Boolean) -> Unit = {}) {
        val manager = ReviewManagerFactory.create(activity)
        val flow = manager.requestReviewFlow()
        flow.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                openStoreListing(activity)
                onDone(false)
                return@addOnCompleteListener
            }
            manager.launchReviewFlow(activity, task.result)
                .addOnCompleteListener {
                    RatingPrefs.markPrompted(activity)
                    onDone(true)
                }
        }
    }

    fun openStoreListing(context: Context) {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (market.resolveActivity(context.packageManager) != null) {
            context.startActivity(market)
        } else {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE))
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(web)
        }
        RatingPrefs.markPrompted(context)
    }
}
