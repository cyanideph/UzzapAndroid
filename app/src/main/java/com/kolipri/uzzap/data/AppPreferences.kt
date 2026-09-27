package com.kolipri.uzzap.data

import android.content.Context

/** Device-level preferences shared by settings and background services. */
object AppPreferences {
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun isBatterySavingEnabled(context: Context): Boolean =
        context.getSharedPreferences("uzzap_prefs", Context.MODE_PRIVATE).getBoolean("battery_saving", false)

    fun isNotificationSoundEnabled(context: Context): Boolean =
        context.getSharedPreferences("uzzap_prefs", Context.MODE_PRIVATE).getBoolean("tone_sound", true)

    fun isVibrationEnabled(context: Context): Boolean =
        context.getSharedPreferences("uzzap_prefs", Context.MODE_PRIVATE).getBoolean("tone_vibrate", true)
}
