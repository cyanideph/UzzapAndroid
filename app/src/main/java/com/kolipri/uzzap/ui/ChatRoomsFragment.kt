package com.kolipri.uzzap.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.ModeratorRoleClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.UnifiedRoomClient
import com.kolipri.uzzap.model.ChatRoomCategory
import com.kolipri.uzzap.model.ChatRoomInfo
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem

class ChatRoomsFragment : Fragment(R.layout.fragment_chat_rooms) {
    private var loaded=false
    private var selectedCategory:ChatRoomCategory?=null
    private lateinit var tvLoading:TextView
    private lateinit var contentContainer:View
    private lateinit var tvHeader:TextView
    private lateinit var listView:ListView
    private val host get()=requireActivity() as ChatHostActivity

    override fun onViewCreated(view:View,savedInstanceState:Bundle?){
        super.onViewCreated(view,savedInstanceState)
        tvLoading=view.findViewById(R.id.tv_loading);contentContainer=view.findViewById(R.id.content_container);tvHeader=view.findViewById(R.id.tv_header);listView=view.findViewById(R.id.list_rooms)
        refreshFromSupabase(initial=!loaded)
    }
    override fun onResume(){super.onResume();if(::listView.isInitialized)refreshFromSupabase()}
    override fun onHiddenChanged(hidden:Boolean){super.onHiddenChanged(hidden);if(!hidden&&::listView.isInitialized)refreshFromSupabase()}
    fun setupScreen(){host.setScreenTitle(getString(R.string.chat_rooms_title));host.showConnectedBall(true);refreshSoftkeys()}
    fun onBackPressed():Boolean{if(selectedCategory!=null){showCategories();return true};return false}
    private fun refreshSoftkeys(){if(selectedCategory==null)host.setSoftkeys(getString(R.string.softkey_options),getString(R.string.softkey_menu),onLeft={showOptionsMenu()},onRight={showOptionsMenu()})else host.setSoftkeys(getString(R.string.softkey_options),getString(R.string.softkey_back),onLeft={showOptionsMenu()},onRight={showCategories()})}

