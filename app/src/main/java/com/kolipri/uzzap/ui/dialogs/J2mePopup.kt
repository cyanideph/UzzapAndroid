package com.kolipri.uzzap.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.kolipri.uzzap.R

data class PopupItem(
    val label: String,
    val iconRes: Int? = null,
    val enabled: Boolean = true
)

/** Compact legacy Uzzap/J2ME option menu and message popup. */
object J2mePopup {
    fun show(context: Context, items: List<PopupItem>, selectedIndex: Int = 0, onSelect: (Int) -> Unit) {
        val dialog = Dialog(context, R.style.Theme_Uzzap_Popup)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.BOTTOM)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_popup_panel) }
        items.forEachIndexed { index, item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(6, 5, 6, 5); minimumHeight = dpToPx(context, 28)
                setBackgroundResource(R.drawable.bg_menu_row_selector); isSelected = index == selectedIndex
                setOnClickListener { dialog.dismiss(); if (item.enabled) onSelect(index) }
            }
            item.iconRes?.let { res -> row.addView(android.widget.ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 18), dpToPx(context, 18)).apply { marginEnd = dpToPx(context, 6) }
                setImageResource(res)
            }) }
            row.addView(TextView(context).apply { text = item.label; textSize = 14f; includeFontPadding = true; setTextColor(if (item.enabled) Color.BLACK else Color.GRAY) })
            list.addView(row)
        }
        root.addView(list)
        root.addView(softkeys(context, dialog, items, selectedIndex, onSelect))
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun showMessage(context: Context, title: String, message: String, onClose: (() -> Unit)? = null) {
        val dialog = Dialog(context, R.style.Theme_Uzzap_Popup)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.BOTTOM)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_popup_panel) }
        root.addView(TextView(context).apply {
            text = title; textSize = 14f; setTextColor(Color.BLACK); setPadding(6, 5, 6, 4); setBackgroundColor(Color.WHITE)
        })
        root.addView(TextView(context).apply {
            text = message; textSize = 13f; setTextColor(Color.BLACK); setPadding(6, 6, 6, 8); setBackgroundColor(Color.WHITE)
        })
        root.addView(softkeys(context, dialog, emptyList(), 0) { onClose?.invoke() })
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun softkeys(context: Context, dialog: Dialog, items: List<PopupItem>, selectedIndex: Int, onSelect: (Int) -> Unit): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; setBackgroundResource(R.drawable.bar_bottom)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 32))
        addView(TextView(context).apply {
            text = context.getString(R.string.softkey_select); setTextColor(Color.WHITE); textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START; setPadding(6, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { dialog.dismiss(); if (items.isNotEmpty() && selectedIndex in items.indices && items[selectedIndex].enabled) onSelect(selectedIndex) }
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.softkey_cancel); setTextColor(Color.WHITE); textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END; setPadding(0, 0, 6, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { dialog.dismiss() }
        })
    }

    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
