package com.kolipri.uzzap.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import com.kolipri.uzzap.ui.util.EmoticonParser

/**
 * Uzzap emoticon picker.
 *
 * The picker does not maintain its own emoticon mapping. It reads the
 * Android central registry from EmoticonParser so picker, composer and
 * message renderer always use exactly the same code -> drawable mapping.
 */
object EmoticonPickerDialog {

    fun show(context: Context, onPick: (String) -> Unit) {
        val dialog = Dialog(context)
        dialog.setTitle("Add Emoticon")

        val grid = GridLayout(context).apply {
            columnCount = 5
            setPadding(20, 20, 20, 20)
        }
        val density = context.resources.displayMetrics.density
        val itemSize = (52 * density).toInt()
        val pad = (10 * density).toInt()

        EmoticonParser.definitions.forEach { definition ->
            grid.addView(ImageView(context).apply {
                setImageResource(definition.drawableRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(pad, pad, pad, pad)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = itemSize
                    height = itemSize
                }
                setBackgroundResource(android.R.drawable.list_selector_background)
                contentDescription = "Emoticon " + definition.label
                setOnClickListener {
                    onPick(definition.code)
                    dialog.dismiss()
                }
            })
        }

        val scroll = ScrollView(context).apply {
            addView(grid)
        }
        dialog.setContentView(scroll)
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.show()
    }
}
