package com.antiglitch.yetanothernotifier.ui.components

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
import com.antiglitch.yetanothernotifier.ui.properties.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationCard(
    modifier: Modifier = Modifier
) {
    val repository = VisualPropertiesRepository.getInstance()
    val properties by repository.notificationProperties.collectAsState()
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Card(
            modifier = Modifier
                .size(
                    width = properties.width,
                    height = properties.height
                )
                .graphicsLayer(
                    scaleX = properties.scale,
                    scaleY = properties.scale
                )
                .then(
                    when (properties.gravity) {
                        Gravity.TOP_START -> Modifier.align(Alignment.TopStart)
                        Gravity.TOP_CENTER -> Modifier.align(Alignment.TopCenter)
                        Gravity.TOP_END -> Modifier.align(Alignment.TopEnd)
                        Gravity.CENTER_START -> Modifier.align(Alignment.CenterStart)
                        Gravity.CENTER -> Modifier.align(Alignment.Center)
                        Gravity.CENTER_END -> Modifier.align(Alignment.CenterEnd)
                        Gravity.BOTTOM_START -> Modifier.align(Alignment.BottomStart)
                        Gravity.BOTTOM_CENTER -> Modifier.align(Alignment.BottomCenter)
                        Gravity.BOTTOM_END -> Modifier.align(Alignment.BottomEnd)
                    }
                ),
            shape = CardDefaults.shape(
                shape = if (properties.roundedCorners) {
                    RoundedCornerShape(properties.cornerRadius)
                } else {
                    RectangleShape
                }
            ),
            onClick = {
                // Handle click event if needed
            },
            border = CardDefaults.border()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Notification",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${properties.duration}ms",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
