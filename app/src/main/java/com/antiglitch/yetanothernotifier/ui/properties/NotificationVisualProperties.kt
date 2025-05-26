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

enum class AspectRatio(val ratio: Float, val displayName: String) {
    ULTRA_WIDE(6.0f, "Ultra Wide"),
    WIDE(4.0f, "Wide"),
    CINEMA(2.35f, "Cinema"),
    STANDARD(1.77f, "16:9"),
    GOLDEN(1.618f, "Golden"),
    CLASSIC(1.33f, "4:3"),
    SQUARE(1.0f, "Square"),
    PORTRAIT(0.75f, "3:4"),
    TALL(0.5f, "Tall"),
    ULTRA_TALL(0.25f, "Ultra Tall")
}

enum class Gravity(val displayName: String) {
    TOP_START("Top Left"),
    TOP_CENTER("Top Center"),
    TOP_END("Top Right"),
    CENTER_START("Middle Left"),
    CENTER("Center"),
    CENTER_END("Middle Right"),
    BOTTOM_START("Bottom Left"),
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_END("Bottom Right");
    
    companion object {
        val gravityGrid = listOf(
            listOf(TOP_START, TOP_CENTER, TOP_END),
            listOf(CENTER_START, CENTER, CENTER_END),
            listOf(BOTTOM_START, BOTTOM_CENTER, BOTTOM_END)
        )
    }
}

sealed class VisualProperties {
    data class Notification(val properties: NotificationVisualProperties) : VisualProperties()
    // Add more component properties here as needed
}
