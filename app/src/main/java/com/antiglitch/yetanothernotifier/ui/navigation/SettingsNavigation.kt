package com.antiglitch.yetanothernotifier.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.antiglitch.yetanothernotifier.ui.fragments.SettingsFragment
import com.antiglitch.yetanothernotifier.ui.fragments.NotificationPropertiesFragment
import com.antiglitch.yetanothernotifier.ui.fragments.MqttPropertiesFragment
import android.util.Log
import android.app.Activity

enum class SettingsScreen {
    MAIN,
    NOTIFICATION_PROPERTIES,
    MQTT_PROPERTIES
}

@Composable
fun SettingsNavigation(
    modifier: Modifier = Modifier,
    initialScreen: SettingsScreen = SettingsScreen.MAIN,
    onBackPressed: () -> Unit = {}, // Default empty implementation
    onExitSettings: (() -> Unit)? = null // Fix: Make the entire function type nullable
) {
    // Move context access inside the composable
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Create the actual exit function inside the composable
    val actualOnExitSettings = onExitSettings ?: {
        Log.d("SettingsNavigation", "Sending app to background")
        activity?.moveTaskToBack(true)
    }
    
    // Add composition logging
    Log.d("SettingsNavigation", "SettingsNavigation composed with initialScreen: $initialScreen")
    
    var currentScreen by remember { mutableStateOf(initialScreen) }

    // Handle system back button navigation
    BackHandler(enabled = true) {
        Log.d("SettingsNavigation", "Back pressed. Current screen: $currentScreen")
        if (currentScreen != SettingsScreen.MAIN) {
            // If on a sub-screen, navigate back to the main settings screen
            Log.d("SettingsNavigation", "Navigating from $currentScreen to MAIN.")
            currentScreen = SettingsScreen.MAIN
        } else {
            // If on the main settings screen, exit settings entirely
            Log.d("SettingsNavigation", "On MAIN screen, exiting settings.")
            actualOnExitSettings()
        }
    }

    when (currentScreen) {
        SettingsScreen.MAIN -> {
            Log.d("SettingsNavigation", "Displaying MAIN settings screen")
            SettingsFragment(
                onNavigateToNotificationProperties = {
                    Log.d("SettingsNavigation", "Navigating to NOTIFICATION_PROPERTIES")
                    currentScreen = SettingsScreen.NOTIFICATION_PROPERTIES
                },
                onNavigateToMqttProperties = {
                    Log.d("SettingsNavigation", "Navigating to MQTT_PROPERTIES")
                    currentScreen = SettingsScreen.MQTT_PROPERTIES
                },
                modifier = modifier
            )
        }
        
        SettingsScreen.NOTIFICATION_PROPERTIES -> {
            Log.d("SettingsNavigation", "Displaying NOTIFICATION_PROPERTIES screen")
            NotificationPropertiesFragment(
                modifier = modifier,
                onBackPressed = {
                    Log.d("SettingsNavigation", "NotificationPropertiesFragment back pressed, navigating to MAIN")
                    currentScreen = SettingsScreen.MAIN
                }
            )
        }
        
        SettingsScreen.MQTT_PROPERTIES -> {
            Log.d("SettingsNavigation", "Displaying MQTT_PROPERTIES screen")
            MqttPropertiesFragment(
                modifier = modifier,
                onBackPressed = {
                    Log.d("SettingsNavigation", "MqttPropertiesFragment back pressed, navigating to MAIN")
                    currentScreen = SettingsScreen.MAIN
                }
            )
        }
    }
}
