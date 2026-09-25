package com.kolipri.uzzap.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kolipri.uzzap.data.AppState
import com.kolipri.uzzap.data.SupabaseClient
import com.kolipri.uzzap.data.SupabaseRealtimeClient
import com.kolipri.uzzap.model.ChatRoomCategory
import com.kolipri.uzzap.model.ChatRoomInfo

class ModernUzzapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UzzapModernTheme {
                ModernUzzapShell(
                    nickname = AppState.nickname.ifBlank { "cy" },
                    username = AppState.username.ifBlank { AppState.nickname.ifBlank { "cy" } }
                )
            }
        }
    }

    override fun onDestroy() {
        SupabaseRealtimeClient.stop()
        super.onDestroy()
    }
}

@Composable
private fun UzzapModernTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF63B915),
            secondary = Color(0xFF2E7D32),
            background = Color(0xFFF5F8F3),
            surface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernUzzapShell(nickname: String, username: String) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var onlineCount by remember { mutableIntStateOf(-1) }
    var realtime by remember { mutableStateOf(SupabaseRealtimeClient.isConnected()) }
    var categories by remember { mutableStateOf(AppState.categories) }
    var buddies by remember { mutableStateOf(emptyList<String>()) }
    var roomCounts by remember { mutableStateOf(emptyMap<String, Int>()) }

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { realtime = it }
        SupabaseRealtimeClient.addConnectionListener(listener)
        SupabaseRealtimeClient.start()
        onDispose { SupabaseRealtimeClient.removeConnectionListener(listener) }
    }

    LaunchedEffect(username) {
        SupabaseClient.fetchCategoriesAndRooms { result ->
            if (!result.isNullOrEmpty()) {
                categories = result
                AppState.categories = result
            }
        }
        SupabaseClient.fetchBuddies(username) { groups ->
            buddies = groups?.values.orEmpty().flatten()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(username, true) }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        SupabaseClient.fetchOnlineCount { if (it >= 0) onlineCount = it }
        SupabaseClient.fetchLiveRoomCounts { if (it != null) roomCounts = it }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            SupabaseClient.fetchOnlineCount { if (it >= 0) onlineCount = it }
            SupabaseClient.fetchLiveRoomCounts { if (it != null) roomCounts = it }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (selected) {
                                0 -> "Home"
                                1 -> "Messages"
                                2 -> "Rooms"
                                else -> "Profile"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (realtime) "Uzzap is online" else "Reconnecting…",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    StatusDot(realtime)
                    Spacer(Modifier.width(16.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                listOf("Home", "Messages", "Rooms", "Profile").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Text(listOf("H", "M", "R", "P")[index], fontWeight = FontWeight.Bold) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(padding)
        ) {
            when (selected) {
                0 -> HomeContent(
                    nickname, onlineCount, realtime, buddies.size, categories.sumOf { it.rooms.size },
                    onMessages = { selected = 1 },
                    onRooms = { selected = 2 },
                    onProfile = { selected = 3 },
                    onBot = { context.startActivity(ChatHostActivity.intent(context, ChatHostActivity.MODE_CHAT)) }
                )
                1 -> MessagesContent(
                    buddies = buddies,
                    onOpenBuddy = { context.startActivity(PrivateChatActivity.intent(context, it)) },
                    onOpenLegacy = { context.startActivity(InstantMessagingActivity.intent(context)) }
                )
                2 -> RoomsContent(categories, roomCounts) {
                    context.startActivity(
                        ChatHostActivity.intent(context, ChatHostActivity.MODE_CHAT)
                            .putExtra("modern_open_room", it)
                    )
                }
                else -> ProfileContent(
                    nickname, username, AppState.email, AppState.role,
                    AppState.presence.label, AppState.statusMessage,
                    onSettings = { context.startActivity(LegacySettingsActivity.intent(context, LegacySettingsActivity.MODE_SETTINGS)) },
                    onStatus = { context.startActivity(LegacySettingsActivity.intent(context, LegacySettingsActivity.MODE_STATUS)) }
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    nickname: String, online: Int, realtime: Boolean, buddyCount: Int, roomCount: Int,
    onMessages: () -> Unit, onRooms: () -> Unit, onProfile: () -> Unit, onBot: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Welcome back, $nickname", color = Color.White,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your real Uzzap account, rooms and buddies are connected.",
                        color = Color.White.copy(alpha = .92f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("Online", if (online >= 0) online.toString() else "—")
                        MiniStat("Buddies", buddyCount.toString())
                        MiniStat("Rooms", roomCount.toString())
                    }
                }
            }
        }
        item {
            Text("Quick access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Messages", "M", onMessages, Modifier.weight(1f))
                QuickAction("Rooms", "R", onRooms, Modifier.weight(1f))
            }
            Spacer(Modifier.size(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Profile", "P", onProfile, Modifier.weight(1f))
                QuickAction("UzzapBot", "B", onBot, Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live connection", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(realtime)
                        Spacer(Modifier.width(8.dp))
                        Text(if (realtime) "Realtime connected" else "Realtime reconnecting")
                    }
                    Text(
                        "Room messages, private-message events and presence continue through the existing Supabase clients.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesContent(buddies: List<String>, onOpenBuddy: (String) -> Unit, onOpenLegacy: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text("Your buddies", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Private chats use the existing Uzzap direct-message and Realtime backend.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (buddies.isEmpty()) {
            item { EmptyCard("No buddies yet",
                "Add a buddy from Extended Messaging, then private chats will appear here.",
                "Open Messages", onOpenLegacy) }
        } else {
            items(buddies, key = { it.lowercase() }) { buddy -> BuddyCard(buddy) { onOpenBuddy(buddy) } }
        }
    }
}

@Composable
private fun RoomsContent(categories: List<ChatRoomCategory>, counts: Map<String, Int>, onOpenRoom: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text("Province & regional rooms", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Room hierarchy comes from Supabase. Live counts refresh every 30 seconds.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (categories.isEmpty()) {
            item { EmptyCard("Loading rooms…", "Fetching the current room hierarchy from Uzzap cloud.", null) {} }
        } else {
            categories.forEach { category ->
                item(key = category.name) {
                    Card(shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(category.name, Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${category.rooms.size} rooms", style = MaterialTheme.typography.labelMedium)
                            }
                            HorizontalDivider()
                            category.rooms.forEach { room ->
                                val count = counts[room.name] ?: room.userCount
                                RoomRow(room, count, onOpenRoom)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    nickname: String, username: String, email: String, role: String, presence: String, statusMessage: String,
    onSettings: () -> Unit, onStatus: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(58.dp), CircleShape,
                            MaterialTheme.colorScheme.primary.copy(alpha = .14f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(nickname.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("@$username", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ProfileLine("Email", email.ifBlank { "Not set" })
                    ProfileLine("Status", presence)
                    if (statusMessage.isNotBlank()) ProfileLine("Status message", statusMessage)
                    ProfileLine("Role", role.ifBlank { "user" })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSettings, Modifier.weight(1f)) { Text("Account") }
                Button(onClick = onStatus, Modifier.weight(1f)) { Text("Status") }
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, letter: String, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.size(38.dp), CircleShape, MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) { Text(letter, fontWeight = FontWeight.Bold) }
            }
            Text(title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .16f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold)
            Text(label, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BuddyCard(buddy: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp), CircleShape, MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) { Text(buddy.take(1).uppercase(), fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(buddy, fontWeight = FontWeight.SemiBold)
                Text("Private chat", style = MaterialTheme.typography.labelSmall)
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun RoomRow(room: ChatRoomInfo, count: Int, onOpenRoom: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(room.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count online · capacity ${room.capacity}", style = MaterialTheme.typography.labelSmall)
        }
        FilterChip(selected = false, onClick = { onOpenRoom(room.name) }, label = { Text("Open") })
    }
}

@Composable
private fun EmptyCard(title: String, body: String, action: String?, onAction: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Surface(Modifier.size(10.dp), CircleShape,
        if (online) Color(0xFF63B915) else Color(0xFF9AA0A6)) {}
}
