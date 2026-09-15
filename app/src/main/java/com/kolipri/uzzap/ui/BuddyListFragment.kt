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
    private var cachedUnreadCounts: Map<String, Int> = emptyMap()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted/denied handled gracefully */ }
    private val monitor = object : Runnable {
        override fun run() {
            if (!monitoring || !isAdded) return
            PresenceClient.heartbeat(AppState.presence != Presence.INVISIBLE)
            checkUnreadNotifications()
            adapter.notifyDataSetChanged()
            handler.postDelayed(this, 10_000L)
        }
    }
    private val host get() = requireActivity() as ChatHostActivity

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
        monitoring = true
        handler.removeCallbacks(monitor)
        handler.post(monitor)
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
            monitoring = true
            handler.removeCallbacks(monitor)
            handler.post(monitor)
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
        val hostActivity = activity as? ChatHostActivity ?: return
        hostActivity.setScreenTitle(getString(R.string.em_buddy_list_title))
        hostActivity.showConnectedBall(true)
        hostActivity.setSoftkeys(
            getString(R.string.softkey_options),
            getString(R.string.softkey_menu),
            onLeft = { showOptionsMenu() },
            onRight = { showOptionsMenu() }
        )
    }

    fun syncBuddiesFromSupabase() {
        SupabaseClient.fetchBuddies(AppState.nickname) { grouped ->
            if (!isAdded || grouped == null) return@fetchBuddies
            for (g in AppState.buddyGroups) if (g.key != "all") g.buddies.clear()
            grouped.forEach { (grpName, buddies) ->
                (AppState.buddyGroups.find { it.name.equals(grpName, true) }
                    ?: AppState.buddyGroups.find { it.key == "friends" })?.buddies?.addAll(buddies)
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun checkUnreadNotifications() {
        PrivateMessageStateClient.fetchUnread(AppState.username) { state ->
            if (isAdded && state != null) {
                UzzapNotificationManager.notifyPrivateMessages(requireContext(), state.count, state.senders)
            }
        }
    }

    private fun refreshStatusIcon() {
        ivMyStatus.setImageResource(
            when (AppState.presence) {
                Presence.AVAILABLE -> R.drawable.d_online
                Presence.NOT_AVAILABLE -> R.drawable.d_notavailable
                Presence.INVISIBLE -> R.drawable.d_offline
            }
        )
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
        J2mePopup.show(
            requireContext(),
            listOf(PopupItem(getString(R.string.menu_rename_group)), PopupItem(getString(R.string.menu_return_defaults)))
        ) { index ->
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
        val input = EditText(requireContext()).apply { setText(group.name) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.menu_rename_group))
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    group.name = it
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(getString(R.string.softkey_cancel), null)
            .show()
    }

    private fun showOptionsMenu() {
        J2mePopup.show(
            requireContext(),
            listOf(
                PopupItem(getString(R.string.menu_new_group)),
                PopupItem("Add Buddy"),
                PopupItem("Sync Buddies"),
                PopupItem("Test Notification & Alert"),
                PopupItem(getString(R.string.menu_clear_history)),
                PopupItem(getString(R.string.menu_uzzap_help))
            )
        ) { index ->
            when (index) {
                0 -> showNewGroupDialog()
                1 -> showAddBuddyDialog()
                2 -> syncBuddiesFromSupabase()
                3 -> {
                    UzzapNotificationManager.sendTestNotification(
                        requireContext(),
                        sender = "uzzapbot",
                        messageText = "Kamusta cy! New Uzzap message with alert sound :)"
                    )
                    Toast.makeText(requireContext(), "Test message & alert sound triggered!", Toast.LENGTH_SHORT).show()
                }
                4 -> confirmClearHistory()
                5 -> startActivity(SimpleTextActivity.intentForAsset(requireContext(), "Help", "help.txt"))
            }
        }
    }

    private fun showAddBuddyDialog() {
        val input = EditText(requireContext()).apply { hint = "Enter buddy nickname/handle" }
        AlertDialog.Builder(requireContext())
            .setTitle("Add Buddy")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val handle = input.text.toString().trim()
                if (handle.isBlank()) return@setPositiveButton
                if (handle.equals(AppState.nickname, ignoreCase = true)) {
                    Toast.makeText(requireContext(), "You cannot add yourself as a buddy.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val friendsGroup = AppState.buddyGroups.find { it.key == "friends" } ?: AppState.buddyGroups.getOrNull(2)
                if (friendsGroup != null && !friendsGroup.buddies.contains(handle)) friendsGroup.buddies.add(handle)
                SupabaseClient.addBuddy(AppState.nickname, handle, friendsGroup?.name ?: "My Friends") { success ->
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            if (success) "Buddy '$handle' added." else "Buddy '$handle' could not be saved.",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (success) syncBuddiesFromSupabase() else adapter.notifyDataSetChanged()
                    }
                }
            }
            .setNegativeButton(getString(R.string.softkey_cancel), null)
            .show()
    }

    private fun showNewGroupDialog() {
        val input = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.menu_new_group))
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { name ->
                    if (AppState.buddyGroups.any { it.name.equals(name, ignoreCase = true) }) {
                        Toast.makeText(requireContext(), "That group already exists.", Toast.LENGTH_SHORT).show()
                    } else {
                        AppState.buddyGroups.add(BuddyGroup(name.lowercase(), name))
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            .setNegativeButton(getString(R.string.softkey_cancel), null)
            .show()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear local history")
            .setMessage("This only clears locally cached room messages on this device. Messages already stored on the server will not be deleted.")
            .setPositiveButton(getString(R.string.softkey_yes)) { _, _ ->
                AppState.roomMessages.clear()
                Toast.makeText(requireContext(), "Local message cache cleared.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.softkey_no), null)
            .show()
    }

    private fun showStatusMenu() {
        J2mePopup.show(
            requireContext(),
            listOf(
                PopupItem(getString(R.string.status_message_ellipsis), R.drawable.d_statusmessage),
                PopupItem(getString(R.string.status_available), R.drawable.d_online),
                PopupItem(getString(R.string.status_not_available), R.drawable.d_notavailable),
                PopupItem(getString(R.string.status_invisible), R.drawable.d_offline),
                PopupItem(getString(R.string.settings_ellipsis), R.drawable.d_settings)
            )
        ) { index ->
            when (index) {
                0 -> showStatusMessageDialog()
                1 -> { AppState.presence = Presence.AVAILABLE; refreshStatusIcon() }
                2 -> { AppState.presence = Presence.NOT_AVAILABLE; refreshStatusIcon() }
                3 -> { AppState.presence = Presence.INVISIBLE; refreshStatusIcon() }
                4 -> startActivity(LegacySettingsActivity.intent(requireContext(), LegacySettingsActivity.MODE_SETTINGS))
            }
        }
    }

    private fun showStatusMessageDialog() {
        val input = EditText(requireContext()).apply { setText(AppState.statusMessage) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.status_message_ellipsis))
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                AppState.statusMessage = input.text.toString()
                AppState.syncProfileToSupabase()
            }
            .setNegativeButton(getString(R.string.softkey_cancel), null)
            .show()
    }

    private fun showBuddyOptions(buddy: String, group: BuddyGroup) {
        val ctx = context ?: return
        J2mePopup.show(
            ctx,
            listOf(PopupItem("Chat with $buddy"), PopupItem("View Profile"), PopupItem("Block User"), PopupItem("Report User"), PopupItem("Delete Buddy"))
        ) { index ->
            when (index) {
                0 -> startActivity(PrivateChatActivity.intent(ctx, buddy))
                1 -> showProfile(buddy)
                2 -> SafetyClient.block(buddy) { ok ->
                    if (isAdded) {
                        if (ok) {
                            group.buddies.remove(buddy)
                            adapter.notifyDataSetChanged()
                        }
                        Toast.makeText(ctx, if (ok) "$buddy blocked" else "Could not block $buddy", Toast.LENGTH_SHORT).show()
                    }
                }
                3 -> showReportDialog(buddy)
                4 -> {
                    AlertDialog.Builder(ctx)
                        .setTitle("Delete Buddy")
                        .setMessage("Remove '$buddy' from your buddies?")
                        .setPositiveButton(getString(R.string.softkey_yes)) { _, _ ->
                            group.buddies.remove(buddy)
                            adapter.notifyDataSetChanged()
                            SupabaseClient.deleteBuddy(AppState.nickname, buddy) { success ->
                                if (isAdded) {
                                    if (success) {
                                        Toast.makeText(ctx, "Removed '$buddy'", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ctx, "Could not delete '$buddy' from server", Toast.LENGTH_SHORT).show()
                                        syncBuddiesFromSupabase()
                                    }
                                }
                            }
                        }
                        .setNegativeButton(getString(R.string.softkey_no), null)
                        .show()
                }
            }
        }
    }

    private fun showProfile(buddy: String) {
        PresenceClient.fetch(buddy) { p ->
            if (isAdded) {
                AlertDialog.Builder(requireContext())
                    .setTitle(buddy)
                    .setMessage("Nickname: $buddy\nStatus: ${p?.status ?: "Unknown"}\nPresence: ${if (p?.isOnline == true) "Online" else "Offline"}")
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }

    private fun showReportDialog(buddy: String) {
        val reasons = arrayOf("Spam", "Harassment", "Impersonation", "Other")
        AlertDialog.Builder(requireContext())
            .setTitle("Report $buddy")
            .setItems(reasons) { _, which ->
                SafetyClient.report(buddy, reasons[which], "Reported from buddy list") { ok ->
                    if (isAdded) Toast.makeText(requireContext(), if (ok) "Report submitted" else "Report failed", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
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
            name.text = if (group.key == "all") "${group.name} (${buddies.size})" else group.name
            icon.setImageResource(if (!group.expandable || group.expanded) R.drawable.d_minus else R.drawable.d_plus)
            val show = (group.key == "all" && group.expanded) || (group.expandable && group.expanded)
            searchRow.visibility = if (show && group.key == "friends") View.VISIBLE else View.GONE
            container.removeAllViews()
            if (show && buddies.isNotEmpty()) {
                container.visibility = View.VISIBLE
                empty.visibility = View.GONE
                for (buddy in buddies) {
                    val row = LayoutInflater.from(context).inflate(R.layout.item_buddy_status_row, container, false)
                    val statusIcon = row.findViewById<ImageView>(R.id.iv_status_icon)
                    val statusName = row.findViewById<TextView>(R.id.tv_status_username)
                    val unread = row.findViewById<TextView>(R.id.tv_unread_count)
                    statusName.text = buddy
                    statusIcon.setImageResource(R.drawable.d_offline)
                    val cachedCount = cachedUnreadCounts[buddy.lowercase()] ?: 0
                    unread.text = if (cachedCount > 99) "99+" else cachedCount.toString()
                    unread.visibility = if (cachedCount > 0) View.VISIBLE else View.GONE
                    row.setOnClickListener { showBuddyOptions(buddy, group) }
                    container.addView(row)
                }
                PrivateMessageStateClient.fetchUnread(AppState.username) { unreadState ->
                    if (!isAdded || unreadState == null) return@fetchUnread
                    cachedUnreadCounts = unreadState.countsBySender
                    if (!container.isAttachedToWindow) return@fetchUnread
                    for (i in 0 until container.childCount) {
                        val row = container.getChildAt(i)
                        val nameView = row.findViewById<TextView>(R.id.tv_status_username)
                        val badge = row.findViewById<TextView>(R.id.tv_unread_count)
                        val count = unreadState.countsBySender[nameView.text.toString().lowercase()] ?: 0
                        badge.text = if (count > 99) "99+" else count.toString()
                        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
                    }
                }
                PresenceClient.fetchMultiple(buddies) { presenceMap ->
                    if (!isAdded || !container.isAttachedToWindow) return@fetchMultiple
                    for (i in 0 until container.childCount) {
                        val r = container.getChildAt(i)
                        val name = r.findViewById<TextView>(R.id.tv_status_username).text.toString()
                        val icon = r.findViewById<ImageView>(R.id.iv_status_icon)
                        val p = presenceMap[name.lowercase()]
                        icon.setImageResource(if (p?.isOnline == true) R.drawable.d_online else R.drawable.d_offline)
                    }
                }
            } else if (show) {
                container.visibility = View.GONE
                empty.visibility = View.VISIBLE
            } else {
                container.visibility = View.GONE
                empty.visibility = View.GONE
            }
            return view
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 4901
    }
}
