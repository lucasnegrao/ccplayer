package com.antiglitch.yetanothernotifier.utils

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.antiglitch.yetanothernotifier.data.properties.Gravity
import android.view.Gravity as AndroidGravity

object NotificationUtils {
    // This function must be called from within a BoxScope
    fun BoxScope.alignModifier(gravity: Gravity): Modifier {
        return Modifier.align(getAlignmentForGravity(gravity))
    }

    fun getAlignmentForGravity(gravity: Gravity): Alignment {
        return when (gravity) {
            Gravity.TOP_START -> Alignment.TopStart
            Gravity.TOP_CENTER -> Alignment.TopCenter
            Gravity.TOP_END -> Alignment.TopEnd
            Gravity.CENTER_START -> Alignment.CenterStart
            Gravity.CENTER -> Alignment.Center
            Gravity.CENTER_END -> Alignment.CenterEnd
            Gravity.BOTTOM_START -> Alignment.BottomStart
            Gravity.BOTTOM_CENTER -> Alignment.BottomCenter
            Gravity.BOTTOM_END -> Alignment.BottomEnd
        }
    }

    // Enum for orientation, used for settings panel placement
    enum class Orientation { Horizontal, Vertical }

    fun getOppositeAlignment(gravity: Gravity): Alignment {
        return when (gravity) {
            Gravity.TOP_START -> Alignment.BottomEnd
            Gravity.TOP_CENTER -> Alignment.BottomCenter
            Gravity.TOP_END -> Alignment.BottomStart
            Gravity.CENTER_START -> Alignment.CenterEnd
            Gravity.CENTER -> Alignment.CenterEnd // fallback
            Gravity.CENTER_END -> Alignment.CenterStart
            Gravity.BOTTOM_START -> Alignment.TopEnd
            Gravity.BOTTOM_CENTER -> Alignment.TopCenter
            Gravity.BOTTOM_END -> Alignment.TopStart
        }
    }

    fun getOppositeOrientation(gravity: Gravity): Orientation {
        return when (gravity) {
            Gravity.TOP_CENTER,
            Gravity.BOTTOM_CENTER -> Orientation.Vertical

            Gravity.CENTER -> Orientation.Horizontal // fallback
            else -> Orientation.Horizontal
        }
    }

    /**
     * Converts the app's custom Gravity type to Android's system Gravity int constants
     */
    fun Gravity.toAndroidGravity(): Int {
        return when (this) {
            Gravity.TOP_START -> AndroidGravity.TOP or AndroidGravity.START
            Gravity.TOP_CENTER -> AndroidGravity.TOP or AndroidGravity.CENTER_HORIZONTAL
            Gravity.TOP_END -> AndroidGravity.TOP or AndroidGravity.END
            Gravity.CENTER_START -> AndroidGravity.CENTER_VERTICAL or AndroidGravity.START
            Gravity.CENTER -> AndroidGravity.CENTER
            Gravity.CENTER_END -> AndroidGravity.CENTER_VERTICAL or AndroidGravity.END
            Gravity.BOTTOM_START -> AndroidGravity.BOTTOM or AndroidGravity.START
            Gravity.BOTTOM_CENTER -> AndroidGravity.BOTTOM or AndroidGravity.CENTER_HORIZONTAL
            Gravity.BOTTOM_END -> AndroidGravity.BOTTOM or AndroidGravity.END
        }
    }

    /**
     * Returns a Modifier that applies the correct margin as an offset from the chosen gravity edge,
     * taking into account the card's alignment and true size, and the current scale.
     */
    fun Modifier.marginForGravity(gravity: Gravity, margin: Dp, scale: Float = 1f): Modifier {
        val scaledMargin = margin * scale
        return this.then(
            when (gravity) {
                Gravity.TOP_START -> Modifier.offset(x = scaledMargin, y = scaledMargin)
                Gravity.TOP_CENTER -> Modifier.offset(y = scaledMargin)
                Gravity.TOP_END -> Modifier.offset(x = -scaledMargin, y = scaledMargin)
                Gravity.CENTER_START -> Modifier.offset(x = scaledMargin)
                Gravity.CENTER -> Modifier // No offset for true center
                Gravity.CENTER_END -> Modifier.offset(x = -scaledMargin)
                Gravity.BOTTOM_START -> Modifier.offset(x = scaledMargin, y = -scaledMargin)
                Gravity.BOTTOM_CENTER -> Modifier.offset(y = -scaledMargin)
                Gravity.BOTTOM_END -> Modifier.offset(x = -scaledMargin, y = -scaledMargin)
            }
        )
    }

    /**
     * Returns a Pair of (x, y) pixel offsets for WindowManager.LayoutParams based on gravity and margin.
     */
    fun getOffsetsForGravity(gravity: Gravity, marginPx: Int): Pair<Int, Int> {
        return when (gravity) {
            Gravity.TOP_START -> marginPx to marginPx
            Gravity.TOP_CENTER -> 0 to marginPx
            Gravity.TOP_END -> -marginPx to marginPx
            Gravity.CENTER_START -> marginPx to 0
            Gravity.CENTER -> 0 to 0 // No offset for true center
            Gravity.CENTER_END -> -marginPx to 0
            Gravity.BOTTOM_START -> marginPx to -marginPx
            Gravity.BOTTOM_CENTER -> 0 to -marginPx
            Gravity.BOTTOM_END -> -marginPx to -marginPx
        }
    }
}
