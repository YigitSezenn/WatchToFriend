package com.watch.watchtofriend.ui.watch

import android.content.Context

/** Sesli sohbet ayarları — Desktop localStorage ile aynı anahtarlar. */
object VoicePrefs {
    private const val PREFS = "wtf_voice_prefs"
    private const val KEY_MIC_GAIN = "wtf_mic_gain"
    private const val KEY_SPEAK_RMS = "wtf_speak_rms"
    private const val KEY_PUSH_TO_TALK = "wtf_push_to_talk"
    private const val KEY_MIC_BOOST_V2 = "wtf_mic_boost_v2"

    const val MIC_GAIN_MAX = 3f
    const val MIC_GAIN_DEFAULT = 1.8f
    const val SPEAK_RMS_DEFAULT = 4f

    fun loadMicGain(context: Context, default: Float = MIC_GAIN_DEFAULT): Float {
        migrateMicBoostIfNeeded(context)
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_MIC_GAIN, default)
        return v.coerceIn(0f, MIC_GAIN_MAX)
    }

    fun loadSpeakRmsUi(context: Context, default: Float = SPEAK_RMS_DEFAULT): Float {
        migrateMicBoostIfNeeded(context)
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_SPEAK_RMS, default)
        return v.coerceIn(2f, 25f)
    }

    private fun migrateMicBoostIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIC_BOOST_V2, false)) return
        val editor = prefs.edit()
        val gain = prefs.getFloat(KEY_MIC_GAIN, MIC_GAIN_DEFAULT)
        if (gain <= 1.05f) editor.putFloat(KEY_MIC_GAIN, MIC_GAIN_DEFAULT)
        val rms = prefs.getFloat(KEY_SPEAK_RMS, SPEAK_RMS_DEFAULT)
        if (rms > 8f) editor.putFloat(KEY_SPEAK_RMS, SPEAK_RMS_DEFAULT)
        editor.putBoolean(KEY_MIC_BOOST_V2, true).apply()
    }

    fun saveMicGain(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_MIC_GAIN, value.coerceIn(0f, MIC_GAIN_MAX))
            .apply()
    }

    fun saveSpeakRmsUi(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SPEAK_RMS, value.coerceIn(2f, 25f))
            .apply()
    }

    fun loadPushToTalk(context: Context, default: Boolean = false): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PUSH_TO_TALK, default)

    fun savePushToTalk(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PUSH_TO_TALK, enabled)
            .apply()
    }
}
