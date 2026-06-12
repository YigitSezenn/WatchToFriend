package com.watch.watchtofriend.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableIntStateOf

/**
 * Kalıcı tema tercihi (SharedPreferences) + Compose-uyumlu global durum.
 * 0 = Sistem, 1 = Açık, 2 = Koyu. Varsayılan koyu (mevcut görünümü korur).
 */
object ThemePref {
    private const val PREFS = "wtf_prefs"
    private const val KEY = "theme_mode"

    val mode = mutableIntStateOf(2)

    fun load(context: Context) {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 2)
        mode.intValue = value
        applyNightMode(context, value)
    }

    fun set(context: Context, value: Int) {
        mode.intValue = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, value).apply()
        applyNightMode(context, value)
    }

    private fun applyNightMode(context: Context, value: Int) {
        val nightMode = when (value) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.applicationContext.getSystemService(UiModeManager::class.java)
                ?.setApplicationNightMode(
                    when (value) {
                        1 -> UiModeManager.MODE_NIGHT_NO
                        2 -> UiModeManager.MODE_NIGHT_YES
                        else -> UiModeManager.MODE_NIGHT_AUTO
                    }
                )
        }
    }
}
