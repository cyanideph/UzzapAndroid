package com.kolipri.uzzap.ui

import android.app.Activity
import android.content.Context
import androidx.annotation.AttrRes
import com.kolipri.uzzap.R

/** Real runtime theming: stores the chosen theme, applies it to activities, and
 *  resolves semantic colors for programmatically-built UI. Replaces the dead
 *  write-only theme picker from UzzapParityActivity. */
object ThemeManager {
    private const val PREFS = "uzzap_prefs"
    private const val KEY_THEME = "theme"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, "classic") ?: "classic"

    fun setTheme(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_THEME, name).apply()
    }

    fun themeRes(name: String): Int = when (name.lowercase()) {
        "dark" -> R.style.UzzapTheme_Dark
        "black" -> R.style.UzzapTheme_Black
        "blue" -> R.style.UzzapTheme_Blue
        "green" -> R.style.UzzapTheme_Green
        else -> R.style.UzzapTheme_Classic
    }

    /** Call before super.onCreate() in every themed activity. */
    fun apply(activity: Activity) { activity.setTheme(themeRes(current(activity))) }

    /** Resolve a semantic theme color attr (falls back to dark text if undefined). */
    fun resolve(context: Context, @AttrRes attr: Int, fallback: Int = 0xFF191919.toInt()): Int {
        val a = context.obtainStyledAttributes(intArrayOf(attr))
        val color = a.getColor(0, fallback)
        a.recycle()
        return color
    }

    /** Content-card background. Replaces hardcoded Color.WHITE surfaces. */
    fun card(context: Context): Int = resolve(context, R.attr.uzzapColorCard, 0xFFFFFFFF.toInt())

    /** Destructive/moderation-red actions and badges. */
    fun danger(context: Context): Int = resolve(context, R.attr.uzzapColorDanger, 0xFFB00020.toInt())

    /** Healthy/online accents. */
    fun success(context: Context): Int = resolve(context, R.attr.uzzapColorSuccess, 0xFF2E7D32.toInt())
}
