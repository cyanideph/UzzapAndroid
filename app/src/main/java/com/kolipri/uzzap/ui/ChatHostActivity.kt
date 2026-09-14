package com.kolipri.uzzap.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.UzzapNotificationManager

class ChatHostActivity : BaseChromeActivity() {
    private val buddyFragment = BuddyListFragment()
    private val roomsFragment = ChatRoomsFragment()
    private val roomFragments = linkedMapOf<String, ChatRoomFragment>()
    private val joinedRooms = mutableListOf<String>()
    private var currentTab = 0
    private var selectedRoomName: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var monitoring = false
    private val notificationPoll = object : Runnable {
        override fun run() {
            if (!monitoring || isFinishing) return
            PresenceClient.heartbeat(AppState.presence != com.kolipri.uzzap.model.Presence.INVISIBLE)
            PrivateMessageStateClient.fetchUnread(AppState.username) { state ->
                if (!isFinishing && state != null && state.count > 0) UzzapNotificationManager.notifyPrivateMessages(this@ChatHostActivity, state.count, state.senders)
            }
            handler.postDelayed(this, 8_000L)
        }
    }

    override fun contentLayoutRes(): Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UzzapNotificationManager.initialize(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        showConnectedBall(true); showTabStrip(true)
        supportFragmentManager.beginTransaction().add(R.id.content_frame, buddyFragment, TAG_BUDDY).add(R.id.content_frame, roomsFragment, TAG_ROOMS).hide(roomsFragment).commitNow()
        val startOnRooms = intent.getStringExtra(EXTRA_MODE) == MODE_CHAT
        currentTab = if (startOnRooms) 1 else 0
        applyVisibility(); renderTabs(); notifyCurrentTabSelected()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when { currentTab >= ROOM_TAB_START -> confirmLeaveSelectedRoom(); currentTab == 1 && roomsFragment.onBackPressed() -> Unit; currentTab == 1 && intent.getStringExtra(EXTRA_MODE) == MODE_CHAT -> finish(); currentTab == 1 -> goToBuddyList(); else -> finish() }
            }
        })
    }

    override fun onResume() { super.onResume(); monitoring = true; handler.removeCallbacks(notificationPoll); handler.post(notificationPoll) }
    override fun onPause() { monitoring = false; handler.removeCallbacks(notificationPoll); PresenceClient.heartbeat(false); super.onPause() }
    override fun onDestroy() { monitoring = false; handler.removeCallbacksAndMessages(null); PresenceClient.heartbeat(false); super.onDestroy() }

    fun confirmLeaveRoom() = confirmLeaveSelectedRoom()
    private fun confirmLeaveSelectedRoom() {
        val roomName = selectedRoomName ?: return
        AlertDialog.Builder(this).setTitle("Leave Room").setMessage(getString(R.string.leave_room_confirm)).setPositiveButton(getString(R.string.softkey_yes)) { _, _ -> SupabaseClient.leaveRoom(roomName, AppState.nickname); leaveRoom(roomName) }.setNegativeButton(getString(R.string.softkey_no), null).show()
    }
    fun openRoom(roomName: String) {
        if (!joinedRooms.contains(roomName)) { joinedRooms.add(roomName); val fragment = ChatRoomFragment.newInstance(roomName); roomFragments[roomName] = fragment; supportFragmentManager.beginTransaction().add(R.id.content_frame, fragment, roomTag(roomName)).hide(fragment).commitNow() }
        selectedRoomName = roomName; currentTab = ROOM_TAB_START + joinedRooms.indexOf(roomName); applyVisibility(); renderTabs(); notifyCurrentTabSelected()
    }
    fun leaveRoom(roomName: String? = null) {
        val targetRoom = roomName ?: selectedRoomName ?: return; val index = joinedRooms.indexOf(targetRoom); if (index < 0) return
        roomFragments.remove(targetRoom)?.let { supportFragmentManager.beginTransaction().remove(it).commitNow() }; joinedRooms.removeAt(index)
        if (joinedRooms.isEmpty()) { selectedRoomName = null; currentTab = 1 } else { val nextIndex = index.coerceAtMost(joinedRooms.lastIndex); selectedRoomName = joinedRooms[nextIndex]; currentTab = ROOM_TAB_START + nextIndex }
        applyVisibility(); renderTabs(); notifyCurrentTabSelected()
    }
    fun selectTab(index: Int) { if (index >= ROOM_TAB_START && index - ROOM_TAB_START >= joinedRooms.size) return; currentTab = index; selectedRoomName = if (index >= ROOM_TAB_START) joinedRooms[index - ROOM_TAB_START] else null; applyVisibility(); renderTabs(); notifyCurrentTabSelected() }
    fun goToBuddyList() = selectTab(0)
    fun goToRoomsList() = selectTab(1)
    private fun notifyCurrentTabSelected() { when { currentTab == 0 -> buddyFragment.setupScreen(); currentTab == 1 -> roomsFragment.setupScreen(); currentTab >= ROOM_TAB_START -> selectedRoomName?.let { roomFragments[it]?.setupScreen() } } }
    private fun applyVisibility() { val tx: FragmentTransaction = supportFragmentManager.beginTransaction(); if (currentTab == 0) tx.show(buddyFragment) else tx.hide(buddyFragment); if (currentTab == 1) tx.show(roomsFragment) else tx.hide(roomsFragment); roomFragments.forEach { (name, fragment) -> if (currentTab >= ROOM_TAB_START && name == selectedRoomName) tx.show(fragment) else tx.hide(fragment) }; tx.commitNowAllowingStateLoss() }
    private fun renderTabs() { tabStripContainer.removeAllViews(); addIconTab(R.drawable.d_buddylist_icon, 0, "EM"); addIconTab(R.drawable.d_chatrooms, 1, "Rooms"); joinedRooms.forEachIndexed { index, roomName -> addRoomIconTab(ROOM_TAB_START + index, roomName) } }
    private fun addIconTab(icon: Int, index: Int, description: String) { val iv = ImageView(this); val sizePx = (34 * resources.displayMetrics.density).toInt(); iv.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = (4 * resources.displayMetrics.density).toInt() }; iv.setImageResource(icon); iv.setBackgroundResource(R.drawable.tab_bg_selector); iv.isSelected = index == currentTab; iv.contentDescription = description; iv.setPadding(4, 4, 4, 4); iv.setOnClickListener { selectTab(index) }; tabStripContainer.addView(iv) }
    private fun addRoomIconTab(index: Int, roomName: String) { val iv = ImageView(this); val sizePx = (34 * resources.displayMetrics.density).toInt(); iv.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = (4 * resources.displayMetrics.density).toInt() }; iv.setImageResource(R.drawable.d_chatrooms); iv.setBackgroundResource(R.drawable.tab_bg_selector); iv.isSelected = index == currentTab; iv.contentDescription = "Joined room $roomName"; iv.setPadding(4, 4, 4, 4); iv.setOnClickListener { selectTab(index) }; tabStripContainer.addView(iv) }
    private fun roomTag(roomName: String): String = TAG_ROOM_PREFIX + roomName.hashCode()

    companion object {
        const val MODE_EM = "em"; const val MODE_CHAT = "chat"; private const val EXTRA_MODE = "extra_mode"; private const val TAG_BUDDY = "tab_buddy"; private const val TAG_ROOMS = "tab_rooms"; private const val TAG_ROOM_PREFIX = "tab_room_"; private const val ROOM_TAB_START = 2; private const val NOTIFICATION_PERMISSION_REQUEST = 4107
        fun intent(context: Context, mode: String): Intent = Intent(context, ChatHostActivity::class.java).putExtra(EXTRA_MODE, mode)
    }
}
