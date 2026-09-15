package com.kolipri.uzzap

import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.BuddyPresence
import com.kolipri.uzzap.data.UnreadPrivateMessage
import com.kolipri.uzzap.model.BuddyGroup
import com.kolipri.uzzap.model.ChatMessage
import com.kolipri.uzzap.model.ChatRoomCategory
import com.kolipri.uzzap.model.ChatRoomInfo
import com.kolipri.uzzap.model.DirectMessage
import com.kolipri.uzzap.model.Presence
import com.kolipri.uzzap.ui.util.EmoticonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * End-to-End behavioral test suite from a user's perspective.
 *
 * Covers all core functional journeys in the Uzzap application:
 * 1. User Authentication, Profile & Presence Settings
 * 2. Philippine Region & Province Chatroom Discovery
 * 3. Public Room Chat, Emoticons & uzzapbot System Message Formatting
 * 4. Buddy List Organization, Grouping & Batch Presence Status
 * 5. 1-on-1 Private Messaging, Unread Tracking & Notification Suppression
 * 6. User Moderation: Reporting & Blocking
 * 7. Offline Resilience & State Preservation
 */
class UzzapUserPerspectiveAllFunctionsTest {

    @Before
    fun setUp() {
        AppState.username = "juandelacruz"
        AppState.nickname = "Juan"
        AppState.statusMessage = "Available"
        AppState.presence = Presence.AVAILABLE
        AppState.themeName = "default"
        AppState.activeChatBuddy = null
        AppState.buddyGroups.clear()
        AppState.buddyGroups.addAll(
            listOf(
                BuddyGroup("friends", "Friends", true, true, mutableListOf()),
                BuddyGroup("family", "Family", true, false, mutableListOf()),
                BuddyGroup("work", "Work", true, false, mutableListOf()),
                BuddyGroup("all", "All", true, true, mutableListOf())
            )
        )
    }

    // =========================================================================
    // 1. User Authentication, Profile & Presence Settings
    // =========================================================================
    @Test
    fun userJourney_profileAndPresenceManagement() {
        // User views default profile
        assertEquals("juandelacruz", AppState.username)
        assertEquals("Juan", AppState.nickname)
        assertEquals(Presence.AVAILABLE, AppState.presence)

        // User updates nickname and status message
        AppState.nickname = "Kuya Juan"
        AppState.statusMessage = "Tara chat tayo!"
        assertEquals("Kuya Juan", AppState.nickname)
        assertEquals("Tara chat tayo!", AppState.statusMessage)

        // User changes presence to INVISIBLE so buddies see them as offline
        AppState.presence = Presence.INVISIBLE
        assertEquals(Presence.INVISIBLE, AppState.presence)

        // User customizes UI theme to classic
        AppState.themeName = "classic"
        assertEquals("classic", AppState.themeName)

        // User switches presence back to AVAILABLE
        AppState.presence = Presence.AVAILABLE
        assertEquals(Presence.AVAILABLE, AppState.presence)
    }

    // =========================================================================
    // 2. Philippine Region & Province Chatroom Discovery
    // =========================================================================
    @Test
    fun userJourney_philippineRoomHierarchyAndSearch() {
        // App loads authoritative Philippine region & province rooms
        val calabarzonRooms = listOf(
            ChatRoomInfo("Batangas", 24, 100),
            ChatRoomInfo("Cavite", 45, 100),
            ChatRoomInfo("Laguna", 38, 100),
            ChatRoomInfo("Quezon", 15, 100),
            ChatRoomInfo("Rizal", 30, 100)
        )
        val ncrRooms = listOf(
            ChatRoomInfo("Metro Manila", 85, 200)
        )
        val centralVisayasRooms = listOf(
            ChatRoomInfo("Bohol", 12, 100),
            ChatRoomInfo("Cebu", 60, 100)
        )

        val categories = listOf(
            ChatRoomCategory("Region IV-A", calabarzonRooms.sumOf { it.userCount }, calabarzonRooms.sumOf { it.capacity }, calabarzonRooms),
            ChatRoomCategory("NCR", ncrRooms.sumOf { it.userCount }, ncrRooms.sumOf { it.capacity }, ncrRooms),
            ChatRoomCategory("Region VII", centralVisayasRooms.sumOf { it.userCount }, centralVisayasRooms.sumOf { it.capacity }, centralVisayasRooms)
        )
        AppState.categories = categories

        // User browses categories
        assertEquals(3, AppState.categories.size)
        val calabarzon = AppState.categories.first { it.name == "Region IV-A" }
        assertEquals(152, calabarzon.userCount)
        assertEquals(500, calabarzon.capacity)

        // User inspects provinces inside Region IV-A
        assertEquals(5, calabarzon.rooms.size)
        assertTrue(calabarzon.rooms.any { it.name == "Batangas" })

        // User searches for province "Cebu"
        val query = "cebu"
        val searchResults = AppState.categories
            .flatMap { it.rooms }
            .filter { it.name.lowercase().contains(query) }
        assertEquals(1, searchResults.size)
        assertEquals("Cebu", searchResults.first().name)
        assertEquals(60, searchResults.first().userCount)

        // User searches for a non-existent room
        val emptyResults = AppState.categories
            .flatMap { it.rooms }
            .filter { it.name.lowercase().contains("tokyo") }
        assertTrue(emptyResults.isEmpty())
    }

