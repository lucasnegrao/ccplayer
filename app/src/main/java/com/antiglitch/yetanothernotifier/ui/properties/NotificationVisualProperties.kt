package com.antiglitch.yetanothernotifier.ui.properties

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

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

@Serializable
data class NotificationVisualProperties(
    val duration: Long = 3000L, // milliseconds
    val scale: Float = 0.5f, // Scale as percentage of screen (0.5 = 50% of screen width)
    val aspect: AspectRatio = AspectRatio.WIDE,
    // Width and height are computed properties based on scale and aspect ratio
    @Serializable(with = DpSerializer::class)
    val cornerRadius: Dp = 12.dp,
    val gravity: Gravity = Gravity.TOP_CENTER,
    val roundedCorners: Boolean = true,
    @Serializable(with = DpSerializer::class)
    val margin: Dp = 16.dp // <-- Add margin property with default
) {
    // Screen dimensions for calculations (set by the service)
    @kotlinx.serialization.Transient
    var screenWidthDp: Float = DEFAULT_SCREEN_WIDTH_DP
    @kotlinx.serialization.Transient
    var screenHeightDp: Float = DEFAULT_SCREEN_HEIGHT_DP
    
    // Computed properties using actual screen dimensions
    val width: Dp
        get() = getSize(screenWidthDp, screenHeightDp).first
    
    val height: Dp
        get() = getSize(screenWidthDp, screenHeightDp).second
    
    // Function to set screen dimensions
    fun withScreenDimensions(widthDp: Float, heightDp: Float): NotificationVisualProperties {
        return this.apply {
            screenWidthDp = widthDp
            screenHeightDp = heightDp
        }
    }
    
    fun getSize(screenWidthDp: Float, screenHeightDp: Float): Pair<Dp, Dp> {
        // Debug logging
        println("DEBUG: getSize called with screenWidth=$screenWidthDp, screenHeight=$screenHeightDp, scale=$scale, aspect=${aspect.ratio}")
        
        // Determine if this is a portrait aspect ratio (height > width)
        val isPortraitRatio = aspect.ratio < 1.0f
        
        val finalWidth: Float
        val finalHeight: Float
        
        if (isPortraitRatio) {
            // For portrait ratios, scale applies to height
            val targetHeight = screenHeightDp * scale // Remove 0.9f multiplier
            val calculatedWidth = targetHeight * aspect.ratio
            
            // Check if width fits (leave small margin for safety)
            val maxWidth = screenWidthDp * 0.95f
            if (calculatedWidth > maxWidth) {
                finalWidth = maxWidth
                finalHeight = finalWidth / aspect.ratio
            } else {
                finalWidth = calculatedWidth
                finalHeight = targetHeight
            }
        } else {
            // For landscape ratios, scale applies to width
            val targetWidth = screenWidthDp * scale // Remove 0.9f multiplier
            val calculatedHeight = targetWidth / aspect.ratio
            
            // Check if height fits (leave small margin for safety)
            val maxHeight = screenHeightDp * 0.95f
            if (calculatedHeight > maxHeight) {
                finalHeight = maxHeight
                finalWidth = finalHeight * aspect.ratio
            } else {
                finalWidth = targetWidth
                finalHeight = calculatedHeight
            }
        }
        
        println("DEBUG: Final dimensions: width=$finalWidth, height=$finalHeight")
        return finalWidth.dp to finalHeight.dp
    }
    
    companion object {
        private const val DEFAULT_SCREEN_WIDTH_DP = 360f // Typical Android screen width
        private const val DEFAULT_SCREEN_HEIGHT_DP = 640f // Typical Android screen height
        
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

@Serializable
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

@Serializable
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

// Custom serializer for Dp
@Serializable
private class DpSerializer : kotlinx.serialization.KSerializer<Dp> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("Dp", kotlinx.serialization.descriptors.PrimitiveKind.FLOAT)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Dp) = encoder.encodeFloat(value.value)
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Dp = decoder.decodeFloat().dp
}

