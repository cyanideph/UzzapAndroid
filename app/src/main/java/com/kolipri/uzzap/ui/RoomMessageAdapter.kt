package com.kolipri.uzzap.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kolipri.uzzap.R
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.ui.util.EmoticonParser

/** RecyclerView adapter for room history. System messages are centered; normal messages keep the classic layout. */
class RoomMessageAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_SYSTEM = 1
        private const val TYPE_NORMAL = 2
    }

    private val items = mutableListOf<ChatMessage>()

    fun setItems(messages: List<ChatMessage>) {
        items.clear()
        items.addAll(messages)
        notifyDataSetChanged()
    }

    fun insertAtTop(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        items.addAll(0, messages)
        notifyItemRangeInserted(0, messages.size)
    }

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

    fun replaceAt(index: Int, message: ChatMessage) {
        if (index !in items.indices) return
        items[index] = message
        notifyItemChanged(index)
    }

    fun indexOf(message: ChatMessage): Int = items.indexOf(message)

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = if (items[position].isSystem) TYPE_SYSTEM else TYPE_NORMAL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SYSTEM) {
            SystemHolder(TextView(context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(12, 8, 12, 8)
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.uzzap_special_blue))
            })
        } else {
            val container = LinearLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayout.VERTICAL
                setPadding(12, 4, 12, 4)
            }
            NormalHolder(container)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        if (holder is SystemHolder) {
            holder.text.text = EmoticonParser.parse(context, message.body.ifEmpty { message.sender }, 15f)
        } else if (holder is NormalHolder) {
            holder.container.removeAllViews()
            holder.container.addView(TextView(context).apply {
                text = "${message.sender}:"
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.uzzap_special_red))
            })
            holder.container.addView(TextView(context).apply {
                text = EmoticonParser.parse(context, message.body, 15f)
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.uzzap_text))
            })
        }
    }

    class SystemHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    class NormalHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
}
