package com.antiglitch.yetanothernotifier

import android.os.Bundle
import android.util.Log
import android.view.Gravity
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.antiglitch.yetanothernotifier.ui.components.NotificationCard
import com.antiglitch.yetanothernotifier.ui.components.PermissionDialog
import com.antiglitch.yetanothernotifier.ui.navigation.SettingsNavigation
import com.antiglitch.yetanothernotifier.ui.navigation.SettingsScreen
import com.antiglitch.yetanothernotifier.data.repository.MqttDiscoveryRepository
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.utils.NotificationUtils // Updated import
import com.antiglitch.yetanothernotifier.utils.NotificationUtils.getAlignmentForGravity // Specific import
import com.antiglitch.yetanothernotifier.utils.NotificationUtils.getOppositeAlignment // Specific import
import com.antiglitch.yetanothernotifier.utils.NotificationUtils.getOppositeOrientation // Specific import
import com.antiglitch.yetanothernotifier.utils.NotificationUtils.marginForGravity // Specific import
import com.antiglitch.yetanothernotifier.utils.NotificationUtils.toAndroidGravity // Specific import
import com.antiglitch.yetanothernotifier.ui.theme.YetAnotherNotifierTheme
import com.antiglitch.yetanothernotifier.utils.PermissionType
import com.antiglitch.yetanothernotifier.utils.PermissionUtil

class MainActivity : ComponentActivity() {
    // Create state holders for permission statuses
    private val hasOverlayPermission = mutableStateOf(false)
    private val hasInternetPermission = mutableStateOf(false)

    // Use ServiceOrchestrator instead of managing services directly
    private lateinit var serviceOrchestrator: ServiceOrchestrator

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize service orchestrator
        serviceOrchestrator = ServiceOrchestrator.getInstance(this)

        // Initial check for permissions
        checkPermissions()

        setContent {
            YetAnotherNotifierTheme {
                // Permission handling flow
                val showOverlayPermission = remember { mutableStateOf(!hasOverlayPermission.value) }
                val showInternetPermission =
                    remember { mutableStateOf(!hasInternetPermission.value && hasOverlayPermission.value) }

                if (hasOverlayPermission.value && hasInternetPermission.value) {
                    // Both permissions granted, show the main screen
                    NotificationPropertiesScreen()

                    // Initialize services through orchestrator
                    LaunchedEffect(Unit) {
                        serviceOrchestrator.initialize()
                    }
                } else {
                    // At least one permission is missing, show permission dialogs
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
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
                                    // Reinitialize services after permission granted
                                    serviceOrchestrator.reinitialize()
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
                                    // Reinitialize services after permission granted
                                    serviceOrchestrator.reinitialize()
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
        serviceOrchestrator.onAppForeground()
        Log.d("MainActivity", "onResume: Permissions checked, orchestrator notified")
    }

    override fun onPause() {
        super.onPause()
        serviceOrchestrator.onAppBackground()
        Log.d("MainActivity", "onPause: Orchestrator notified")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Let orchestrator handle cleanup
        if (!isChangingConfigurations) {
            serviceOrchestrator.shutdown()
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
    val gravityValue =
        notificationProperties.gravity // Renamed from 'gravity' to avoid conflict with android.view.Gravity
    val marginDp = notificationProperties.margin // Renamed from 'margin'

    // State for navigation between settings screens
    var currentScreen by remember { mutableStateOf("main") }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Start the service regardless; it will manage its own overlay visibility
        // NotificationOverlayService.startService(LocalContext.current) // Removed from here

        // If the app is in the foreground, render the NotificationCard directly
        // The service will not show its overlay if appForegroundState.value is true.
        val isAppInForeground by OverlayService.appForegroundState.collectAsState()
        if (isAppInForeground) {
            val cardAlignment = NotificationUtils.getAlignmentForGravity(gravityValue)

            // Use the width and height from notificationProperties (which are Dp)
            val cardWidth = notificationProperties.width
            val cardHeight = notificationProperties.height

            Box( // Outer Box to provide alignment context
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(cardAlignment) // Align this box (which wraps the card)
                        .marginForGravity(gravityValue, marginDp) // Apply offset using consolidated util
                        .width(cardWidth)  // Apply calculated width from properties
                        .height(cardHeight) // Apply calculated height from properties
                ) {
                    NotificationCard() // Render directly in the activity
                }
            }
        }

        // Use SettingsNavigation instead of direct fragment composition
        SettingsNavigation(
            initialScreen = when (currentScreen) {
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
                    when (NotificationUtils.getOppositeOrientation(gravityValue)) {
                        NotificationUtils.Orientation.Horizontal -> Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .align(NotificationUtils.getOppositeAlignment(gravityValue))

                        NotificationUtils.Orientation.Vertical -> Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.8f)
                            .align(NotificationUtils.getOppositeAlignment(gravityValue))
                    }
                )
                .padding(8.dp)
        )
    }
}
