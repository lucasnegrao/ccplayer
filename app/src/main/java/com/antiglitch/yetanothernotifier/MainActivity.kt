package com.antiglitch.yetanothernotifier

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.antiglitch.yetanothernotifier.ui.components.NotificationCard
import com.antiglitch.yetanothernotifier.ui.fragments.NotificationPropertiesFragment
import com.antiglitch.yetanothernotifier.ui.properties.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.ui.properties.getAlignmentForGravity
import com.antiglitch.yetanothernotifier.ui.properties.getOppositeAlignment
import com.antiglitch.yetanothernotifier.ui.properties.getOppositeOrientation
import com.antiglitch.yetanothernotifier.ui.properties.Orientation
import androidx.compose.ui.platform.LocalContext
import com.antiglitch.yetanothernotifier.ui.fragments.OverlayPermissionWarningFragment
import com.antiglitch.yetanothernotifier.ui.theme.YetAnotherNotifierTheme

class MainActivity : ComponentActivity() {
    // Create a state holder for the permission status that can be updated
    private val hasOverlayPermission = mutableStateOf(false)

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make window transparent
        window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // Initial check for permission
        checkOverlayPermission()

        // Remove the manual ViewTree setup - let ComponentActivity handle it

        setContent {
            YetAnotherNotifierTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                ) {
                    // Use the class-level state
                    if (hasOverlayPermission.value) {
                        NotificationPropertiesScreen(
                        )
                    } else {
                        OverlayPermissionWarningFragment()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission when activity resumes (user comes back from settings)
        checkOverlayPermission()
        Log.d("MainActivity", "onResume: Permission status: ${hasOverlayPermission.value}")
    }


    private fun checkOverlayPermission() {
        // Update the permission state
        hasOverlayPermission.value = PermissionUtil.canDrawOverlays(this)
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

    Box(modifier = Modifier.fillMaxSize()) {

        NotificationCard(
            modifier = Modifier
                .align(getAlignmentForGravity(gravity))
                .padding(margin) // Use margin directly
        )

        // Properties panel on the opposite side, less than half screen
        NotificationPropertiesFragment(
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
