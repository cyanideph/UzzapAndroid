package com.kolipri.uzzap

import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.DeliveryReceipt
import com.kolipri.uzzap.data.MessageIdRegistry
import com.kolipri.uzzap.data.RoomInvitation
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.DirectMessage
import com.kolipri.uzzap.model.ModerationAction
import com.kolipri.uzzap.model.Presence
import com.kolipri.uzzap.model.UserProfile
import com.kolipri.uzzap.model.UserReport
import com.kolipri.uzzap.ui.util.EmoticonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Detailed verification suite for individual components across the codebase.
 */
class UzzapAllComponentsFunctionalityTest {

    @Test
    fun testMessageIdRegistry_bidirectionalMapping() {
        val testUuid1 = UUID.randomUUID().toString()
        val testUuid2 = UUID.randomUUID().toString()

        val id1 = MessageIdRegistry.register(testUuid1)
        val id2 = MessageIdRegistry.register(testUuid2)

        // Ensure non-zero IDs
        assertTrue(id1 != 0L)
        assertTrue(id2 != 0L)
        assertNotEquals(id1, id2)

        // Ensure idempotency when registering the same UUID
        val id1Again = MessageIdRegistry.register(testUuid1)
        assertEquals(id1, id1Again)

        // Ensure reverse lookup works
        assertEquals(testUuid1, MessageIdRegistry.uuidFor(id1))
        assertEquals(testUuid2, MessageIdRegistry.uuidFor(id2))

        // Non-existent ID returns null
        assertNull(MessageIdRegistry.uuidFor(9999999999L))
    }

    @Test
    fun testEmoticonParser_comprehensiveCodes() {
        // Test standard smileys
        assertTrue(EmoticonParser.hasEmoticons("Kamusta :)"))
        assertTrue(EmoticonParser.hasEmoticons(":-)"))
        assertTrue(EmoticonParser.hasEmoticons("wink ;)"))
        assertTrue(EmoticonParser.hasEmoticons(";-("))
        assertTrue(EmoticonParser.hasEmoticons("haha :D"))
        assertTrue(EmoticonParser.hasEmoticons(":p"))
        assertTrue(EmoticonParser.hasEmoticons("<3"))
        assertTrue(EmoticonParser.hasEmoticons("[love]"))
        assertTrue(EmoticonParser.hasEmoticons(">:-("))
        assertTrue(EmoticonParser.hasEmoticons(":o"))
        assertTrue(EmoticonParser.hasEmoticons(":s"))
        assertTrue(EmoticonParser.hasEmoticons("[party]"))
        assertTrue(EmoticonParser.hasEmoticons("[drink]"))
        assertTrue(EmoticonParser.hasEmoticons("[rose]"))
        assertTrue(EmoticonParser.hasEmoticons("@}->--"))
        assertTrue(EmoticonParser.hasEmoticons("[kiss]"))
        assertTrue(EmoticonParser.hasEmoticons(":*"))

        // Test non-emoticon strings
        assertFalse(EmoticonParser.hasEmoticons("Just ordinary conversation"))
        assertFalse(EmoticonParser.hasEmoticons(null))
        assertFalse(EmoticonParser.hasEmoticons(""))
    }

    @Test
    fun testDeliveryReceiptModel() {
        val receipt = DeliveryReceipt(
            recipient = "pedro",
            body = "Hello Pedro!",
            createdAt = "2026-09-15T02:00:00Z",
            isRead = true
        )
        assertEquals("pedro", receipt.recipient)
        assertEquals("Hello Pedro!", receipt.body)
        assertEquals("2026-09-15T02:00:00Z", receipt.createdAt)
        assertTrue(receipt.isRead)
    }

    @Test
    fun testModerationActionModel() {
        val action = ModerationAction(
            id = 101L,
            moderatorId = "mod-uuid-1234",
            targetUsername = "troll_user",
            actionType = "mute",
            roomName = "Metro Manila",
            reason = "Inappropriate language",
            expiresAt = "2026-09-15T03:00:00Z",
            createdAt = "2026-09-15T02:00:00Z"
        )
        assertEquals(101L, action.id)
        assertEquals("mod-uuid-1234", action.moderatorId)
        assertEquals("troll_user", action.targetUsername)
        assertEquals("mute", action.actionType)
        assertEquals("Metro Manila", action.roomName)
        assertEquals("Inappropriate language", action.reason)
        assertEquals("2026-09-15T03:00:00Z", action.expiresAt)
    }

