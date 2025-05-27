package com.antiglitch.yetanothernotifier.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import com.antiglitch.yetanothernotifier.player.ComposeDemoApp
import com.antiglitch.yetanothernotifier.ui.properties.*
import com.antiglitch.yetanothernotifier.player.ExoPlayerFragment

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val properties by repository.properties.collectAsState()

    Column(
        modifier = Modifier
            .size(
                width = properties.width,
                height = properties.height
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = if (properties.roundedCorners) {
                    RoundedCornerShape(properties.cornerRadius)
                } else {
                    RectangleShape
                }
            )
    ) {
        ComposeDemoApp()
    }
}