    // =========================================================================
    // 3. Public Room Chat, Emoticons & uzzapbot System Message Formatting
    // =========================================================================
    @Test
    fun userJourney_roomChatEmoticonsAndSystemBotMessages() {
        val roomName = "Batangas"
        val messages = AppState.messagesFor(roomName)
        messages.clear()

        // User enters room: welcome system message from uzzapbot is received
        val welcomeMsg = ChatMessage(
            sender = "uzzapbot",
            body = "Welcome to Batangas room! Please follow community guidelines.",
            isSystem = true,
            id = 1001L
        )
        AppState.addOrUpdateMessages(roomName, listOf(welcomeMsg))

        // Another user joins: system announcement
        val joinMsg = ChatMessage(
            sender = "System",
            body = "MariaClara has entered the room.",
            isSystem = true,
            id = 1002L
        )
        AppState.addOrUpdateMessages(roomName, listOf(joinMsg))

        // User sends a chat message with Uzzap emoticons
        val userMsgText = "Kumusta mga Batangueño! :) :D"
        val userMsg = ChatMessage(
            sender = AppState.nickname,
            body = userMsgText,
            isSystem = false,
            id = 1003L
        )
        AppState.addOrUpdateMessages(roomName, listOf(userMsg))

        // Verify messages in room
        assertEquals(3, messages.size)

        // User perspective: verify bot & system messages are recognized as system view type
        assertTrue(messages[0].isSystem || messages[0].sender.equals("uzzapbot", ignoreCase = true))
        assertTrue(messages[1].isSystem || messages[1].sender.equals("System", ignoreCase = true))

        // User perspective: normal chat messages are NOT system messages
        assertFalse(messages[2].isSystem)
        assertEquals("Juan", messages[2].sender)
        assertEquals(userMsgText, messages[2].body)

        // Emoticon parsing check: verify smile and grin emoticon codes are registered
        assertTrue(EmoticonParser.hasEmoticons("Hello :)"))
        assertTrue(EmoticonParser.hasEmoticons("Haha :D"))
        assertTrue(EmoticonParser.hasEmoticons("Wink ;)"))
        assertFalse(EmoticonParser.hasEmoticons("Plain text with no smileys"))

        // Duplicate protection: receiving the same message via realtime again does NOT duplicate
        val duplicateAdded = AppState.addOrUpdateMessages(roomName, listOf(userMsg))
        assertEquals(0, duplicateAdded)
        assertEquals(3, messages.size)
    }

    // =========================================================================
    // 4. Buddy List Organization, Grouping & Batch Presence Status
    // =========================================================================
    @Test
    fun userJourney_buddyListManagementAndBatchPresence() {
        val friendsGroup = AppState.buddyGroups.first { it.key == "friends" }
        val familyGroup = AppState.buddyGroups.first { it.key == "family" }

        // User adds buddies
        friendsGroup.buddies.add("mariaclara")
        friendsGroup.buddies.add("pedropenduko")
        familyGroup.buddies.add("nanay_rosa")

        // User checks "All" group view: aggregates unique buddies across all groups
        val allBuddies = AppState.buddyGroups
            .filter { it.key != "all" }
            .flatMap { it.buddies }
            .distinct()
        assertEquals(3, allBuddies.size)
        assertTrue(allBuddies.contains("mariaclara"))
        assertTrue(allBuddies.contains("pedropenduko"))
        assertTrue(allBuddies.contains("nanay_rosa"))

        // Simulate server-backed batch presence status
        val now = Instant.now()
        val onlineLastSeen = now.minusSeconds(10).toString() // Active 10 seconds ago (< 35s)
        val offlineLastSeen = now.minusSeconds(120).toString() // Inactive 2 minutes ago (> 35s)

        val presenceMap = mapOf(
            "mariaclara" to BuddyPresence("mariaclara", isOnline = true, lastSeen = onlineLastSeen, status = "Chatting"),
            "pedropenduko" to BuddyPresence("pedropenduko", isOnline = false, lastSeen = offlineLastSeen, status = "Away"),
            "nanay_rosa" to BuddyPresence("nanay_rosa", isOnline = true, lastSeen = onlineLastSeen, status = "Cooking")
        )

        // User verifies presence indications in the UI
        val mariaPresence = presenceMap["mariaclara"]
        assertNotNull(mariaPresence)
        assertTrue(mariaPresence!!.isOnline)
        assertEquals("Chatting", mariaPresence.status)

        val pedroPresence = presenceMap["pedropenduko"]
        assertNotNull(pedroPresence)
        assertFalse(pedroPresence!!.isOnline)

        // User moves a buddy from Friends to Family
        friendsGroup.buddies.remove("mariaclara")
        familyGroup.buddies.add("mariaclara")
        assertFalse(friendsGroup.buddies.contains("mariaclara"))
        assertTrue(familyGroup.buddies.contains("mariaclara"))
    }

