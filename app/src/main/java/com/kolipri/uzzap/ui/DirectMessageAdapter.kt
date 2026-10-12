package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.TimeUtils
import com.kolipri.uzzap.model.DirectMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** RecyclerView adapter for private-chat messages. Recycles rows instead of growing a ScrollView view tree. */
class DirectMessageAdapter(private val context: Context) : RecyclerView.Adapter<DirectMessageAdapter.MessageHolder>() {
    private val items = mutableListOf<DirectMessage>()

    fun itemsSnapshot(): List<DirectMessage> = items.toList()

    fun setItems(messages: List<DirectMessage>) {
        val unique = LinkedHashMap<Long, DirectMessage>()
        messages.forEach { message -> if (message.id > 0) unique[message.id] = message }
        // Never drop not-yet-acknowledged local echoes (id <= 0): a history
        // refresh arriving before the send callback must not erase them.
        val next = (unique.values.sortedBy { it.id } + items.filter { it.id <= 0 }.distinct()).toList()
        val old = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition].id == next[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition] == next[newItemPosition]
        })
        items.clear()
        items.addAll(next)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val bubble = TextView(context).apply {
            textSize = 15f
            setPadding(dp(13), dp(9), dp(13), dp(6))
            maxWidth = (context.resources.displayMetrics.widthPixels * 0.78f).toInt()
        }
        val time = TextView(context).apply {
            textSize = 10f
            setPadding(dp(5), dp(2), dp(5), 0)
        }
        row.addView(bubble, LinearLayout.LayoutParams(-2, -2))
        row.addView(time, LinearLayout.LayoutParams(-2, -2))
        return MessageHolder(row, bubble, time)
    }

    override fun onBindViewHolder(holder: MessageHolder, position: Int) {
        val message = items[position]
        val mine = message.sender.equals(AppState.username, true)
        holder.row.gravity = if (mine) Gravity.END else Gravity.START
        holder.bubble.setTextColor(if (mine) Color.WHITE else Color.rgb(35, 37, 41))
        holder.bubble.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(if (mine) Color.rgb(47, 116, 78) else Color.WHITE)
            if (!mine) setStroke(dp(1), Color.rgb(229, 231, 235))
        }
        holder.bubble.text = message.body
        val time = TimeUtils.parseIsoToEpochMillis(message.createdAt)?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))
        } ?: if (message.createdAt.isNotBlank()) message.createdAt
        else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        holder.time.text = if (mine) "$time  ✓" else time
        holder.time.setTextColor(Color.rgb(125, 130, 138))
    }

    override fun getItemCount(): Int = items.size

    class MessageHolder(
        val row: LinearLayout,
        val bubble: TextView,
        val time: TextView
    ) : RecyclerView.ViewHolder(row)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
