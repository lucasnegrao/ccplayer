package com.antiglitch.yetanothernotifier.ui.properties

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

sealed class ComponentType {
    object Notification : ComponentType()
    // Add more component types here as needed
}

// Ranges for numeric properties
object PropertyRanges {
    val DURATION = 1000L..10000L
    val SCALE = 0.1f..1.0f
    val CORNER_RADIUS = 0.dp..30.dp
    val MARGIN = 0.dp..64.dp // Add reasonable max margin
    
    // Default step sizes for UI controls
    val DURATION_STEP = 500L
    val SCALE_STEP = 0.05f
    val CORNER_RADIUS_STEP = 2.dp
}

data class NotificationVisualProperties(
    val duration: Long = 3000L, // milliseconds
    val scale: Float = 0.5f, // Scale as percentage of screen (0.5 = 50% of screen width)
    val aspect: AspectRatio = AspectRatio.WIDE,
    // Width and height are computed properties based on scale and aspect ratio
    val cornerRadius: Dp = 12.dp,
    val gravity: Gravity = Gravity.TOP_CENTER,
    val roundedCorners: Boolean = true,
    val margin: Dp = 16.dp // <-- Add margin property with default
) {
    // Computed properties
    val width: Dp
        get() = (scale * 1000).dp  // This is just a placeholder - would be calculated from actual screen width
    
    val height: Dp
        get() = (width.value / aspect.ratio).dp
    
    companion object {
        // Validation methods to ensure values stay within ranges
        fun validateDuration(value: Long): Long = 
            value.coerceIn(PropertyRanges.DURATION)
        
        fun validateScale(value: Float): Float =
            value.coerceIn(PropertyRanges.SCALE)
        
        fun validateCornerRadius(value: Dp): Dp =
            value.coerceIn(PropertyRanges.CORNER_RADIUS)
        
        fun validateMargin(value: Dp): Dp =
            value.coerceIn(PropertyRanges.MARGIN)
    }
}

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

// Updated theme options to match available palettes
enum class NotificationTheme(val displayName: String) {
    DEFAULT("Default Dark"),
    LIGHT("Light"),
    MATERIAL_YOU("Material You"), // Dynamic colors on supported devices
    HIGH_CONTRAST("High Contrast"),
    CLASSIC("Classic")
}
