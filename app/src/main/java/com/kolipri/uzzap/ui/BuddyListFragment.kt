package com.kolipri.uzzap.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.kolipri.uzzap.R
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.BuddyRequest
import com.kolipri.uzzap.data.BuddyRequestClient
import com.kolipri.uzzap.data.PrivateMessageStateClient
import com.kolipri.uzzap.data.PresenceClient
import com.kolipri.uzzap.data.SafetyClient
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.UzzapNotificationManager
import com.kolipri.uzzap.model.BuddyGroup
import com.kolipri.uzzap.model.Presence
import com.kolipri.uzzap.ui.dialogs.J2mePopup
import com.kolipri.uzzap.ui.dialogs.PopupItem

class BuddyListFragment : Fragment(R.layout.fragment_buddy_list) {
    private lateinit var adapter: GroupAdapter
    private lateinit var ivMyStatus: ImageView
    private lateinit var tvUsername: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var monitoring = false
    private var buddyRequestFetchInFlight = false
    private var cachedUnreadCounts: Map<String, Int> = emptyMap()
    private var lastBadgePollMs = 0L
    private val badgePollThrottleMs = 8000L
    private var pendingRequests: List<BuddyRequest> = emptyList()
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val monitor = object : Runnable {
        override fun run() {
            if (!monitoring || !isAdded) return
            PresenceClient.heartbeat(AppState.presence != Presence.INVISIBLE)
            checkUnreadNotifications()
            checkBuddyRequests(showDialog = true)
            adapter.notifyDataSetChanged()
            handler.postDelayed(this, 8_000L)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ivMyStatus = view.findViewById(R.id.iv_my_status)
        tvUsername = view.findViewById(R.id.tv_username)
        tvUsername.text = AppState.username
        refreshStatusIcon()
        view.findViewById<View>(R.id.row_status).setOnClickListener { showStatusMenu() }
        val listView = view.findViewById<ListView>(R.id.list_groups)
        adapter = GroupAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ -> onGroupTapped(position) }
        listView.setOnItemLongClickListener { _, _, position, _ -> showGroupContextMenu(position); true }
    }

    override fun onResume() {
        super.onResume()
        setupScreen()
        requestNotificationPermissionIfNeeded()
        syncBuddiesFromSupabase()
        checkBuddyRequests(showDialog = true)
        monitoring = true
        handler.removeCallbacks(monitor)
        handler.postDelayed(monitor, 8_000L)
    }