    private fun refreshFromSupabase(initial:Boolean=false){
        if(initial){tvLoading.visibility=View.VISIBLE;contentContainer.visibility=View.GONE}
        // Refresh the authoritative role before rendering rooms. This makes the
        // Moderator Room appear immediately after a moderator/admin signs in.
        ModeratorRoleClient.refresh {
            UnifiedRoomClient.fetchCategoriesAndRooms{remoteCats->
                if(!isAdded)return@fetchCategoriesAndRooms
                if(remoteCats==null){tvLoading.visibility=View.GONE;contentContainer.visibility=View.VISIBLE;Toast.makeText(requireContext(),"Could not load Philippine chat rooms.",Toast.LENGTH_SHORT).show();return@fetchCategoriesAndRooms}
                UnifiedRoomClient.liveCounts{counts->
                    if(!isAdded)return@liveCounts
                    val live=counts?:emptyMap()
                    val updated=remoteCats.map{category->
                        val rooms=category.rooms.map{room->ChatRoomInfo(room.name,live[room.name]?:0,20)}
                        ChatRoomCategory(category.name,rooms.sumOf{it.userCount},rooms.sumOf{it.capacity},rooms)
                    }.toMutableList()

                    // Moderator Room is a protected app-level room, not a province.
                    // It is intentionally not inserted into the public Philippine
                    // region/province hierarchy returned by Supabase.
                    if(AppState.isModerator){
                        val moderatorRoom=ChatRoomInfo("Moderator Room",live["Moderator Room"]?:0,50)
                        updated.add(ChatRoomCategory("Moderation",moderatorRoom.userCount,moderatorRoom.capacity,listOf(moderatorRoom)))
                    }

                    AppState.categories=updated
                    loaded=true;tvLoading.visibility=View.GONE;contentContainer.visibility=View.VISIBLE
                    val selectedName=selectedCategory?.name;selectedCategory=selectedName?.let{n->updated.firstOrNull{it.name==n}}
                    if(selectedCategory!=null)showRoomsFor(selectedCategory!!)else showCategories()
                }
            }
        }
    }
    private fun showCategories(){selectedCategory=null;tvHeader.text="Regions (1/1)";listView.adapter=object:ArrayAdapter<ChatRoomCategory>(requireContext(),0,AppState.categories){override fun getView(position:Int,convertView:View?,parent:ViewGroup):View{val v=convertView?:LayoutInflater.from(context).inflate(R.layout.item_room_row,parent,false);val c=AppState.categories[position];val moderator=c.name=="Moderation";v.findViewById<ImageView>(R.id.iv_room_icon).setImageResource(if(moderator)R.drawable.d_chatroom_category else R.drawable.d_chatroom_category);v.findViewById<TextView>(R.id.tv_room_label).text="${if(moderator)"🛡️ " else ""}${c.name} (${c.userCount}/${c.capacity})";return v}};listView.setOnItemClickListener{_,_,position,_->showRoomsFor(AppState.categories[position])};refreshSoftkeys()}
    private fun showRoomsFor(category:ChatRoomCategory){selectedCategory=category;tvHeader.text="${category.name} (1/1)";listView.adapter=object:ArrayAdapter<Any>(requireContext(),0,category.rooms){override fun getView(position:Int,convertView:View?,parent:ViewGroup):View{val v=convertView?:LayoutInflater.from(context).inflate(R.layout.item_room_row,parent,false);val r=category.rooms[position];v.findViewById<ImageView>(R.id.iv_room_icon).setImageResource(R.drawable.d_chat_icon);v.findViewById<TextView>(R.id.tv_room_label).text="${if(category.name=="Moderation")"🛡️ " else ""}${r.name} (${r.userCount}/${r.capacity})";return v}};listView.setOnItemClickListener{_,_,position,_->host.openRoom(category.rooms[position].name)};refreshSoftkeys()}
    private fun showOptionsMenu(){val items=if(selectedCategory==null)listOf("Join Room","Select Region","Refresh Room List","Refresh Region List","Search Rooms","Backend Status")else listOf("Join Room","Room Actions","Refresh Room List","Refresh Region List","Search Rooms","Backend Status");J2mePopup.show(requireContext(),items.map{PopupItem(it)}){index->when(index){0->if(selectedCategory?.rooms?.isNotEmpty()==true)host.openRoom(selectedCategory!!.rooms.first().name)else Toast.makeText(requireContext(),"Select a room first.",Toast.LENGTH_SHORT).show();1->if(selectedCategory==null)showCategories()else selectedCategory?.rooms?.firstOrNull()?.let{startActivity(UzzapParityActivity.intent(requireContext(),UzzapParityActivity.MODE_ROOM_ACTIONS,it.name))};2,3->refreshFromSupabase();4->showSearch();5->androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Supabase Status").setMessage("Status: ${SupabaseClient.connectionStatus}\nRegions: ${AppState.categories.size}\nProvince rooms: ${AppState.categories.sumOf{it.rooms.size}}").setPositiveButton("OK",null).show()}}}
    private fun showSearch(){val input=android.widget.EditText(requireContext()).apply{hint="Province or room name"};androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Search Rooms").setView(input).setPositiveButton("Search"){_,_->val q=input.text.toString().trim().lowercase();if(q.isEmpty())return@setPositiveButton;val matches=AppState.categories.flatMap{it.rooms}.filter{it.name.lowercase().contains(q)};if(matches.isEmpty())Toast.makeText(requireContext(),"No rooms found.",Toast.LENGTH_SHORT).show()else androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Rooms (${matches.size})").setItems(matches.map{it.name}.toTypedArray()){_,which->host.openRoom(matches[which].name)}.show()}.setNegativeButton(getString(R.string.softkey_cancel),null).show()}
}
