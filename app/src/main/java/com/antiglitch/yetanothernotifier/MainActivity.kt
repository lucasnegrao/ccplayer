package com.antiglitch.yetanothernotifier

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.antiglitch.yetanothernotifier.ui.components.NotificationCard
import com.antiglitch.yetanothernotifier.ui.components.PermissionDialog
import com.antiglitch.yetanothernotifier.PermissionType
import com.antiglitch.yetanothernotifier.ui.fragments.NotificationPropertiesFragment
import com.antiglitch.yetanothernotifier.ui.fragments.SettingsFragment
import com.antiglitch.yetanothernotifier.ui.properties.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.ui.properties.getAlignmentForGravity
import com.antiglitch.yetanothernotifier.ui.properties.getOppositeAlignment
import com.antiglitch.yetanothernotifier.ui.properties.getOppositeOrientation
import com.antiglitch.yetanothernotifier.ui.properties.Orientation
import androidx.compose.ui.platform.LocalContext
import com.antiglitch.yetanothernotifier.ui.fragments.OverlayPermissionWarningFragment
import com.antiglitch.yetanothernotifier.ui.theme.YetAnotherNotifierTheme
import androidx.core.view.WindowCompat
import com.antiglitch.yetanothernotifier.ui.fragments.MqttPropertiesFragment
import com.antiglitch.yetanothernotifier.ui.navigation.SettingsNavigation
import com.antiglitch.yetanothernotifier.ui.navigation.SettingsScreen
import com.antiglitch.yetanothernotifier.ui.properties.MqttDiscoveryRepository

class MainActivity : ComponentActivity() {
    // Create state holders for permission statuses
    private val hasOverlayPermission = mutableStateOf(false)
    private val hasInternetPermission = mutableStateOf(false)

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial check for permissions
        checkPermissions()

        setContent {
            YetAnotherNotifierTheme {
                // Permission handling flow
                val showOverlayPermission = remember { mutableStateOf(!hasOverlayPermission.value) }
                val showInternetPermission = remember { mutableStateOf(!hasInternetPermission.value && hasOverlayPermission.value) }
                
                if (hasOverlayPermission.value && hasInternetPermission.value) {
                    // Both permissions granted, show the main screen
                    NotificationPropertiesScreen()
                } else {
                    // At least one permission is missing, show a placeholder screen with permission dialogs
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Yet Another Notifier",
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center
                            )
                            
                            Text(
                                text = "Checking permissions...",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        
                        // Show permission dialogs as needed
                        if (showOverlayPermission.value) {
                            PermissionDialog(
                                permissionType = PermissionType.OVERLAY,
                                title = "Display Over Other Apps",
                                description = "This app needs permission to display notifications over other apps. Please grant this permission in the settings.",
                                onPermissionGranted = {
                                    showOverlayPermission.value = false
                                    hasOverlayPermission.value = true
                                    // Once overlay is granted, show internet permission if needed
                                    showInternetPermission.value = !hasInternetPermission.value
                                },
                                onDismiss = {
                                    // If user dismisses, keep showing the dialog
                                    // Alternatively, you could exit the app here
                                }
                            )
                        } else if (showInternetPermission.value) {
                            PermissionDialog(
                                permissionType = PermissionType.INTERNET,
                                title = "Internet Access Required",
                                description = "This app needs internet access to discover and connect to MQTT servers. Please grant this permission in the settings.",
                                onPermissionGranted = {
                                    showInternetPermission.value = false
                                    hasInternetPermission.value = true
                                },
                                onDismiss = {
                                    // If user dismisses, keep showing the dialog
                                    // Alternatively, you could continue with limited functionality
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when activity resumes (user comes back from settings)
        checkPermissions()
        Log.d("MainActivity", "onResume: Overlay: ${hasOverlayPermission.value}, Internet: ${hasInternetPermission.value}")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Ensure we properly stop any services to prevent background clipboard access
        try {
            // Stop any ongoing discovery
            val discoveryRepository = MqttDiscoveryRepository.getInstance(this)
            discoveryRepository.stopDiscovery()
            
//            // Make sure overlay service is properly stopped if needed
//            if (NotificationOverlayService.isRunning && !isChangingConfigurations) {
//                NotificationOverlayService.stopService(this)
//            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping services", e)
        }
    }

    private fun checkPermissions() {
        // Update the permission states
        hasOverlayPermission.value = PermissionUtil.checkPermission(this, PermissionType.OVERLAY)
        hasInternetPermission.value = PermissionUtil.checkPermission(this, PermissionType.INTERNET)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationPropertiesScreen() {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val notificationProperties by repository.properties.collectAsState()
    val gravity = notificationProperties.gravity
    val margin = notificationProperties.margin
    
    // State for navigation between settings screens
    var currentScreen by remember { mutableStateOf("main") }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        NotificationOverlayService.startService(LocalContext.current)
        
        // Use SettingsNavigation instead of direct fragment composition
        SettingsNavigation(
            initialScreen = when(currentScreen) {
                "notification" -> SettingsScreen.NOTIFICATION_PROPERTIES
                "mqtt" -> SettingsScreen.MQTT_PROPERTIES
                else -> SettingsScreen.MAIN
            },
            onBackPressed = {
                // When back is pressed from main settings, reset to main
                currentScreen = ""
            },
            modifier = Modifier
                .then(
                    when (getOppositeOrientation(gravity)) {
                        Orientation.Horizontal -> Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .align(getOppositeAlignment(gravity))
                        Orientation.Vertical -> Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.8f)
                            .align(getOppositeAlignment(gravity))
                    }
                )
                .padding(8.dp)
        )
    }
}