    @Test
    fun testUserReportModel() {
        val report = UserReport(
            id = 55L,
            reporterId = "reporter-uuid-5678",
            reportedUsername = "abusive_spammer",
            reason = "Spamming commercial links",
            details = "Posted repeated telegram invitations in Cebu room",
            status = "pending",
            createdAt = "2026-09-15T02:15:00Z"
        )
        assertEquals(55L, report.id)
        assertEquals("reporter-uuid-5678", report.reporterId)
        assertEquals("abusive_spammer", report.reportedUsername)
        assertEquals("Spamming commercial links", report.reason)
        assertEquals("pending", report.status)
        assertNotNull(report.details)
    }

    @Test
    fun testUserProfileModel() {
        val profile = UserProfile(
            username = "makati_guy",
            nickname = "MakatiGuy",
            status = "Available",
            statusMessage = "Working from home",
            avatar = "deckard",
            isOnline = true,
            role = "moderator"
        )
        assertEquals("makati_guy", profile.username)
        assertEquals("MakatiGuy", profile.nickname)
        assertEquals("Available", profile.status)
        assertEquals("Working from home", profile.statusMessage)
        assertEquals("deckard", profile.avatar)
        assertTrue(profile.isOnline)
        assertEquals("moderator", profile.role)
    }

    @Test
    fun testAppStateModeratorRoleChecking() {
        AppState.role = "user"
        assertFalse(AppState.isModerator)

        AppState.role = "moderator"
        assertTrue(AppState.isModerator)

        AppState.role = "MODERATOR"
        assertTrue(AppState.isModerator)

        AppState.role = "admin"
        assertTrue(AppState.isModerator)

        AppState.role = "ADMIN"
        assertTrue(AppState.isModerator)

        AppState.role = "member"
        assertFalse(AppState.isModerator)
    }

    @Test
    fun testAppStateLocalStateManagement() {
        AppState.username = "testuser"
        AppState.nickname = "Test Nick"
        AppState.email = "test@example.com"
        AppState.authToken = "jwt-sample-token"
        AppState.role = "moderator"
        AppState.presence = Presence.AVAILABLE
        AppState.messagesFor("Manila").add(ChatMessage("someone", "hi"))

        assertEquals("testuser", AppState.username)
        assertEquals("Test Nick", AppState.nickname)
        assertEquals("test@example.com", AppState.email)
        assertEquals("jwt-sample-token", AppState.authToken)
        assertEquals("moderator", AppState.role)
        assertEquals(Presence.AVAILABLE, AppState.presence)
        assertEquals(1, AppState.messagesFor("Manila").size)

        // Reset local in-memory states
        AppState.roomMessages.clear()
        AppState.username = "unknown-user"
        AppState.nickname = "cy"
        AppState.email = ""
        AppState.authToken = ""
        AppState.role = "user"
        AppState.presence = Presence.INVISIBLE

        assertEquals("unknown-user", AppState.username)
        assertEquals("cy", AppState.nickname)
        assertEquals("", AppState.email)
        assertEquals("", AppState.authToken)
        assertEquals("user", AppState.role)
        assertEquals(Presence.INVISIBLE, AppState.presence)
        assertTrue(AppState.messagesFor("Manila").isEmpty())
    }

    @Test
    fun testDirectMessageReadStateAndProperties() {
        val dm = DirectMessage(
            id = 777L,
            sender = "alice",
            recipient = "bob",
            body = "Magandang tanghali!",
            createdAt = "2026-09-15T04:00:00Z",
            read = false
        )
        assertEquals(777L, dm.id)
        assertEquals("alice", dm.sender)
        assertEquals("bob", dm.recipient)
        assertEquals("Magandang tanghali!", dm.body)
        assertFalse(dm.read)

        val readDm = dm.copy(read = true)
        assertTrue(readDm.read)
    }
}
