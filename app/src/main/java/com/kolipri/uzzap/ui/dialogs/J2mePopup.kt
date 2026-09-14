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

/**
 * Mimics the little "Select / Cancel" option list that floats above the
 * softkey bar in the reference screenshots (see the buddy list & chat room
 * long-press menus).
 */
object J2mePopup {

    fun show(
        context: Context,
        items: List<PopupItem>,
        selectedIndex: Int = 0,
        onSelect: (Int) -> Unit
    ) {
        val dialog = Dialog(context, R.style.Theme_Uzzap_Popup)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.BOTTOM)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_popup_panel)
        }
        items.forEachIndexed { index, item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(28, 20, 28, 20)
                setBackgroundResource(R.drawable.bg_menu_row_selector)
                isSelected = index == selectedIndex
                setOnClickListener {
                    dialog.dismiss()
                    if (item.enabled) onSelect(index)
                }
            }
            item.iconRes?.let { res ->
                row.addView(android.widget.ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 22), dpToPx(context, 22)).apply {
                        marginEnd = dpToPx(context, 12)
                    }
                    setImageResource(res)
                })
            }
            row.addView(TextView(context).apply {
                text = item.label
                textSize = 17f
                setTextColor(if (item.enabled) Color.BLACK else Color.GRAY)
            })
            list.addView(row)
        }
        root.addView(list)

        // Select / Cancel softkey bar, matching the rest of the app chrome.
        val softkeys = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bar_bottom)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 46))
        }
        softkeys.addView(TextView(context).apply {
            text = context.getString(R.string.softkey_select)
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(30, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                dialog.dismiss()
                if (items.isNotEmpty() && items[selectedIndex].enabled) onSelect(selectedIndex)
            }
        })
        softkeys.addView(TextView(context).apply {
            text = context.getString(R.string.softkey_cancel)
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, 0, 30, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(softkeys)

        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
