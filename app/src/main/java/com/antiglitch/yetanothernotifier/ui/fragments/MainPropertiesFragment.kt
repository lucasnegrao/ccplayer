package com.antiglitch.yetanothernotifier.ui.fragments

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class SettingsItem(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsFragment(
    onNavigateToNotificationProperties: () -> Unit,
    onNavigateToMqttProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    context as? Activity
    val prefs = remember { context.getSharedPreferences("focus_memory", Context.MODE_PRIVATE) }

    val focusRequesters = remember { List(2) { FocusRequester() } }
    val scrollState = rememberScrollState()
    var currentFocusedIndex by remember { mutableStateOf(0) }

    // Define settings items
    val settingsItems = remember {
        listOf(
            SettingsItem(
                title = "Notification Properties",
                description = "Configure notification display settings, duration, scale, and appearance",
                icon = Icons.Default.Notifications,
                onClick = {
                    // Save current focus before navigating
                    prefs.edit().putInt("settings_last_focus", currentFocusedIndex).apply()
                    onNavigateToNotificationProperties()
                }
            ),
            SettingsItem(
                title = "MQTT Settings",
                description = "Configure MQTT broker connection, topics, and network discovery",
                icon = Icons.Default.Settings,
                onClick = {
                    // Save current focus before navigating
                    prefs.edit().putInt("settings_last_focus", currentFocusedIndex).apply()
                    onNavigateToMqttProperties()
                }
            )
        )
    }

    // Handle back button press - clear focus memory when leaving settings
    DisposableEffect(Unit) {
        onDispose {
            // Clear focus memory when completely leaving settings
            prefs.edit().remove("settings_last_focus").apply()
        }
    }

    // Restore focus on return and handle initial focus
    LaunchedEffect(Unit) {
        val lastFocusedIndex = prefs.getInt("settings_last_focus", 0)
        currentFocusedIndex = lastFocusedIndex.coerceIn(0, focusRequesters.size - 1)

        scrollState.scrollTo(0)
        focusRequesters[currentFocusedIndex].requestFocus()
    }

    // Handle back press - send app to background
    LaunchedEffect(Unit) {
        // This would typically be handled by the parent activity/navigation
        // but we can set up the logic here for when back is pressed
    }

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Configure application preferences",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings items list
        settingsItems.forEachIndexed { index, item ->
            SettingsItemCard(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            currentFocusedIndex = index
                        }
                    }
            )
        }

        // App info section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column {
                    Text(
                        text = "Yet Another Notifier",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsItemCard(
    item: SettingsItem,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = item.onClick,
        modifier = modifier,
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        scale = CardDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.0f
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(MaterialTheme.colorScheme.primaryContainer, 8.dp)
        ),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Arrow indicator
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Navigate",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
