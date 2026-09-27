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
import com.kolipri.uzzap.data.AppDiagnostics
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.ModeratorRoleClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.UnifiedRoomClient
import com.kolipri.uzzap.model.ChatRoomCategory
import com.kolipri.uzzap.model.ChatRoomInfo
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem

class ChatRoomsFragment : Fragment(R.layout.fragment_chat_rooms) {
    private var loaded = false
    private var selectedCategory: ChatRoomCategory? = null
    private var loadInFlight = false
    private var lastSuccessfulLoadAt = 0L
    private lateinit var tvLoading: TextView
    private lateinit var contentContainer: View
    private lateinit var tvHeader: TextView
    private lateinit var listView: ListView
    private val host get() = requireActivity() as ChatHostActivity

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvLoading = view.findViewById(R.id.tv_loading)
        contentContainer = view.findViewById(R.id.content_container)
        tvHeader = view.findViewById(R.id.tv_header)
        listView = view.findViewById(R.id.list_rooms)
        refreshFromSupabase(initial = !loaded)
    }

    override fun onResume() {
        super.onResume()
        if (::listView.isInitialized) refreshFromSupabase()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::listView.isInitialized) refreshFromSupabase()
    }

    fun setupScreen() {
        host.setScreenTitle(getString(R.string.chat_rooms_title))
        host.showConnectedBall(true)
        refreshSoftkeys()
    }

    fun onBackPressed(): Boolean {
        if (selectedCategory != null) {
            showCategories()
            return true
        }
        return false
    }

    private fun refreshSoftkeys() {
        if (selectedCategory == null) {
            host.setSoftkeys(getString(R.string.softkey_options), getString(R.string.softkey_menu), onLeft = { showOptionsMenu() }, onRight = { showOptionsMenu() })
        } else {
            host.setSoftkeys(getString(R.string.softkey_options), getString(R.string.softkey_back), onLeft = { showOptionsMenu() }, onRight = { showCategories() })
        }
    }

    private fun refreshFromSupabase(initial: Boolean = false) {
        val now = System.currentTimeMillis()
        if (loadInFlight) return
        if (!initial && loaded && now - lastSuccessfulLoadAt < 1_500L) return
        loadInFlight = true
        if (initial) {
            tvLoading.visibility = View.VISIBLE
            contentContainer.visibility = View.GONE
        }
        AppDiagnostics.started("RoomList.load", "STARTED")
        ModeratorRoleClient.refresh {
            AppDiagnostics.started("RoomList.fetchCategories", "STARTED")
            UnifiedRoomClient.fetchCategoriesAndRooms { remoteCats ->
                if (!isAdded) {
                    loadInFlight = false
                    return@fetchCategoriesAndRooms
                }
                if (remoteCats == null) {
                    loadInFlight = false
                    AppDiagnostics.failed("RoomList.fetchCategories", "Could not load chat categories/rooms")
                    AppDiagnostics.failed("RoomList.load", "Could not load Philippine chat rooms")
                    tvLoading.visibility = View.GONE
                    contentContainer.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Could not load Philippine chat rooms.", Toast.LENGTH_SHORT).show()
                    return@fetchCategoriesAndRooms
                }
                AppDiagnostics.success("RoomList.fetchCategories", "categories=${remoteCats.size} rooms=${remoteCats.sumOf { it.rooms.size }}")
                AppDiagnostics.started("RoomList.liveCounts", "STARTED")
                UnifiedRoomClient.liveCounts { counts ->
                    if (!isAdded) {
                        loadInFlight = false
                        return@liveCounts
                    }
                    if (counts == null) AppDiagnostics.failed("RoomList.liveCounts", "No live room counts returned")
                    else AppDiagnostics.success("RoomList.liveCounts", "rooms=${counts.size}")
                    val live = counts ?: emptyMap()
                    val updated = remoteCats.map { category ->
                        val rooms = category.rooms.map { room ->
                            val remote = live[room.name] ?: 0
                            val isJoined = AppState.isRoomJoined(room.name)
                            val userCount = if (isJoined && remote < 1) 1 else remote
                            val cap = if (room.capacity > 0) room.capacity else 20
                            ChatRoomInfo(room.name, userCount, cap)
                        }
                        ChatRoomCategory(category.name, rooms.sumOf { it.userCount }, rooms.sumOf { it.capacity }, rooms)
                    }.toMutableList()
                    if (AppState.isModerator) {
                        val modRemote = live["Moderator Room"] ?: 0
                        val isModJoined = AppState.isRoomJoined("Moderator Room")
                        val modCount = if (isModJoined && modRemote < 1) 1 else modRemote
                        val moderatorRoom = ChatRoomInfo("Moderator Room", modCount, 50)
                        updated.add(ChatRoomCategory("Moderation", moderatorRoom.userCount, moderatorRoom.capacity, listOf(moderatorRoom)))
                    }
                    AppState.categories = updated
                    loaded = true
                    lastSuccessfulLoadAt = System.currentTimeMillis()
                    loadInFlight = false
                    tvLoading.visibility = View.GONE
                    contentContainer.visibility = View.VISIBLE
                    val selectedName = selectedCategory?.name
                    selectedCategory = selectedName?.let { n -> updated.firstOrNull { it.name == n } }
                    if (selectedCategory != null) showRoomsFor(selectedCategory!!) else showCategories()
                    AppDiagnostics.success("RoomList.load", "regions=${updated.size} rooms=${updated.sumOf { it.rooms.size }}")
                }
            }
        }
    }

    private fun showCategories() {
        selectedCategory = null
        tvHeader.text = "Regions (1/1)"
        listView.adapter = object : ArrayAdapter<ChatRoomCategory>(requireContext(), 0, AppState.categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_room_row, parent, false)
                val c = AppState.categories[position]
                val moderator = c.name == "Moderation"
                val liveUserCount = c.rooms.sumOf { r -> if (AppState.isRoomJoined(r.name)) maxOf(r.userCount, 1) else r.userCount }
                val cap = if (c.capacity > 0) c.capacity else c.rooms.sumOf { if (it.capacity > 0) it.capacity else 20 }
                v.findViewById<ImageView>(R.id.iv_room_icon).setImageResource(R.drawable.d_chatroom_category)
                v.findViewById<TextView>(R.id.tv_room_label).text = "${c.name} ($liveUserCount/$cap)"
                return v
            }
        }
        listView.setOnItemClickListener { _, _, position, _ -> showRoomsFor(AppState.categories[position]) }
        refreshSoftkeys()
    }

    private fun showRoomsFor(category: ChatRoomCategory) {
        selectedCategory = category
        tvHeader.text = "${category.name} (1/1)"
        listView.adapter = object : ArrayAdapter<Any>(requireContext(), 0, category.rooms) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_room_row, parent, false)
                val r = category.rooms[position]
                val isJoined = AppState.isRoomJoined(r.name)
                val count = if (isJoined) maxOf(r.userCount, 1) else r.userCount
                val cap = if (r.capacity > 0) r.capacity else 20
                v.findViewById<ImageView>(R.id.iv_room_icon).setImageResource(R.drawable.d_chat_icon)
                v.findViewById<TextView>(R.id.tv_room_label).text = "${r.name} ($count/$cap)"
                return v
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            val clickedRoom = category.rooms[position]
            AppDiagnostics.info(requireContext(), "RoomList.openRoom", "room=${clickedRoom.name}")
            host.openRoom(clickedRoom.name)
        }
        refreshSoftkeys()
    }

    private fun showOptionsMenu() {
        // Exact legacy Chat Rooms list options from com.kolipri.kalypte.bm.
        val labels = if (selectedCategory == null) {
            listOf("Select Category", "Refresh Category List", "Start Buddy Group Chat", "Close Tab")
        } else {
            listOf("Join Room", "Refresh Room List", "Start Buddy Group Chat", "Close Tab")
        }
        J2mePopup.show(requireContext(), labels.map { PopupItem(it) }) { index ->
            when (index) {
                0 -> if (selectedCategory == null) showCategories()
                    else selectedCategory?.rooms?.firstOrNull()?.let { host.openRoom(it.name) }
                1 -> refreshFromSupabase(initial = false)
                2 -> {
                    val room = selectedCategory?.rooms?.firstOrNull()
                    if (room != null) startActivity(UzzapParityActivity.intent(requireContext(), UzzapParityActivity.MODE_ROOM_ACTIONS, room.name))
                    else Toast.makeText(requireContext(), "Select a room first.", Toast.LENGTH_SHORT).show()
                }
                3 -> requireActivity().finish()
            }
        }
    }

    private fun showSearch() {
        UzzapDialogs.form(requireContext(), iconAsset = "anim-icons/menu_backend.gif", title = "Search Rooms", fields = listOf(UzzapDialogs.FieldSpec("Province or room name")), positive = "Search", onPositive = { f ->
            val q = f[0].text.toString().trim().lowercase()
            if (q.isEmpty()) { f[0].error = "Enter a province or room name"; return@form false }
            val matches = AppState.categories.flatMap { it.rooms }.filter { it.name.lowercase().contains(q) }
            if (matches.isEmpty()) Toast.makeText(requireContext(), "No rooms found.", Toast.LENGTH_SHORT).show()
            else UzzapDialogs.items(requireContext(), title = "Rooms (${matches.size})", items = matches.map { it.name }.toTypedArray(), onPick = { which -> host.openRoom(matches[which].name) }, negative = null)
            true
        })
    }
}
