package com.kolipri.uzzap.data

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.kolipri.uzzap.R

/** Legacy Uzzap visual system. The original Java theme architecture is the visual source of truth. */
object LegacyThemeManager {
    private const val PREFS = "uzzap_prefs"
    private const val KEY_THEME = "legacy_theme"
    const val DEFAULT = "default"
    const val BLACK = "black"
    const val DOLPHINS = "dolphins"
    const val HEARTS = "hearts"
    const val ROSES = "roses"
    const val UZZAP = "uzzap"

    data class Theme(
        val id: String,
        val displayName: String,
        val background: Int,
        val surface: Int,
        val primary: Int,
        val secondary: Int,
        val text: Int,
        val special: Int,
        val onPrimary: Int,
        val defaultAssetNamespace: String = DEFAULT
    )

    private val defaultTheme = Theme(
        DEFAULT, "Default", Color.WHITE, Color.WHITE,
        Color.rgb(0xDF, 0xDF, 0xDF), Color.rgb(0x0D, 0x0C, 0xAF),
        Color.BLACK, Color.rgb(0xAF, 0x0C, 0x0D), Color.WHITE
    )
    private val uzzapTheme = Theme(
        UZZAP, "Uzzap / Reptilian", Color.rgb(0xF9, 0xF9, 0xF9), Color.rgb(0xF9, 0xF9, 0xF9),
        Color.rgb(0x8E, 0xEF, 0x04), Color.rgb(0x05, 0x74, 0x1D),
        Color.BLACK, Color.rgb(0xAF, 0x0C, 0x0D), Color.BLACK, UZZAP
    )
    private val fallbackThemes = listOf(
        Theme(BLACK, "Black", Color.BLACK, Color.rgb(0x18, 0x18, 0x18), Color.rgb(0x30, 0x30, 0x30), Color.rgb(0x80, 0x80, 0x80), Color.WHITE, Color.rgb(0xFF, 0x80, 0x80), Color.WHITE),
        Theme(DOLPHINS, "Dolphins", defaultTheme.background, defaultTheme.surface, defaultTheme.primary, defaultTheme.secondary, defaultTheme.text, defaultTheme.special, defaultTheme.onPrimary),
        Theme(HEARTS, "Hearts", defaultTheme.background, defaultTheme.surface, defaultTheme.primary, defaultTheme.secondary, defaultTheme.text, defaultTheme.special, defaultTheme.onPrimary),
        Theme(ROSES, "Roses", defaultTheme.background, defaultTheme.surface, defaultTheme.primary, defaultTheme.secondary, defaultTheme.text, defaultTheme.special, defaultTheme.onPrimary)
    )

    fun themes(): List<Theme> = listOf(defaultTheme, uzzapTheme) + fallbackThemes

    /** New installs open in the recovered Uzzap/Reptilian skin so the app immediately feels like legacy Uzzap. */
    fun current(context: Context): Theme {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, UZZAP).orEmpty()
        return themes().firstOrNull { it.id == id } ?: uzzapTheme
    }

    fun select(context: Context, id: String) {
        val safe = themes().firstOrNull { it.id == id }?.id ?: UZZAP
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, safe).apply()
        AppState.themeName = safe
    }

    fun assetPath(theme: Theme, assetName: String): String = "themes/${theme.defaultAssetNamespace}/$assetName"
    fun fallbackAssetPath(assetName: String): String = "themes/$DEFAULT/$assetName"

    fun applyChrome(root: View, theme: Theme) {
        root.setBackgroundResource(if (theme.id == UZZAP) R.drawable.legacy_background else 0)
        if (theme.id != UZZAP) root.setBackgroundColor(theme.background)
        root.findViewById<View>(R.id.status_bar_spacer)?.setBackgroundColor(theme.primary)
        root.findViewById<View>(R.id.content_frame)?.let {
            if (theme.id == UZZAP) it.setBackgroundResource(R.drawable.legacy_background) else it.setBackgroundColor(theme.background)
        }
        root.findViewById<View>(R.id.nav_bar_spacer)?.setBackgroundColor(theme.secondary)
        root.findViewById<View>(R.id.tab_strip_container)?.setBackgroundColor(theme.surface)
        root.findViewById<TextView>(R.id.tv_title)?.setTextColor(theme.onPrimary)
        root.findViewById<TextView>(R.id.btn_left)?.setTextColor(theme.onPrimary)
        root.findViewById<TextView>(R.id.btn_right)?.setTextColor(theme.onPrimary)
    }

    fun cardBackground(theme: Theme): GradientDrawable = GradientDrawable().apply {
        setColor(theme.surface)
        cornerRadius = 14f
        setStroke(1, theme.primary)
    }
}