    override fun onPause() {
        monitoring = false
        handler.removeCallbacks(monitor)
        PresenceClient.heartbeat(false)
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            setupScreen()
            requestNotificationPermissionIfNeeded()
            syncBuddiesFromSupabase()
            checkBuddyRequests(showDialog = true)
            monitoring = true
            handler.removeCallbacks(monitor)
            handler.postDelayed(monitor, 8_000L)
        } else {
            monitoring = false
            handler.removeCallbacks(monitor)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun setupScreen() {
        val host = activity as? ChatHostActivity ?: return
        host.setScreenTitle(getString(R.string.em_buddy_list_title))
        host.showConnectedBall(true)
        host.setSoftkeys(getString(R.string.softkey_options), getString(R.string.softkey_menu), onLeft = { showOptionsMenu() }, onRight = { showOptionsMenu() })
        if (::tvUsername.isInitialized) tvUsername.text = AppState.username
    }

    fun syncBuddiesFromSupabase() {
        SupabaseClient.fetchBuddies(AppState.username) { grouped ->
            if (!isAdded || grouped == null) return@fetchBuddies
            for (g in AppState.buddyGroups) if (g.key != "all" && g.key != "pending") g.buddies.clear()
            grouped.forEach { (grpName, buddies) ->
                (AppState.buddyGroups.find { it.name.equals(grpName, true) } ?: AppState.buddyGroups.find { it.key == "friends" })?.buddies?.addAll(buddies)
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun checkBuddyRequests(showDialog: Boolean) {
        if (!isAdded || buddyRequestFetchInFlight) return
        val username = AppState.username.trim()
        if (username.isBlank()) return
        buddyRequestFetchInFlight = true
        BuddyRequestClient.fetchPending(username) { requests ->
            buddyRequestFetchInFlight = false
            if (!isAdded || requests == null) return@fetchPending
            val previousIds = pendingRequests.map { it.id }.toSet()
            pendingRequests = requests
            val pendingGroup = AppState.buddyGroups.find { it.key == "pending" }
            pendingGroup?.buddies?.apply {
                clear()
                addAll(requests.map { it.requesterHandle }.distinct())
            }
            adapter.notifyDataSetChanged()
            if (showDialog) requests.firstOrNull { !previousIds.contains(it.id) }?.let { showBuddyRequestDialog(it) }
        }
    }

    private fun showBuddyRequestDialog(request: BuddyRequest) {
        if (!isAdded || isRemoving) return
        AlertDialog.Builder(requireContext())
            .setTitle("Buddy Request")
            .setMessage("${request.requesterHandle} wants to add you as a buddy.")
            .setPositiveButton("Accept") { _, _ -> respondToRequest(request, true) }
            .setNegativeButton("Reject") { _, _ -> respondToRequest(request, false) }
            .setNeutralButton("Later", null)
            .show()
    }

    private fun respondToRequest(request: BuddyRequest, accept: Boolean) {
        BuddyRequestClient.respond(request.id, accept) { ok ->
            if (!isAdded) return@respond
            Toast.makeText(requireContext(), if (ok) if (accept) "${request.requesterHandle} is now your buddy." else "Buddy request rejected." else "Could not update buddy request.", Toast.LENGTH_SHORT).show()
            if (ok) {
                pendingRequests = pendingRequests.filterNot { it.id == request.id }
                syncBuddiesFromSupabase()
                checkBuddyRequests(showDialog = false)
            }
        }
    }

    private fun checkUnreadNotifications() {
        PrivateMessageStateClient.fetchUnread(AppState.username) { state ->
            if (isAdded && state != null && state.count > 0) UzzapNotificationManager.notifyPrivateMessages(requireContext(), state.count, state.senders)
        }
    }

    private fun refreshStatusIcon() {
        ivMyStatus.setImageResource(when (AppState.presence) {
            Presence.AVAILABLE -> R.drawable.d_online
            Presence.NOT_AVAILABLE -> R.drawable.d_notavailable
            Presence.INVISIBLE -> R.drawable.d_offline
        })
        AppState.syncProfileToSupabase()
        PresenceClient.heartbeat(AppState.presence != Presence.INVISIBLE)
    }

    private fun onGroupTapped(position: Int) {
        val group = AppState.buddyGroups[position]
        if (!group.expandable) return
        group.expanded = !group.expanded
        adapter.notifyDataSetChanged()
    }

    private fun showGroupContextMenu(position: Int) {
        val group = AppState.buddyGroups[position]
        J2mePopup.show(requireContext(), listOf(PopupItem(getString(R.string.menu_rename_group)), PopupItem(getString(R.string.menu_return_defaults)))) { index ->
            when (index) {
                0 -> showRenameDialog(group)
                1 -> {
                    if (AppState.buddyGroups.size >= 5) {
                        AppState.buddyGroups[1].name = "Most Frequent"
                        AppState.buddyGroups[2].name = "My Friends"
                        AppState.buddyGroups[3].name = "Other Contacts"
                        AppState.buddyGroups[4].name = "Pending Buddies"
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun showRenameDialog(group: BuddyGroup) {
        UzzapDialogs.form(requireContext(), iconAsset = "anim-icons/edit_write.gif", title = getString(R.string.menu_rename_group), fields = listOf(UzzapDialogs.FieldSpec("Group name", group.name)), positive = "OK", onPositive = { f ->
            val name = f[0].text.toString().trim()
            if (name.isEmpty()) { f[0].error = "Enter a group name"; return@form false }
            group.name = name; adapter.notifyDataSetChanged(); true
        })
    }

    private fun showOptionsMenu() {
        J2mePopup.show(requireContext(), listOf(PopupItem(getString(R.string.menu_new_group)), PopupItem("Add Buddy"), PopupItem("Sync Buddies"), PopupItem("Test Notification & Alert"), PopupItem(getString(R.string.menu_clear_history)), PopupItem(getString(R.string.menu_uzzap_help)))) { index ->
            when (index) {
                0 -> showNewGroupDialog()
                1 -> showAddBuddyDialog()
                2 -> syncBuddiesFromSupabase()
                3 -> { UzzapNotificationManager.sendTestNotification(requireContext(), "uzzapbot", "Kamusta! Uzzap notification test."); Toast.makeText(requireContext(), "Test notification triggered!", Toast.LENGTH_SHORT).show() }
                4 -> { AppState.roomMessages.clear(); Toast.makeText(requireContext(), "Local message cache cleared.", Toast.LENGTH_SHORT).show() }
                5 -> startActivity(SimpleTextActivity.intentForAsset(requireContext(), "Help", "help.txt"))
            }
        }
    }

    private fun showAddBuddyDialog() {
        UzzapDialogs.form(requireContext(), iconAsset = "anim-icons/im_profile.gif", title = "Add Buddy", fields = listOf(UzzapDialogs.FieldSpec("Enter buddy nickname/handle")), positive = "Send Request", onPositive = { f ->
            val handle = f[0].text.toString().trim()
            if (handle.isBlank()) { f[0].error = "Enter a buddy nickname"; return@form false }
            if (handle.equals(AppState.username, ignoreCase = true) || handle.equals(AppState.nickname, ignoreCase = true)) { f[0].error = "You cannot add yourself as a buddy."; return@form false }
            BuddyRequestClient.send(AppState.username, handle) { success, error ->
                if (!isAdded) return@send
                Toast.makeText(requireContext(), if (success) "Buddy request sent to '$handle'." else (error ?: "Buddy request failed."), Toast.LENGTH_SHORT).show()
                if (success) checkBuddyRequests(false)
            }
            true
        })
    }

    private fun showNewGroupDialog() {
        UzzapDialogs.form(requireContext(), iconAsset = "anim-icons/edit_write.gif", title = getString(R.string.menu_new_group), fields = listOf(UzzapDialogs.FieldSpec("Group name")), positive = "Create", onPositive = { f ->
            val name = f[0].text.toString().trim()
            if (name.isEmpty()) { f[0].error = "Enter a group name"; return@form false }
            if (AppState.buddyGroups.any { it.name.equals(name, true) }) { f[0].error = "That group already exists."; return@form false }
            AppState.buddyGroups.add(BuddyGroup(name.lowercase(), name)); adapter.notifyDataSetChanged(); true
        })
    }

    private fun showStatusMenu() {
        J2mePopup.show(requireContext(), listOf(PopupItem(getString(R.string.status_message_ellipsis), R.drawable.d_statusmessage), PopupItem(getString(R.string.status_available), R.drawable.d_online), PopupItem(getString(R.string.status_not_available), R.drawable.d_notavailable), PopupItem(getString(R.string.status_invisible), R.drawable.d_offline), PopupItem(getString(R.string.settings_ellipsis), R.drawable.d_settings))) { index ->
            when (index) {
                0 -> { val input = EditText(requireContext()).apply { setText(AppState.statusMessage) }; AlertDialog.Builder(requireContext()).setTitle(getString(R.string.status_message_ellipsis)).setView(input).setPositiveButton("OK") { _, _ -> AppState.statusMessage = input.text.toString(); AppState.syncProfileToSupabase() }.setNegativeButton(getString(R.string.softkey_cancel), null).show() }
                1 -> { AppState.presence = Presence.AVAILABLE; refreshStatusIcon() }
                2 -> { AppState.presence = Presence.NOT_AVAILABLE; refreshStatusIcon() }
                3 -> { AppState.presence = Presence.INVISIBLE; refreshStatusIcon() }
                4 -> startActivity(LegacySettingsActivity.intent(requireContext(), LegacySettingsActivity.MODE_SETTINGS))
            }
        }
    }

    private fun showBuddyOptions(buddy: String, group: BuddyGroup) {
        if (group.key == "pending") {
            pendingRequests.firstOrNull { it.requesterHandle.equals(buddy, true) }?.let { showBuddyRequestDialog(it) }
            return
        }
        val ctx = context ?: return
        J2mePopup.show(ctx, listOf(PopupItem("Chat with $buddy"), PopupItem("View Profile"), PopupItem("Block User"), PopupItem("Report User"), PopupItem("Delete Buddy"))) { index ->
            when (index) {
                0 -> startActivity(PrivateChatActivity.intent(ctx, buddy))
                1 -> PresenceClient.fetch(buddy) { p -> if (isAdded) UzzapDialogs.show(requireContext(), iconAsset = "anim-icons/im_profile.gif", title = buddy, message = "Nickname: $buddy\nStatus: ${p?.status ?: "Unknown"}\nPresence: ${if (p?.isOnline == true) "Online" else "Offline"}", positive = "Close") }
                2 -> SafetyClient.block(buddy) { ok -> if (isAdded) { if (ok) group.buddies.remove(buddy); adapter.notifyDataSetChanged(); Toast.makeText(ctx, if (ok) "$buddy blocked" else "Could not block $buddy", Toast.LENGTH_SHORT).show() } }
                3 -> showReportDialog(buddy)
                4 -> UzzapDialogs.show(ctx, iconAsset = "anim-icons/delete_trash.gif", title = "Delete Buddy", message = "Remove '$buddy' from your buddies?", positive = "Remove", onPositive = {
                    SupabaseClient.deleteBuddy(AppState.username, buddy) { success -> if (isAdded) { if (success) { group.buddies.remove(buddy); adapter.notifyDataSetChanged(); Toast.makeText(ctx, "Removed '$buddy'", Toast.LENGTH_SHORT).show() } else Toast.makeText(ctx, "Could not remove '$buddy'", Toast.LENGTH_SHORT).show() } }
                })
            }
        }
    }

    private fun showReportDialog(buddy: String) {
        UzzapDialogs.show(requireContext(), iconAsset = "anim-icons/privacy_lock.gif", title = "Report User",
            message = "Report \"$buddy\" for inappropriate behavior? Moderators will review this report.",
            inputHint = "Add details (optional)...", positive = "Report", onPositive = { details ->
                SafetyClient.report(buddy, "Other", details.ifBlank { "Reported from buddy list" }) { ok ->
                    if (isAdded) Toast.makeText(requireContext(), if (ok) "Report submitted" else "Report failed", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private inner class GroupAdapter : ArrayAdapter<BuddyGroup>(requireContext(), 0, AppState.buddyGroups) {
        override fun getCount(): Int = AppState.buddyGroups.size
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_buddy_group, parent, false)
            val group = AppState.buddyGroups[position]
            val icon = view.findViewById<ImageView>(R.id.iv_group_icon)
            val name = view.findViewById<TextView>(R.id.tv_group_name)
            val searchRow = view.findViewById<View>(R.id.search_row)
            val container = view.findViewById<LinearLayout>(R.id.container_buddies)
            val empty = view.findViewById<TextView>(R.id.tv_empty)
            val buddies = if (group.key == "all") AppState.buddyGroups.filter { it.key != "all" }.flatMap { it.buddies }.distinct() else group.buddies
            name.text = if (group.key == "all") "${group.name} (${buddies.size})" else if (group.key == "pending") "${group.name} (${buddies.size})" else group.name
            icon.setImageResource(if (!group.expandable || group.expanded) R.drawable.d_minus else R.drawable.d_plus)
            val show = (group.key == "all" && group.expanded) || (group.expandable && group.expanded)
            searchRow.visibility = if (show && group.key == "friends") View.VISIBLE else View.GONE
            container.removeAllViews()
            if (show && buddies.isNotEmpty()) {
                container.visibility = View.VISIBLE; empty.visibility = View.GONE
                for (buddy in buddies) {
                    val row = LayoutInflater.from(context).inflate(R.layout.item_buddy_status_row, container, false)
                    val statusIcon = row.findViewById<ImageView>(R.id.iv_status_icon)
                    val statusName = row.findViewById<TextView>(R.id.tv_status_username)
                    val unread = row.findViewById<TextView>(R.id.tv_unread_count)
                    statusName.text = if (group.key == "pending") "$buddy • Request" else buddy
                    statusIcon.setImageResource(if (group.key == "pending") R.drawable.d_buddylist_icon else R.drawable.d_offline)
                    val cachedCount = cachedUnreadCounts[buddy.lowercase()] ?: 0
                    unread.text = if (group.key == "pending") "!" else if (cachedCount > 99) "99+" else cachedCount.toString()
                    unread.visibility = if (group.key == "pending" || cachedCount > 0) View.VISIBLE else View.GONE
                    row.setOnClickListener { showBuddyOptions(buddy, group) }
                    container.addView(row)
                }
                if (group.key != "pending" && System.currentTimeMillis() - lastBadgePollMs >= badgePollThrottleMs) {
                    lastBadgePollMs = System.currentTimeMillis()
                    PrivateMessageStateClient.fetchUnread(AppState.username) { unreadState ->
                        if (!isAdded || unreadState == null || !container.isAttachedToWindow) return@fetchUnread
                        cachedUnreadCounts = unreadState.countsBySender
                        for (i in 0 until container.childCount) {
                            val row = container.getChildAt(i); val n = row.findViewById<TextView>(R.id.tv_status_username); val badge = row.findViewById<TextView>(R.id.tv_unread_count); val count = unreadState.countsBySender[n.text.toString().lowercase()] ?: 0
                            badge.text = if (count > 99) "99+" else count.toString(); badge.visibility = if (count > 0) View.VISIBLE else View.GONE
                        }
                    }
                    PresenceClient.fetchMultiple(buddies) { presenceMap ->
                        if (!isAdded || !container.isAttachedToWindow) return@fetchMultiple
                        for (i in 0 until container.childCount) {
                            val r = container.getChildAt(i); val n = r.findViewById<TextView>(R.id.tv_status_username).text.toString(); val p = presenceMap[n.lowercase()]; r.findViewById<ImageView>(R.id.iv_status_icon).setImageResource(if (p?.isOnline == true) R.drawable.d_online else R.drawable.d_offline)
                        }
                    }
                }
            } else if (show) { container.visibility = View.GONE; empty.visibility = View.VISIBLE } else { container.visibility = View.GONE; empty.visibility = View.GONE }
            return view
        }
    }
}