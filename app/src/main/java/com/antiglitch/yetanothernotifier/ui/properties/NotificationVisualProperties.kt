package com.antiglitch.yetanothernotifier.ui.properties

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

sealed class ComponentType {
    object Notification : ComponentType()
    // Add more component types here as needed
}

data class NotificationVisualProperties(
    val duration: Long = 3000L, // milliseconds
    val scale: Float = 1.0f,
    val aspect: AspectRatio = AspectRatio.WIDE,
    val width: Dp = 320.dp,
    val height: Dp = 80.dp,
    val gravity: Gravity = Gravity.TOP_CENTER,
    val roundedCorners: Boolean = true,
    val cornerRadius: Dp = 12.dp
)

enum class AspectRatio(val ratio: Float) {
    SQUARE(1.0f),
    WIDE(4.0f),
    TALL(0.5f)
}

enum class Gravity {
    TOP_START,
    TOP_CENTER,
    TOP_END,
    CENTER_START,
    CENTER,
    CENTER_END,
    BOTTOM_START,
    BOTTOM_CENTER,
    BOTTOM_END
}

sealed class VisualProperties {
    data class Notification(val properties: NotificationVisualProperties) : VisualProperties()
    // Add more component properties here as needed
}
