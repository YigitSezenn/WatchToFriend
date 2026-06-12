package com.watch.watchtofriend.ui.locale

import android.content.Context
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import java.util.Locale

object LocalePrefs {
    private const val PREFS = "wtf_prefs"
    private const val KEY = "app_locale"

    /** "", "tr", or "en" — empty = follow system language */
    fun getTag(context: Context): String {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return when (raw) {
            "system" -> ""
            "tr", "en" -> raw
            else -> ""
        }
    }

    fun setTag(context: Context, tag: String) {
        val normalized = when (tag) {
            "system" -> ""
            else -> tag
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, normalized).apply()
    }

    fun resolveLocale(context: Context): Locale {
        return when (getTag(context)) {
            "tr" -> Locale.forLanguageTag("tr")
            "en" -> Locale.forLanguageTag("en")
            else -> deviceLocale()
        }
    }

    /** Cihazın gerçek sistem dili — Locale.setDefault() ile kirlenmez */
    private fun deviceLocale(): Locale {
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        val lang = locales.get(0)?.language?.lowercase() ?: "en"
        return if (lang == "tr") Locale.forLanguageTag("tr") else Locale.forLanguageTag("en")
    }
}
