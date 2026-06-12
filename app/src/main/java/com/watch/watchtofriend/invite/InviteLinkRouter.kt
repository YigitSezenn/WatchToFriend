package com.watch.watchtofriend.invite

import android.content.Intent

/** Davet linkinden gelen oda kodunu oturum açılana kadar tutar. */
object InviteLinkRouter {

    @Volatile
    private var pendingCode: String? = null

    fun consumeFromIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        val action = intent.action
        if (action != null && action != Intent.ACTION_VIEW) return
        InviteLink.parseCode(data)?.let { pendingCode = it }
    }

    fun takePendingCode(): String? {
        val code = pendingCode
        pendingCode = null
        return code
    }
}
