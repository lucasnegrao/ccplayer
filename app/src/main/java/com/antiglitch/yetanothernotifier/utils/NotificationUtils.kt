package com.antiglitch.yetanothernotifier.utils

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.antiglitch.yetanothernotifier.data.properties.Gravity

object NotificationUtils {
    // This function must be called from within a BoxScope
    fun BoxScope.alignModifier(gravity: Gravity): Modifier {
        return when (gravity) {
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
    }


    // Optionally, move gravity alignment helpers here from NotificationGravityUtils if not already done
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
}
