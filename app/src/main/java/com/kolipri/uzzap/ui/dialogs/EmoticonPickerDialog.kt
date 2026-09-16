package com.kolipri.uzzap.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import com.kolipri.uzzap.R

object EmoticonPickerDialog {

    private val emoticons: List<Pair<Int, String>> = listOf(
        R.drawable.d_emoticon_smile to ":)",
        R.drawable.d_emoticon_sad to ":(",
        R.drawable.d_emoticon_wink to ";)",
        R.drawable.d_emoticon_tongue to ":p",
        R.drawable.d_emoticon_laugh to ":D",
        R.drawable.d_emoticon_love to "<3",
        R.drawable.d_emoticon_cry to ";(",
        R.drawable.d_emoticon_angry to ">:(",
        R.drawable.d_emoticon_surprise to ":o",
        R.drawable.d_emoticon_sick to ":s",
        R.drawable.d_emoticon_clown to ":o)",
        R.drawable.d_emoticon_party to "\\o/",
        R.drawable.d_emoticon_drink to "[drink]",
        R.drawable.d_emoticon_rose to "[rose]",
        R.drawable.d_kiss to "[kiss]",
        R.drawable.d_emoticon_11 to "[emoticon-11]",
        R.drawable.d_emoticon_12 to "[emoticon-12]",
        R.drawable.d_emoticon_13 to "[emoticon-13]",
        R.drawable.d_emoticon_14 to "[emoticon-14]",
        R.drawable.d_emoticon_15 to "[emoticon-15]",
        R.drawable.d_emoticon_16 to "[emoticon-16]",
        R.drawable.d_emoticon_17 to "[emoticon-17]",
        R.drawable.d_emoticon_18 to "[emoticon-18]",
        R.drawable.d_emoticon_19 to "[emoticon-19]",
        R.drawable.d_emoticon_20 to "[emoticon-20]",
        R.drawable.d_emoticon_47 to "[emoticon-47]"
    )

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

        emoticons.forEach { (res, code) ->
            grid.addView(ImageView(context).apply {
                setImageResource(res)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(pad, pad, pad, pad)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = itemSize
                    height = itemSize
                }
                setBackgroundResource(android.R.drawable.list_selector_background)
                contentDescription = "Emoticon $code"
                setOnClickListener {
                    onPick(code)
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
