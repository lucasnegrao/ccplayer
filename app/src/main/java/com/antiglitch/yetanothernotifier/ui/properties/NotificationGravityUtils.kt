package com.antiglitch.yetanothernotifier.ui.properties

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

enum class Orientation { Horizontal, Vertical }

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
 * Returns a Modifier that applies the correct margin as an offset from the chosen gravity edge,
 * taking into account the card's alignment and true size, and the current scale.
 */
fun Modifier.marginForGravity(gravity: Gravity, margin: Dp, scale: Float): Modifier {
    val scaledMargin = margin * scale
    return this.then(
        when (gravity) {
            Gravity.TOP_START -> Modifier.offset(x = scaledMargin, y = scaledMargin)
            Gravity.TOP_CENTER -> Modifier.offset(y = scaledMargin)
            Gravity.TOP_END -> Modifier.offset(x = -scaledMargin, y = scaledMargin)
            Gravity.CENTER_START -> Modifier.offset(x = scaledMargin)
            Gravity.CENTER -> Modifier
            Gravity.CENTER_END -> Modifier.offset(x = -scaledMargin)
            Gravity.BOTTOM_START -> Modifier.offset(x = scaledMargin, y = -scaledMargin)
            Gravity.BOTTOM_CENTER -> Modifier.offset(y = -scaledMargin)
            Gravity.BOTTOM_END -> Modifier.offset(x = -scaledMargin, y = -scaledMargin)
        }
    )
}
