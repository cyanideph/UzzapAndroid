package com.kolipri.uzzap.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.ui.util.EmoticonParser
import com.kolipri.uzzap.ui.util.UzzapColorCodeParser

/** Room history adapter with unified DiceBear identity rendering. */
class RoomMessageAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object { private const val TYPE_SYSTEM = 1; private const val TYPE_NORMAL = 2 }
    private val items = mutableListOf<ChatMessage>()
    var onMessageLongClickListener: ((ChatMessage) -> Unit)? = null

    fun setItems(messages: List<ChatMessage>) {
        val byId = linkedMapOf<Long, ChatMessage>(); val local = mutableListOf<ChatMessage>()
        for (item in items) { if (item.id > 0) byId[item.id] = item else local.add(item) }
        for (item in messages) { if (item.id > 0) byId[item.id] = item else if (local.none { it == item }) local.add(item) }
        items.clear(); items.addAll(byId.values.sortedBy { it.id }); items.addAll(local); notifyDataSetChanged()
    }
    fun removeById(id: Long) { val idx=items.indexOfFirst { it.id==id }; if(idx>=0){items.removeAt(idx);notifyItemRemoved(idx)} }
    fun insertAtTop(messages: List<ChatMessage>) { if(messages.isEmpty())return;items.addAll(0,messages);notifyItemRangeInserted(0,messages.size) }
    fun add(message: ChatMessage) { if(message.id>0&&items.any{it.id==message.id})return;items.add(message);notifyItemInserted(items.lastIndex) }
    fun replaceAt(index:Int,message:ChatMessage){if(index !in items.indices)return;items[index]=message;notifyItemChanged(index)}
    fun indexOf(message:ChatMessage)=items.indexOf(message)
    override fun getItemCount()=items.size
    override fun getItemViewType(position:Int)=if(items[position].isSystem)TYPE_SYSTEM else TYPE_NORMAL

    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):RecyclerView.ViewHolder=if(viewType==TYPE_SYSTEM){
        SystemHolder(TextView(context).apply{layoutParams=RecyclerView.LayoutParams(-1,-2);gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;setPadding(12,8,12,8);textSize=15f;setTextColor(ContextCompat.getColor(context,R.color.uzzap_special_blue))})
    }else{
        val row=LinearLayout(context).apply{layoutParams=RecyclerView.LayoutParams(-1,-2);orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP;setPadding(12,4,12,4)}
        val container=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(10,0,0,0)}
        row.addView(container,LinearLayout.LayoutParams(0,-2,1f))
        NormalHolder(row,container)
    }

    override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int){
        val message=items[position]
        holder.itemView.setOnLongClickListener{onMessageLongClickListener?.invoke(message);true}
        if(holder is SystemHolder) holder.text.text=EmoticonParser.parse(context,message.body,16f)
        else if(holder is NormalHolder){
            holder.container.removeAllViews()
            holder.container.addView(TextView(context).apply{text=renderFormatted(displaySender(message.sender)+":",R.color.uzzap_special_red);textSize=17f;setTypeface(typeface,Typeface.BOLD)})
            holder.container.addView(TextView(context).apply{text=renderFormatted(message.body,R.color.uzzap_text);textSize=16f})
        }
    }

    private fun displaySender(sender:String):String{
        val u=AppState.username.trim();val n=AppState.nickname.trim()
        return if(u.isNotBlank()&&n.isNotBlank()&&sender.trim().equals(u,true))n else sender
    }
    private fun renderFormatted(value:String,defaultColorRes:Int):CharSequence{
        val defaultColor=ContextCompat.getColor(context,defaultColorRes)
        return EmoticonParser.parse(context,UzzapColorCodeParser.parse(value,defaultColor),16f)
    }
    class SystemHolder(val text:TextView):RecyclerView.ViewHolder(text)
    class NormalHolder(view:View,val container:LinearLayout):RecyclerView.ViewHolder(view)
}