package com.antiglitch.yetanothernotifier.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class NavigationItem(
    val screen: SettingsScreen,
    val title: String,
    val icon: ImageVector,
    val description: String = ""
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppNavigationDrawer(
    currentScreen: SettingsScreen,
    onNavigateToScreen: (SettingsScreen) -> Unit,
    onCloseDrawer: () -> Unit,
    isDrawerOpen: Boolean,
    drawerAlignment: Alignment = Alignment.CenterStart,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var selectedIndex by remember { mutableStateOf(0) }
    
    val navigationItems = remember {
        listOf(
            NavigationItem(
                screen = SettingsScreen.NOTIFICATION_PROPERTIES,
                title = "Notifications",
                icon = Icons.Default.Notifications,
                description = "Display and appearance settings"
            ),
            NavigationItem(
                screen = SettingsScreen.MQTT_PROPERTIES,
                title = "MQTT",
                icon = Icons.Default.Settings,
                description = "Network and broker configuration"
            )
        )
    }

    // Update selected index when current screen changes
    LaunchedEffect(currentScreen) {
        selectedIndex = navigationItems.indexOfFirst { it.screen == currentScreen }.coerceAtLeast(0)
    }

    // Animate drawer width based on expanded state
    val drawerWidth by animateDpAsState(
        targetValue = if (isExpanded) 320.dp else 72.dp,
        animationSpec = tween(durationMillis = 300),
        label = "drawerWidth"
    )

    // Calculate content padding based on drawer alignment and width
    val contentPaddingStart = if (drawerAlignment == Alignment.CenterStart) drawerWidth else 0.dp
    val contentPaddingEnd = if (drawerAlignment == Alignment.CenterEnd) drawerWidth else 0.dp

    Box(modifier = modifier.fillMaxSize()) {
        // Content area with padding to account for drawer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = contentPaddingStart, end = contentPaddingEnd)
        ) {
            content()
        }

        // Collapsed/Expanded Navigation Drawer
        Box(
            modifier = Modifier
                .align(drawerAlignment)
                .width(drawerWidth)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = when (drawerAlignment) {
                        Alignment.CenterStart -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        Alignment.CenterEnd -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        else -> RoundedCornerShape(16.dp)
                    }
                )
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header with expand/collapse button
                if (isExpanded) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Navigation",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = { isExpanded = false }
                        ) {
                            Icon(
                                imageVector = when (drawerAlignment) {
                                    Alignment.CenterStart -> Icons.Default.ChevronLeft
                                    Alignment.CenterEnd -> Icons.Default.ChevronRight
                                    else -> Icons.Default.ChevronLeft
                                },
                                contentDescription = "Collapse"
                            )
                        }
                    }
                } else {
                    // Collapsed header - just the expand button
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { isExpanded = true },
                            modifier = Modifier.focusRequester(focusRequester)
                        ) {
                            Icon(
                                imageVector = when (drawerAlignment) {
                                    Alignment.CenterStart -> Icons.Default.ChevronRight
                                    Alignment.CenterEnd -> Icons.Default.ChevronLeft
                                    else -> Icons.Default.ChevronRight
                                },
                                contentDescription = "Expand"
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Navigation items
                navigationItems.forEachIndexed { index, item ->
                    if (isExpanded) {
                        // Expanded item with icon and text
                        Button(
                            onClick = {
                                selectedIndex = index
                                onNavigateToScreen(item.screen)
                                // Don't auto-collapse on navigation to keep drawer accessible
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .height(56.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = if (index == selectedIndex) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface,
                                contentColor = if (index == selectedIndex)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // Collapsed item - icon only
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    selectedIndex = index
                                    onNavigateToScreen(item.screen)
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (index == selectedIndex) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (index == selectedIndex)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Request focus on expand button when drawer should be shown
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen && !isExpanded) {
            focusRequester.requestFocus()
        }
    }
}
