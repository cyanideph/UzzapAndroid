package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kolipri.uzzap.R
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.ui.util.EmoticonParser

/** Compact legacy Uzzap/J2ME-style room transcript. */
class RoomMessageAdapter(private val context: Context) : RecyclerView.Adapter<RoomMessageAdapter.MessageHolder>() {
    private val items = mutableListOf<ChatMessage>()
    var onMessageLongClickListener: ((ChatMessage) -> Unit)? = null

    fun setItems(messages: List<ChatMessage>) { items.clear(); items.addAll(messages); notifyDataSetChanged() }
    fun removeById(id: Long) { val i = items.indexOfFirst { it.id == id }; if (i >= 0) { items.removeAt(i); notifyItemRemoved(i) } }
    fun insertAtTop(messages: List<ChatMessage>) { if (messages.isNotEmpty()) { items.addAll(0, messages); notifyItemRangeInserted(0, messages.size) } }
    fun add(message: ChatMessage) { items.add(message); notifyItemInserted(items.lastIndex) }
    fun replaceAt(index: Int, message: ChatMessage) { if (index in items.indices) { items[index] = message; notifyItemChanged(index) } }
    fun indexOf(message: ChatMessage): Int = items.indexOf(message)
    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder = MessageHolder(TextView(context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(3, 0, 3, 0)
        gravity = Gravity.TOP or Gravity.START
        includeFontPadding = true
        textSize = 14f
        setLineSpacing(0f, 1.0f)
        setTextColor(ContextCompat.getColor(context, R.color.uzzap_text))
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    })

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
        val message = items[position]
        holder.itemView.setOnLongClickListener { onMessageLongClickListener?.invoke(message); true }
        val system = message.isSystem || message.sender.equals("uzzapbot", true) || message.sender.equals("System", true)
        val sender = message.sender.ifBlank { "System" }
        val body = message.body
        val prefix = if (system) "" else "* $sender:\n"
        val text = if (system) {
            if (sender.equals("uzzapbot", true) && !body.startsWith("uzzapbot", true)) "- uzzapbot: $body" else body.ifBlank { sender }
        } else prefix + body
        val styled = SpannableString(EmoticonParser.parse(context, text, 14f))
        if (!system && prefix.isNotEmpty()) styled.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.uzzap_special_red)), 0, prefix.length.coerceAtMost(styled.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (system && styled.isNotEmpty()) styled.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.uzzap_special_blue)), 0, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        holder.text.text = styled
    }

    class MessageHolder(val text: TextView) : RecyclerView.ViewHolder(text)
}
