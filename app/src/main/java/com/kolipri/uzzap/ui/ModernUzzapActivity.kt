package com.kolipri.uzzap.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kolipri.uzzap.data.AppState

class ModernUzzapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UzzapModernTheme {
                ModernUzzapShell(
                    nickname = AppState.nickname.ifBlank { "cy" },
                    onLegacy = { finish() }
                )
            }
        }
    }
}

@Composable
private fun UzzapModernTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF79D900),
            secondary = Color(0xFF2E7D32),
            background = Color(0xFFF7F9F5)
        ),
        content = content
    )
}

@Composable
private fun ModernUzzapShell(
    nickname: String,
    onLegacy: () -> Unit
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding()
            ) {
                listOf("Home", "Messages", "Rooms", "Profile").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Text(listOf("⌂", "◌", "◇", "●")[index]) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F9F5))
                .statusBarsPadding()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.size(4.dp))
            Text("UZZAP", style = MaterialTheme.typography.headlineMedium)
            Text("Welcome back, $nickname", style = MaterialTheme.typography.titleMedium)

            when (selected) {
                0 -> HomeContent()
                1 -> PlaceholderContent("Messages", "Your modern messaging surface will live here.")
                2 -> PlaceholderContent("Rooms", "Your province and chatroom browser will live here.")
                3 -> PlaceholderContent("Profile", "Your profile and settings will live here.")
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = onLegacy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Return to existing Uzzap")
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun HomeContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Your Uzzap", style = MaterialTheme.typography.titleLarge)
            Text(
                "This is the first Compose shell. Existing Supabase, authentication, rooms, messaging and bot code remain untouched.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusPill("Cloud", "Ready", Modifier.weight(1f))
                StatusPill("UI", "Compose", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusPill(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PlaceholderContent(title: String, body: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
