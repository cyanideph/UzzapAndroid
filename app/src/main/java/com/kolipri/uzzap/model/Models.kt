package com.kolipri.uzzap.model

/** Presence status the user can set from the EM status menu. */
enum class Presence(val label: String) {
    AVAILABLE("Available"),
    NOT_AVAILABLE("Not Available"),
    INVISIBLE("Invisible")
}

data class BuddyGroup(
    val key: String,
    var name: String,
    val expandable: Boolean = true,
    var expanded: Boolean = false,
    val buddies: MutableList<String> = mutableListOf()
)

data class ChatRoomCategory(
    val name: String,
    val userCount: Int,
    val capacity: Int,
    val rooms: List<ChatRoomInfo>
)

data class ChatRoomInfo(
    val name: String,
    var userCount: Int,
    val capacity: Int
)

data class ChatMessage(
    val sender: String,
    val body: String,
    val isSystem: Boolean = false,
    val id: Long = 0,
    val createdAt: String = ""
)

data class DirectMessage(
    val id: Long = 0,
    val sender: String,
    val recipient: String,
    val body: String,
    val createdAt: String = "",
    val read: Boolean = false
)

data class UserProfile(
    val username: String,
    val nickname: String,
    val status: String = "Available",
    val statusMessage: String = "",
    val avatar: String = "kool",
    val isOnline: Boolean = true
)
