package com.antiglitch.yetanothernotifier

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class PermissionType {
    OVERLAY,
    INTERNET,
    LOCATION,
    BLUETOOTH,
    NOTIFICATION,
    // Add more permission types as needed
}

object PermissionUtil {
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            // On older versions, this permission is granted at install time
            true
        }
    }

    fun checkPermission(context: Context, permissionType: PermissionType): Boolean {
        return when (permissionType) {
            PermissionType.OVERLAY -> canDrawOverlays(context)
            PermissionType.INTERNET -> checkInternetPermission(context)
            PermissionType.LOCATION -> checkLocationPermission(context)
            PermissionType.BLUETOOTH -> checkBluetoothPermission(context)
            PermissionType.NOTIFICATION -> checkNotificationPermission(context)
        }
    }

    private fun checkInternetPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun navigateToSettings(activity: Activity, permissionType: PermissionType) {
        try {
            // Use a different approach that doesn't trigger clipboard access
            val intent = when (permissionType) {
                PermissionType.OVERLAY -> {
                    Intent().apply {
                        action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                        data = Uri.parse("package:${activity.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }
                }

                else -> {
                    Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.parse("package:${activity.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }
                }
            }

            // Ensure we're not carrying any clipboard data
            intent.removeExtra(Intent.EXTRA_TEXT)

            // Log the intent for debugging
            android.util.Log.d(
                "PermissionUtil",
                "Starting settings with action: ${intent.action} and data: ${intent.data}"
            )

            activity.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PermissionUtil", "Error navigating to settings", e)
            // Fallback to a more basic approach
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            } catch (e2: Exception) {
                android.util.Log.e(
                    "PermissionUtil",
                    "Failed to navigate to settings with fallback",
                    e2
                )
            }
        }
    }

    @Composable
    fun ObservePermission(
        permissionType: PermissionType,
        context: Context,
        onPermissionChanged: (Boolean) -> Unit
    ) {
        var hasPermission by remember { mutableStateOf(checkPermission(context, permissionType)) }
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val currentPermission = checkPermission(context, permissionType)
                    if (hasPermission != currentPermission) {
                        hasPermission = currentPermission
                        onPermissionChanged(currentPermission)
                    }
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
}
