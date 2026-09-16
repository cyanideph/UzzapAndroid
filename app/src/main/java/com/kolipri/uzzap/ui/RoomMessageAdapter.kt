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
import com.kolipri.uzzap.ui.util.UzzapColorCodeParser
import com.kolipri.uzzap.ui.util.UzzapEmoticonRenderer

/** RecyclerView adapter for room history. System messages are centered; normal messages keep the classic layout. */
class RoomMessageAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object { private const val TYPE_SYSTEM = 1; private const val TYPE_NORMAL = 2 }

    private val items = mutableListOf<ChatMessage>()
    var onMessageLongClickListener: ((ChatMessage) -> Unit)? = null

    fun setItems(messages: List<ChatMessage>) { items.clear(); items.addAll(messages); notifyDataSetChanged() }
    fun removeById(id: Long) { val idx = items.indexOfFirst { it.id == id }; if (idx >= 0) { items.removeAt(idx); notifyItemRemoved(idx) } }
    fun insertAtTop(messages: List<ChatMessage>) { if (messages.isEmpty()) return; items.addAll(0, messages); notifyItemRangeInserted(0, messages.size) }
    fun add(message: ChatMessage) { items.add(message); notifyItemInserted(items.lastIndex) }
    fun replaceAt(index: Int, message: ChatMessage) { if (index !in items.indices) return; items[index] = message; notifyItemChanged(index) }
    fun indexOf(message: ChatMessage): Int = items.indexOf(message)
    override fun getItemCount(): Int = items.size

    private fun render(text: String): CharSequence = UzzapColorCodeParser.parse(UzzapEmoticonRenderer.render(text), ColorDefaults.TEXT)

    override fun getItemViewType(position: Int): Int {
        val msg = items[position]
        return if (msg.isSystem || msg.sender.equals("uzzapbot", true) || msg.sender.equals("System", true)) TYPE_SYSTEM else TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = if (viewType == TYPE_SYSTEM) {
        SystemHolder(TextView(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(12, 8, 12, 8)
            textSize = 15f; setTextColor(ContextCompat.getColor(context, R.color.uzzap_special_blue))
        })
    } else {
        NormalHolder(LinearLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL; setPadding(12, 4, 12, 4)
        })
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        holder.itemView.setOnLongClickListener { onMessageLongClickListener?.invoke(message); true }
        if (holder is SystemHolder) {
            val raw = if (message.sender.equals("uzzapbot", true) && !message.body.startsWith("uzzapbot", true)) "uzzapbot: ${message.body}" else message.body.ifEmpty { message.sender }
            holder.text.text = render(raw)
        } else if (holder is NormalHolder) {
            holder.container.removeAllViews()
            holder.container.addView(TextView(context).apply {
                text = render(message.sender + ":")
                textSize = 15f; setTextColor(ContextCompat.getColor(context, R.color.uzzap_special_red))
            })
            holder.container.addView(TextView(context).apply {
                text = render(message.body)
                textSize = 15f; setTextColor(ContextCompat.getColor(context, R.color.uzzap_text))
            })
        }
    }

    private object ColorDefaults { const val TEXT = 0xFF333333.toInt() }
    class SystemHolder(val text: TextView) : RecyclerView.ViewHolder(text)
    class NormalHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
}
