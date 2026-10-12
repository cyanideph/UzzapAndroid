package com.kolipri.uzzap.data

import com.kolipri.uzzap.model.BuddyGroup
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.ChatRoomCategory
import com.kolipri.uzzap.model.Presence

/** Process-local UI/session state. Supabase is the authoritative backend. */
object AppState {
    var username: String = "unknown-user"
    var nickname: String = ""
    var email: String = ""
    var authToken: String = ""
    var presence: Presence = Presence.INVISIBLE
    var statusMessage: String = ""
    /** DiceBear avatar selection encoded as dicebear:style:seed. */
    var avatar: String = ""
    var themeName: String = "default"
    var activeChatBuddy: String? = null
    var role: String = "user"
    val isModerator: Boolean get() = role.equals("moderator", ignoreCase = true) || role.equals("admin", ignoreCase = true)

    val buddyGroups: MutableList<BuddyGroup> = mutableListOf(
        BuddyGroup("all", "All Buddies", expandable = false, expanded = true),
        BuddyGroup("frequent", "Most Frequent", expanded = true),
        BuddyGroup("friends", "My Friends", expanded = true),
        BuddyGroup("other", "Other Contacts", expanded = true),
        BuddyGroup("pending", "Pending Buddies", expanded = true)
    )

    // Never seed fake rooms here. The authoritative room hierarchy comes from Supabase:
    // Philippine region categories -> province chatrooms.
    var categories: List<ChatRoomCategory> = emptyList()
    val joinedRooms: MutableSet<String> = mutableSetOf()
    fun isRoomJoined(roomName: String): Boolean = joinedRooms.contains(roomName)
    val roomMessages: MutableMap<String, MutableList<ChatMessage>> = mutableMapOf()

    fun messagesFor(room: String): MutableList<ChatMessage> = roomMessages.getOrPut(room) { mutableListOf() }

    fun addOrUpdateMessages(room: String, incoming: List<ChatMessage>): Int {
        val list = messagesFor(room)
        var addedCount = 0
        for (msg in incoming) {
            val exists = if (msg.id > 0) list.any { it.id == msg.id }
            else list.any { it.sender == msg.sender && it.body == msg.body && it.isSystem == msg.isSystem }
            if (!exists) { list.add(msg); addedCount++ }
        }
        return addedCount
    }

    fun syncProfileToSupabase() {
        if (SupabaseClient.userAccessToken.isNullOrBlank()) return
        // Keep the edited nickname in the encrypted local session too, otherwise a
        // later session restore can silently replace the markup with the old value.
        SessionStore.updateProfile(nickname, email, avatar.ifBlank { AvatarManager.encode("pixel-art", username) })
        SupabaseClient.upsertProfile(
            username = username,
            nickname = nickname,
            email = email.ifBlank { null },
            status = presence.label,
            statusMessage = statusMessage,
            avatar = avatar.ifBlank { AvatarManager.encode("pixel-art", username) },
            isOnline = presence != Presence.INVISIBLE
        )
    }

    fun resetSession() {
        presence = Presence.INVISIBLE
        syncProfileToSupabase()
        roomMessages.clear()
        joinedRooms.clear()
        buddyGroups.forEach { it.buddies.clear() }
        categories = emptyList()
        username = "unknown-user"
        nickname = ""
        email = ""
        authToken = ""
        role = "user"
        avatar = ""
        SupabaseClient.signOut()
    }
}