package com.kolipri.uzzap

import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.PasswordRecoveryClient
import com.kolipri.uzzap.model.BuddyGroup
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.Presence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UzzapModelsAndRecoveryTest {

    @Test
    fun testChatMessageProperties() {
        val userMsg = ChatMessage(sender = "juan_delacruz", body = "Kamusta Uzzap!", isSystem = false, id = 42L)
        assertEquals("juan_delacruz", userMsg.sender)
        assertEquals("Kamusta Uzzap!", userMsg.body)
        assertFalse(userMsg.isSystem)
        assertEquals(42L, userMsg.id)

        val sysMsg = ChatMessage(sender = "uzzapbot", body = "Welcome to Manila Room!", isSystem = true, id = 43L)
        assertTrue(sysMsg.isSystem)
        assertEquals("uzzapbot", sysMsg.sender)
    }

    @Test
    fun testBuddyGroupTogglingAndMembership() {
        val group = BuddyGroup("friends", "My Friends", expandable = true, expanded = false)
        assertFalse(group.expanded)
        group.expanded = true
        assertTrue(group.expanded)

        group.buddies.add("kool_guy")
        group.buddies.add("chick_99")
        assertEquals(2, group.buddies.size)
        assertTrue(group.buddies.contains("kool_guy"))

        group.buddies.remove("kool_guy")
        assertEquals(1, group.buddies.size)
        assertFalse(group.buddies.contains("kool_guy"))
    }

    @Test
    fun testPasswordRecoveryScheme() {
        assertEquals("uzzap://auth/reset", PasswordRecoveryClient.RESET_URI)
    }

    @Test
    fun testAppStateRoomMessageSeparation() {
        val room1 = "Cebu"
        val room2 = "Davao"

        AppState.messagesFor(room1).clear()
        AppState.messagesFor(room2).clear()

        AppState.messagesFor(room1).add(ChatMessage("alice", "Maayong adlaw!"))
        AppState.messagesFor(room2).add(ChatMessage("bob", "Maayong buntag!"))

        assertEquals(1, AppState.messagesFor(room1).size)
        assertEquals(1, AppState.messagesFor(room2).size)
        assertEquals("Maayong adlaw!", AppState.messagesFor(room1)[0].body)
        assertEquals("Maayong buntag!", AppState.messagesFor(room2)[0].body)
    }

    @Test
    fun testPresenceStates() {
        assertEquals(3, Presence.values().size)
        assertNotNull(Presence.valueOf("AVAILABLE"))
        assertNotNull(Presence.valueOf("NOT_AVAILABLE"))
        assertNotNull(Presence.valueOf("INVISIBLE"))
    }

    @Test
    fun testRoomInvitationProperties() {
        val invite = com.kolipri.uzzap.data.RoomInvitation(
            id = 99L,
            roomName = "Batangas",
            inviterUsername = "maria",
            inviteeUsername = "juan",
            status = "pending",
            createdAt = "2026-09-14T08:00:00Z"
        )
        assertEquals(99L, invite.id)
        assertEquals("Batangas", invite.roomName)
        assertEquals("maria", invite.inviterUsername)
        assertEquals("juan", invite.inviteeUsername)
        assertEquals("pending", invite.status)
    }

    @Test
    fun testBuddyPresenceProperties() {
        val presence = com.kolipri.uzzap.data.BuddyPresence(
            username = "juan",
            isOnline = true,
            lastSeen = "2026-09-14T16:30:00Z",
            status = "Available"
        )
        assertEquals("juan", presence.username)
        assertTrue(presence.isOnline)
        assertEquals("Available", presence.status)
    }

    @Test
    fun testChatRoomInfoCapacity() {
        val room = com.kolipri.uzzap.model.ChatRoomInfo(
            name = "Batangas",
            userCount = 12,
            capacity = 100
        )
        assertEquals("Batangas", room.name)
        assertEquals(12, room.userCount)
        assertEquals(100, room.capacity)
    }

    @Test
    fun testMessageDeduplicationById() {
        val room = "Laguna"
        AppState.messagesFor(room).clear()
        val m1 = ChatMessage("sender1", "Hello", false, id = 500L)
        val m2 = ChatMessage("sender1", "Hello again", false, id = 500L) // same ID
        val m3 = ChatMessage("sender2", "Different", false, id = 501L)

        val added1 = AppState.addOrUpdateMessages(room, listOf(m1))
        assertEquals(1, added1)
        assertEquals(1, AppState.messagesFor(room).size)

        val added2 = AppState.addOrUpdateMessages(room, listOf(m2, m3))
        assertEquals(1, added2) // only m3 should be added
        assertEquals(2, AppState.messagesFor(room).size)
    }

    @Test
    fun testMessageDeduplicationByContentWhenNoId() {
        val room = "Cavite"
        AppState.messagesFor(room).clear()
        val m1 = ChatMessage("senderA", "Exact text", false, id = 0L)
        val m2 = ChatMessage("senderA", "Exact text", false, id = 0L) // exact duplicate
        val m3 = ChatMessage("senderB", "Exact text", false, id = 0L) // different sender

        val added1 = AppState.addOrUpdateMessages(room, listOf(m1))
        assertEquals(1, added1)

        val added2 = AppState.addOrUpdateMessages(room, listOf(m2, m3))
        assertEquals(1, added2) // m2 duplicate skipped, m3 added
        assertEquals(2, AppState.messagesFor(room).size)
    }

    @Test
    fun testUnreadPrivateMessageAggregation() {
        val unread = com.kolipri.uzzap.data.UnreadPrivateMessage(
            count = 5,
            senders = listOf("maria", "pedro", "maria"),
            countsBySender = mapOf("maria" to 3, "pedro" to 2)
        )
        assertEquals(5, unread.count)
        assertEquals(3, unread.countsBySender["maria"])
        assertEquals(2, unread.countsBySender["pedro"])
    }

    @Test
    fun testActiveChatBuddyNotificationSuppression() {
        val activeBuddy = "maria"
        AppState.activeChatBuddy = activeBuddy

        val unread = com.kolipri.uzzap.data.UnreadPrivateMessage(
            count = 4,
            senders = listOf("maria", "pedro"),
            countsBySender = mapOf("maria" to 3, "pedro" to 1)
        )

        // Filter out active buddy
        val relevantSenders = unread.senders.filter { !it.equals(activeBuddy, ignoreCase = true) }
        val relevantCount = relevantSenders.sumOf { unread.countsBySender[it.lowercase()] ?: 1 }

        assertEquals(listOf("pedro"), relevantSenders)
        assertEquals(1, relevantCount)

        AppState.activeChatBuddy = null
    }
}
