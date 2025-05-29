package com.antiglitch.yetanothernotifier.ui.components

import android.webkit.WebView
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.antiglitch.yetanothernotifier.OverlayService
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val properties by repository.properties.collectAsState()

    val player by OverlayService.playerControllerState.collectAsState()

    val shape = if (properties.roundedCorners) {
        RoundedCornerShape(properties.cornerRadius)
    } else {
        RectangleShape
    }

    // Remove Column wrapper and apply shape/transparency directly to HybridPlayer
    HybridPlayerComposable(
        mediaController = player,
        shape = shape,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                alpha = properties.transparency
            )
    )
}