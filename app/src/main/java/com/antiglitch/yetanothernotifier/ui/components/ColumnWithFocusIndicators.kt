package com.antiglitch.yetanothernotifier.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme


@OptIn(ExperimentalFoundationApi::class) // For focusable
@Composable
fun ColumnWithFocusIndicators(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Glow properties - customize these
    val glowColorFocused = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val glowColorUnfocused = Color.Transparent
    val glowElevationFocused = 8.dp
    val glowElevationUnfocused = 0.dp
    val glowSpreadFocused = 6.dp // How much the glow extends beyond the component
    val glowSpreadUnfocused = 0.dp
    val glowCornerRadius = 8.dp // Corner radius for the glow shape

    val animatedGlowColor by animateColorAsState(
        targetValue = if (isFocused) glowColorFocused else glowColorUnfocused,
        label = "columnGlowColorAnimation"
    )
    val animatedGlowElevation by animateDpAsState(
        targetValue = if (isFocused) glowElevationFocused else glowElevationUnfocused,
        label = "columnGlowElevationAnimation"
    )
    val animatedGlowSpread by animateDpAsState(
        targetValue = if (isFocused) glowSpreadFocused else glowSpreadUnfocused,
        label = "columnGlowSpreadAnimation"
    )

    val glowModifier = if (animatedGlowColor != Color.Transparent) {
        Modifier.drawBehind {
            val columnWidth = size.width
            val columnHeight = size.height
            val glowPaint = Paint().asFrameworkPaint().apply {
                color = animatedGlowColor.toArgb()
                isAntiAlias = true
                setShadowLayer(animatedGlowElevation.toPx(), 0f, 0f, animatedGlowColor.toArgb())
            }

            val spreadPx = animatedGlowSpread.toPx()
            val cornerRadiusPx = glowCornerRadius.toPx()

            val glowPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = -spreadPx,
                        top = -spreadPx,
                        right = columnWidth + spreadPx,
                        bottom = columnHeight + spreadPx,
                        topLeftCornerRadius = CornerRadius(cornerRadiusPx),
                        topRightCornerRadius = CornerRadius(cornerRadiusPx),
                        bottomLeftCornerRadius = CornerRadius(cornerRadiusPx),
                        bottomRightCornerRadius = CornerRadius(cornerRadiusPx)
                    )
                )
            }

            // Fix: Use nativeCanvas to draw with Android's Paint
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(glowPath.asAndroidPath(), glowPaint)
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            // Make focusable but allow children to receive focus properly
            .focusable(
                enabled = true,
                interactionSource = interactionSource
            )
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    println("Column gained focus - allowing child focus to proceed")
                } else if (focusState.hasFocus) {
                    println("Column has focus via descendant")
                } else {
                    println("Column lost focus")
                }
            }
            .then(glowModifier) // Apply the glow drawing logic
            // Reduce padding to not interfere with child components' focus indicators
            .padding(if (isFocused) 2.dp else 0.dp),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}
