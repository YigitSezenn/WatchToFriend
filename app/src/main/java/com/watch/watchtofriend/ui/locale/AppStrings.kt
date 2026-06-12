package com.watch.watchtofriend.ui.locale

import android.content.Context
import androidx.annotation.StringRes
import com.watch.watchtofriend.WatchApplication

object AppStrings {
    private val ctx: Context
        get() = WatchApplication.instance

    fun get(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
}
