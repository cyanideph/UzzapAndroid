package com.kolipri.uzzap.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentTransaction
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.RoomInvitationClient
import com.kolipri.uzzap.data.RoomPresenceClient
import com.kolipri.uzzap.data.SupabaseRealtimeClient
import com.kolipri.uzzap.data.UzzapNotificationManager

class ChatHostActivity:BaseChromeActivity(){
    private val buddyFragment=BuddyListFragment();private val roomsFragment=ChatRoomsFragment();private val roomFragments=linkedMapOf<String,ChatRoomFragment>();private val joinedRooms=mutableListOf<String>();private val handledInvitationIds=mutableSetOf<Long>();private var currentTab=0;private var selectedRoomName:String?=null;private val handler=Handler(Looper.getMainLooper());private var monitoring=false
    private val notificationPoll=object:Runnable{override fun run(){if(!monitoring||isFinishing)return;if(currentTab!=0){PresenceClient.heartbeat(AppState.presence!=com.kolipri.uzzap.model.Presence.INVISIBLE);PrivateMessageStateClient.fetchUnread(AppState.username){state->if(!isFinishing&&!isDestroyed&&state!=null&&state.count>0)UzzapNotificationManager.notifyPrivateMessages(this@ChatHostActivity,state.count,state.senders)};RoomInvitationClient.fetchPending(AppState.username){invitations->if(isFinishing||isDestroyed)return@fetchPending;val pending=invitations?.firstOrNull{!handledInvitationIds.contains(it.id)};if(pending!=null){handledInvitationIds.add(pending.id);UzzapDialogs.show(this@ChatHostActivity, iconAsset = "anim-icons/menu_connect.gif", title = "Room Invitation", message = "${pending.inviterUsername} invited you to join '${pending.roomName}' chatroom.\nWould you like to join?", positive = "Join", onPositive = { RoomInvitationClient.respond(pending.id, true) {}; openRoom(pending.roomName) }, negative = "Decline", onNegative = { RoomInvitationClient.respond(pending.id, false) {} })}}};handler.postDelayed(this,8000)}}
    override fun contentLayoutRes():Int?=null
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);UzzapNotificationManager.initialize(this);showConnectedBall(true);showTabStrip(true);supportFragmentManager.beginTransaction().add(R.id.content_frame,buddyFragment,TAG_BUDDY).add(R.id.content_frame,roomsFragment,TAG_ROOMS).hide(roomsFragment).commitNow();currentTab=if(intent.getStringExtra(EXTRA_MODE)==MODE_CHAT)1 else 0;applyVisibility();renderTabs();onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){when{currentTab>=ROOM_TAB_START->confirmHideSelectedRoom();currentTab==1&&roomsFragment.onBackPressed()->Unit;currentTab==1&&intent.getStringExtra(EXTRA_MODE)==MODE_CHAT->finish();currentTab==1->goToBuddyList();else->finish()}}})}
    override fun onResume(){super.onResume();monitoring=true;RoomPresenceClient.resumeAll();SupabaseRealtimeClient.start();handler.removeCallbacks(notificationPoll);handler.post(notificationPoll)}
    override fun onPause(){monitoring=false;handler.removeCallbacks(notificationPoll);RoomPresenceClient.pauseHeartbeat();PresenceClient.heartbeat(false);super.onPause()}
    override fun onDestroy(){monitoring=false;handler.removeCallbacksAndMessages(null);PresenceClient.heartbeat(false);SupabaseRealtimeClient.stop();super.onDestroy()}
    override fun onNetworkRestored(){super.onNetworkRestored();RoomPresenceClient.resumeAll();SupabaseRealtimeClient.start();if(currentTab!=0){PresenceClient.heartbeat(AppState.presence!=com.kolipri.uzzap.model.Presence.INVISIBLE);PrivateMessageStateClient.fetchUnread(AppState.username){state->if(!isFinishing&&!isDestroyed&&state!=null&&state.count>0)UzzapNotificationManager.notifyPrivateMessages(this@ChatHostActivity,state.count,state.senders)}};buddyFragment.syncBuddiesFromSupabase()}
    fun confirmHideRoom()=confirmHideSelectedRoom()
    private fun confirmHideSelectedRoom(){val roomName=selectedRoomName?:return;UzzapDialogs.show(this,iconAsset="anim-icons/menu_exit.gif",title="Hide Room",message=getString(R.string.hide_room_confirm),positive="Hide",onPositive={hideRoom(roomName)})}
    fun confirmLeaveRoom(){val roomName=selectedRoomName?:return;UzzapDialogs.show(this,iconAsset="anim-icons/menu_exit.gif",title="Leave Room",message="Leave $roomName? You will need to join the room again to return.",positive="Leave",onPositive={leaveRoom(roomName)})}
    fun openRoom(roomName:String){if(!AppState.isRoomJoined(roomName))AppState.joinedRooms.add(roomName);if(!joinedRooms.contains(roomName)){joinedRooms.add(roomName);SupabaseRealtimeClient.refreshSubscriptions();val f=ChatRoomFragment.newInstance(roomName);roomFragments[roomName]=f;supportFragmentManager.beginTransaction().add(R.id.content_frame,f,roomTag(roomName)).hide(f).commitNow()};selectedRoomName=roomName;currentTab=ROOM_TAB_START+joinedRooms.indexOf(roomName);applyVisibility();renderTabs();notifyCurrentTabSelected()}
    fun hideRoom(roomName:String?=null){val target=roomName?:selectedRoomName?:return;val index=joinedRooms.indexOf(target);if(index<0)return;selectedRoomName=null;currentTab=1;applyVisibility();renderTabs();notifyCurrentTabSelected()}
    fun leaveRoom(roomName:String?=null){val target=roomName?:selectedRoomName?:return;RoomPresenceClient.leave(target,AppState.nickname);AppState.joinedRooms.remove(target);SupabaseRealtimeClient.refreshSubscriptions();val index=joinedRooms.indexOf(target);if(index<0)return;roomFragments.remove(target)?.let{supportFragmentManager.beginTransaction().remove(it).commitNow()};joinedRooms.removeAt(index);if(joinedRooms.isEmpty()){selectedRoomName=null;currentTab=1}else{val next=index.coerceAtMost(joinedRooms.lastIndex);selectedRoomName=joinedRooms[next];currentTab=ROOM_TAB_START+next};applyVisibility();renderTabs();notifyCurrentTabSelected()}
    fun selectTab(index:Int){if(index>=ROOM_TAB_START&&index-ROOM_TAB_START>=joinedRooms.size)return;currentTab=index;selectedRoomName=if(index>=ROOM_TAB_START)joinedRooms[index-ROOM_TAB_START]else null;applyVisibility();renderTabs();notifyCurrentTabSelected()};fun goToBuddyList()=selectTab(0);fun goToRoomsList()=selectTab(1)
    private fun notifyCurrentTabSelected(){when{currentTab==0->buddyFragment.setupScreen();currentTab==1->roomsFragment.setupScreen();currentTab>=ROOM_TAB_START->selectedRoomName?.let{roomFragments[it]?.setupScreen()}}}
    private fun applyVisibility(){val tx:FragmentTransaction=supportFragmentManager.beginTransaction();if(currentTab==0)tx.show(buddyFragment)else tx.hide(buddyFragment);if(currentTab==1)tx.show(roomsFragment)else tx.hide(roomsFragment);roomFragments.forEach{(name,f)->if(currentTab>=ROOM_TAB_START&&name==selectedRoomName)tx.show(f)else tx.hide(f)};tx.commitNowAllowingStateLoss()}
    private fun renderTabs(){tabStripContainer.removeAllViews();addIconTab(R.drawable.d_buddylist_icon,0,"EM");addIconTab(R.drawable.d_chatrooms,1,"Rooms");joinedRooms.forEachIndexed{index,name->addRoomIconTab(ROOM_TAB_START+index,name)}}
    private fun addIconTab(icon:Int,index:Int,description:String){val iv=ImageView(this);val size=(34*resources.displayMetrics.density).toInt();iv.layoutParams=LinearLayout.LayoutParams(size,size).apply{marginEnd=(4*resources.displayMetrics.density).toInt()};iv.setImageResource(icon);iv.setBackgroundResource(R.drawable.tab_bg_selector);iv.isSelected=index==currentTab;iv.contentDescription=description;iv.setPadding(4,4,4,4);iv.setOnClickListener{selectTab(index)};tabStripContainer.addView(iv)}
    private fun addRoomIconTab(index:Int,roomName:String){val iv=ImageView(this);val size=(34*resources.displayMetrics.density).toInt();iv.layoutParams=LinearLayout.LayoutParams(size,size).apply{marginEnd=(4*resources.displayMetrics.density).toInt()};iv.setImageResource(R.drawable.d_chatrooms);iv.setBackgroundResource(R.drawable.tab_bg_selector);iv.isSelected=index==currentTab;iv.contentDescription="Joined room $roomName";iv.setPadding(4,4,4,4);iv.setOnClickListener{selectTab(index)};tabStripContainer.addView(iv)}
    private fun roomTag(roomName:String)=TAG_ROOM_PREFIX+roomName.hashCode()
    companion object{const val MODE_EM="em";const val MODE_CHAT="chat";private const val EXTRA_MODE="extra_mode";private const val TAG_BUDDY="tab_buddy";private const val TAG_ROOMS="tab_rooms";private const val TAG_ROOM_PREFIX="tab_room_";private const val ROOM_TAB_START=2;fun intent(context:Context,mode:String):Intent=Intent(context,ChatHostActivity::class.java).putExtra(EXTRA_MODE,mode)}
}
