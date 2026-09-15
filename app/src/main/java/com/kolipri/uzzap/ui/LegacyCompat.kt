package com.kolipri.uzzap.ui

import android.widget.EditText
import android.graphics.drawable.GradientDrawable
import com.kolipri.uzzap.data.LegacyThemeManager as DataLegacyThemeManager

/** Small compatibility surface for legacy UI code that intentionally uses short J2ME-era names. */
object LegacyThemeManager {
    fun current(context: android.content.Context) = DataLegacyThemeManager.current(context)
    fun cardBackground(theme: DataLegacyThemeManager.Theme): GradientDrawable = DataLegacyThemeManager.cardBackground(theme)
}

var EditText.singleLine: Boolean
    get() = isSingleLine
    set(value) { setSingleLine(value) }

val DataLegacyThemeManager.Theme.specialText: Int
    get() = special

val DataLegacyThemeManager.Theme.editorBackground: Int
    get() = surface
