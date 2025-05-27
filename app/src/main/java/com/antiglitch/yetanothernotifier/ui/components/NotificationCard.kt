package com.antiglitch.yetanothernotifier.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.antiglitch.yetanothernotifier.NotificationOverlayService
import com.antiglitch.yetanothernotifier.player.ExoPlayerComposable
import com.antiglitch.yetanothernotifier.ui.properties.NotificationVisualPropertiesRepository


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val properties by repository.properties.collectAsState()

    val player by NotificationOverlayService.playerControllerState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize() // Changed from .size() to .fillMaxSize()
            .graphicsLayer(alpha = properties.transparency) // Apply transparency here
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = if (properties.roundedCorners) {
                    RoundedCornerShape(properties.cornerRadius)
                } else {
                    RectangleShape
                }
            )
    ) {
        val shape = if (properties.roundedCorners) {
            RoundedCornerShape(properties.cornerRadius)
        } else {
            RectangleShape
        }

        ExoPlayerComposable(
            player = player, // Use the player from the service
            shape = shape
        )
    }
}