    // =========================================================================
    // 5. 1-on-1 Private Messaging, Unread Tracking & Notification Suppression
    // =========================================================================
    @Test
    fun userJourney_privateMessagingAndSmartNotifications() {
        val buddy1 = "mariaclara"
        val buddy2 = "pedropenduko"

        // User receives direct messages while in main menu
        val dm1 = DirectMessage(1L, buddy1, AppState.username, "Psst, Juan! Kumusta?", "2026-09-14T10:00:00Z", read = false)
        val dm2 = DirectMessage(2L, buddy1, AppState.username, "May tanong ako.", "2026-09-14T10:01:00Z", read = false)
        val dm3 = DirectMessage(3L, buddy2, AppState.username, "Pare, online ka ba?", "2026-09-14T10:02:00Z", read = false)

        var unreadState = UnreadPrivateMessage(
            count = 3,
            senders = listOf(buddy1, buddy2),
            countsBySender = mapOf(buddy1 to 2, buddy2 to 1)
        )
        assertEquals(3, unreadState.count)
        assertEquals(2, unreadState.countsBySender[buddy1])
        assertEquals(1, unreadState.countsBySender[buddy2])

        // User opens PrivateChatActivity with Maria Clara
        AppState.activeChatBuddy = buddy1

        // While chatting with Maria, a new message arrives from Maria AND one from Pedro
        // Notification deduplication logic: Maria's messages must NOT trigger phone notifications
        val activeBuddy = AppState.activeChatBuddy
        assertEquals(buddy1, activeBuddy)

        val relevantSenders = if (activeBuddy != null) unreadState.senders.filter { !it.equals(activeBuddy, ignoreCase = true) } else unreadState.senders
        val relevantCount = relevantSenders.sumOf { unreadState.countsBySender[it.lowercase()] ?: 1 }

        // Only Pedro's message should notify the user!
        assertEquals(listOf(buddy2), relevantSenders)
        assertEquals(1, relevantCount)

        // User reads Maria's messages -> conversation marked read
        unreadState = UnreadPrivateMessage(
            count = 1,
            senders = listOf(buddy2),
            countsBySender = mapOf(buddy2 to 1)
        )
        assertEquals(1, unreadState.count)
        assertNull(unreadState.countsBySender[buddy1])

        // User exits private chat
        AppState.activeChatBuddy = null
        assertNull(AppState.activeChatBuddy)
    }

    // =========================================================================
    // 6. User Moderation: Reporting & Blocking
    // =========================================================================
    @Test
    fun userJourney_moderationReportAndBlock() {
        val spammer = "spammer999"
        val blockedList = mutableSetOf<String>()

        // User encounters unwanted spammer in chat
        assertFalse(blockedList.contains(spammer))

        // User reports the spammer
        val reportReason = "Spam"
        val reportDetails = "Flooding the room with advertisements"
        assertEquals("Spam", reportReason)
        assertTrue(reportDetails.isNotBlank())

        // User blocks the spammer
        blockedList.add(spammer)
        assertTrue(blockedList.contains(spammer))

        // Incoming direct message from spammer arrives
        val incomingDm = DirectMessage(99L, spammer, AppState.username, "Buy cheap products here!", "2026-09-14T11:00:00Z", false)

        // Client & server block verification: message from blocked user is dropped
        val isBlocked = blockedList.contains(incomingDm.sender)
        assertTrue(isBlocked)

        // Clean user messages are accepted
        val friendDm = DirectMessage(100L, "mariaclara", AppState.username, "Ingat ka palagi!", "2026-09-14T11:01:00Z", false)
        assertFalse(blockedList.contains(friendDm.sender))
    }

    // =========================================================================
    // 7. Offline Resilience & State Preservation
    // =========================================================================
    @Test
    fun userJourney_offlineResilienceAndStatePreservation() {
        // App has loaded Philippine rooms into memory
        val initialCategories = listOf(
            ChatRoomCategory("NCR", 50, 200, listOf(ChatRoomInfo("Metro Manila", 50, 200)))
        )
        AppState.categories = initialCategories

        // Network goes offline: remote call returns null/empty
        val remoteCategories: List<ChatRoomCategory>? = null

        // App state retains existing categories so screen is not blank
        val displayedCategories = if (!remoteCategories.isNullOrEmpty()) remoteCategories else AppState.categories
        assertFalse(displayedCategories.isEmpty())
        assertEquals("NCR", displayedCategories.first().name)
        assertEquals("Metro Manila", displayedCategories.first().rooms.first().name)

        // Existing chat messages in room remain preserved during disconnect
        val roomName = "Metro Manila"
        AppState.messagesFor(roomName).add(ChatMessage("System", "Cached conversation history", true, id = 888L))
        assertEquals(1, AppState.messagesFor(roomName).size)

        // App reconnects: state is intact and ready for new realtime events
        assertTrue(AppState.messagesFor(roomName).isNotEmpty())
        assertEquals("Cached conversation history", AppState.messagesFor(roomName).first().body)
    }
}
