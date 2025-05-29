package com.antiglitch.yetanothernotifier.ui.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.antiglitch.yetanothernotifier.ui.fragments.MqttPropertiesFragment
import com.antiglitch.yetanothernotifier.ui.fragments.NotificationPropertiesFragment
import com.antiglitch.yetanothernotifier.data.properties.Gravity
import com.antiglitch.yetanothernotifier.utils.NotificationUtils

enum class SettingsScreen {
    NOTIFICATION_PROPERTIES,
    MQTT_PROPERTIES
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsNavigation(
    modifier: Modifier = Modifier,
    initialScreen: SettingsScreen = SettingsScreen.NOTIFICATION_PROPERTIES,
    onBackPressed: () -> Unit = {},
    onExitSettings: (() -> Unit)? = null,
    notificationGravity: Gravity = Gravity.TOP_CENTER
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val actualOnExitSettings = onExitSettings ?: {
        Log.d("SettingsNavigation", "Sending app to background")
        activity?.moveTaskToBack(true)
    }

    var currentScreen by remember { mutableStateOf(initialScreen) }

    // Calculate drawer positioning based on notification gravity
    val drawerAlignment = when (notificationGravity) {
        Gravity.TOP_START, Gravity.CENTER_START, Gravity.BOTTOM_START -> Alignment.CenterEnd
        Gravity.TOP_END, Gravity.CENTER_END, Gravity.BOTTOM_END -> Alignment.CenterStart
        else -> Alignment.CenterStart // Default for center positions
    }

    // Calculate content area sizing to avoid overlap (adjust for collapsed drawer)
    val contentModifier = when (NotificationUtils.getOppositeOrientation(notificationGravity)) {
        NotificationUtils.Orientation.Horizontal -> Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.9f) // Leave 10% for collapsed drawer
        NotificationUtils.Orientation.Vertical -> Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f) // Leave 15% for notification
    }

    // Handle system back button navigation
    BackHandler(enabled = true) {
        Log.d("SettingsNavigation", "Back pressed. Current screen: $currentScreen")
        // Exit settings directly since we don't have a main screen
        Log.d("SettingsNavigation", "Exiting settings.")
        actualOnExitSettings()
    }

    AppNavigationDrawer(
        currentScreen = currentScreen,
        onNavigateToScreen = { screen ->
            Log.d("SettingsNavigation", "Navigating to $screen")
            currentScreen = screen
        },
        onCloseDrawer = {
            // Not used in the new implementation
        },
        isDrawerOpen = true, // Always show drawer (collapsed by default)
        drawerAlignment = drawerAlignment,
        modifier = modifier
    ) {
        when (currentScreen) {
            SettingsScreen.NOTIFICATION_PROPERTIES -> {
                Log.d("SettingsNavigation", "Displaying NOTIFICATION_PROPERTIES screen")
                NotificationPropertiesFragment(
                    modifier = contentModifier,
                    onBackPressed = {
                        Log.d("SettingsNavigation", "NotificationPropertiesFragment back pressed, exiting settings")
                        actualOnExitSettings()
                    },
                    onOpenDrawer = {
                        // Not needed - drawer is always visible
                    }
                )
            }

            SettingsScreen.MQTT_PROPERTIES -> {
                Log.d("SettingsNavigation", "Displaying MQTT_PROPERTIES screen")
                MqttPropertiesFragment(
                    modifier = contentModifier,
                    onBackPressed = {
                        Log.d("SettingsNavigation", "MqttPropertiesFragment back pressed, exiting settings")
                        actualOnExitSettings()
                    },
                    onOpenDrawer = {
                        // Not needed - drawer is always visible
                    }
                )
            }
        }
    }
}
