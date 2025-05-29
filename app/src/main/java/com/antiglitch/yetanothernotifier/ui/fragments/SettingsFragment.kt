package com.antiglitch.yetanothernotifier.ui.fragments

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsFragment(
    onNavigateToNotificationProperties: () -> Unit,
    onNavigateToMqttProperties: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    context as? Activity
    
    val menuButtonFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    // Request focus on the menu button when this screen loads
    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
        menuButtonFocusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .verticalScroll(scrollState)
            .padding(16.dp), // Reduced padding for better space utilization
        verticalArrangement = Arrangement.spacedBy(16.dp) // Reduced spacing
    ) {
        // Header with menu button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onOpenDrawer,
                modifier = Modifier.focusRequester(menuButtonFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Menu"
                )
                Text(
                    text = "Menu",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall // Smaller heading
            )
        }

        // Welcome content - more compact
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp), // Reduced padding
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp) // Reduced spacing
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp), // Smaller icon
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Configure Settings",
                    style = MaterialTheme.typography.titleLarge, // Smaller text
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Use the navigation menu or quick access below",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick access cards - more compact
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Notification Properties Card
            Card(
                onClick = onNavigateToNotificationProperties,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // Reduced padding
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp), // Smaller icon
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Properties",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Display settings and appearance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // MQTT Settings Card
            Card(
                onClick = onNavigateToMqttProperties,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // Reduced padding
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp), // Smaller icon
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MQTT Settings",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Broker connection and network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // App info section - more compact
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp) // Reduced padding
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp), // Smaller icon
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column {
                    Text(
                        text = "Yet Another Notifier",
                        style = MaterialTheme.typography.bodyMedium,
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
