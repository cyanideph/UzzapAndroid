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
}
