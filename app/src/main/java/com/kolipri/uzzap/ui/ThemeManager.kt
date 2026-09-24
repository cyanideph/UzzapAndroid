package com.kolipri.uzzap.ui

import android.app.Activity
import android.content.Context
import androidx.annotation.AttrRes
import com.kolipri.uzzap.R

/** Runtime theme selection for the Uzzap visual system. Claymorphism v3 is the default/final baseline. */
object ThemeManager {
    private const val PREFS = "uzzap_prefs"
    private const val KEY_THEME = "theme"
    private const val DEFAULT_THEME = "clay"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, DEFAULT_THEME)
            ?.lowercase()
            ?: DEFAULT_THEME

    fun setTheme(context: Context, name: String) {
        val normalized = name.lowercase()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, normalized)
            .apply()
    }

    fun themeRes(name: String): Int = when (name.lowercase()) {
        "clay", "v3", "claymorphism" -> R.style.UzzapTheme_Clay
        "dark" -> R.style.UzzapTheme_Dark
        "black" -> R.style.UzzapTheme_Black
        "blue" -> R.style.UzzapTheme_Blue
        "green" -> R.style.UzzapTheme_Green
        // Legacy preference value: migrate its visual meaning to the final v3 baseline.
        "classic" -> R.style.UzzapTheme_Clay
        else -> R.style.UzzapTheme_Clay
    }

    /** Call before super.onCreate() in every themed activity. */
    fun apply(activity: Activity) {
        activity.setTheme(themeRes(current(activity)))
    }

    /** Resolve a semantic theme color attr (falls back to dark text if undefined). */
    fun resolve(context: Context, @AttrRes attr: Int, fallback: Int = 0xFF191919.toInt()): Int {
        val a = context.obtainStyledAttributes(intArrayOf(attr))
        val color = a.getColor(0, fallback)
        a.recycle()
        return color
    }
}
