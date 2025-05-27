package com.antiglitch.yetanothernotifier.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.antiglitch.yetanothernotifier.PermissionType
import com.antiglitch.yetanothernotifier.PermissionUtil

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PermissionDialog(
    permissionType: PermissionType,
    title: String,
    description: String,
    onPermissionGranted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var permissionGranted by remember { 
        mutableStateOf(PermissionUtil.checkPermission(context, permissionType)) 
    }
    
    // Observe permission changes when returning from settings
    PermissionUtil.ObservePermission(
        permissionType = permissionType,
        context = context,
        onPermissionChanged = { isGranted ->
            permissionGranted = isGranted
            if (isGranted) {
                onPermissionGranted()
            }
        }
    )
    
    // If permission is already granted, call the callback immediately
    DisposableEffect(permissionGranted) {
        if (permissionGranted) {
            onPermissionGranted()
        }
        onDispose { }
    }
    
    // Only show the dialog if permission is not granted
    if (!permissionGranted) {
        // Create a focus requester for the settings button
        val buttonFocusRequester = remember { FocusRequester() }
        
        // Get screen width to calculate dialog width
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val dialogWidth = (screenWidth * 0.5f).coerceAtMost(500.dp) // 50% of screen width, max 500dp
        
        // Request focus when dialog appears
        LaunchedEffect(Unit) {
            try {
                buttonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Log error but don't crash if focus request fails
                android.util.Log.e("PermissionDialog", "Error requesting focus", e)
            }
        }
        
        Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            // Replace Card with Column with rounded corners
            Column(
                modifier = Modifier
                    .width(dialogWidth)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title with icon
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Description - avoid selectable text that might trigger clipboard
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Buttons - vertical arrangement to avoid overlap on narrow screens
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!permissionGranted && activity != null) {
                        Button(
                            onClick = {
                                try {
                                    PermissionUtil.navigateToSettings(activity, permissionType)
                                } catch (e: Exception) {
                                    android.util.Log.e("PermissionDialog", "Error navigating to settings", e)
                                }
                            },
                            modifier = Modifier
                                .focusRequester(buttonFocusRequester)
                                .fillMaxWidth(0.8f)
                        ) {
                            Text("Open Settings")
                        }
                    }
                    
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
