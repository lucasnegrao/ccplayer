package com.antiglitch.yetanothernotifier.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.roundToInt

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFriendlySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float,
    modifier: Modifier = Modifier,
    formatValue: (Float) -> String = { it.toString() },
    showValueInTrack: Boolean = true,
    fillBackgroundColor: Color = MaterialTheme.colorScheme.secondary,
    handleColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    surfaceColor: Color = MaterialTheme.colorScheme.surface
) {
    // Calculate current progress percentage (0-1)
    val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    // Focus management
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isFocused = sliderInteractionSource.collectIsFocusedAsState()
    val sliderFocusRequester = remember { FocusRequester() }

    // Track dimensions and handle measurements
    var trackWidth by remember { mutableStateOf(0f) }
    var trackWidthPx by remember { mutableStateOf(0) }
    val handleSizePx = with(LocalDensity.current) { 32.dp.toPx() }

    // Track drag state
    var isDragging by remember { mutableStateOf(false) }
    var dragPointerPositionOnTrack by remember { mutableStateOf(0f) }

    // Convert touch/pointer position to slider value
    val positionToValue = { position: Float ->
        val newProgress = (position / trackWidth).coerceIn(0f, 1f)
        val exactValue =
            valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)

        if (stepSize > 0) {
            val steps = ((exactValue - valueRange.start) / stepSize).roundToInt()
            (valueRange.start + steps * stepSize).coerceIn(
                valueRange.start,
                valueRange.endInclusive
            )
        } else {
            exactValue
        }
    }

    // Convert absolute position to value (for track clicks)
    val absolutePositionToValue = { absoluteX: Float ->
        positionToValue(absoluteX)
    }

    // Main slider container - OuterBox
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp), // Provides space for glow vertically
        contentAlignment = Alignment.CenterStart
    ) {
        // InnerBox - Represents the visual track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor)
                .onGloballyPositioned {
                    trackWidth = it.size.width.toFloat()
                    trackWidthPx = it.size.width
                }
                .border(
                    width = if (isFocused.value) 2.dp else 0.dp,
                    color = if (isFocused.value) MaterialTheme.colorScheme.primary else surfaceColor,
                    shape = RoundedCornerShape(16.dp)
                )
                // Add click support to the entire track
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newValue = absolutePositionToValue(offset.x)
                        onValueChange(newValue)
                        sliderFocusRequester.requestFocus()
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            if (trackWidthPx > 0) {
                val adjustedTrackWidthInner = maxOf(trackWidthPx, handleSizePx.toInt())
                val maxOffsetInner = maxOf(0, adjustedTrackWidthInner - handleSizePx.toInt())
                val handleLeftEdgeInner = (progress * maxOffsetInner).roundToInt()
                val handleRightEdgeInner = handleLeftEdgeInner + handleSizePx
                val fillRatio = (handleRightEdgeInner / adjustedTrackWidthInner).coerceIn(0f, 1f)

                // TRACK FILL: Colored portion that represents progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillRatio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp)) // Clip fill to track shape
                        .background(fillBackgroundColor)
                )

                // VALUE TEXT: Numerical display in center of track
                if (showValueInTrack) {
                    Text(
                        text = formatValue(value),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor
                    )
                }
            }
        }

        // Handle components are children of OuterBox, sibling to InnerBox (track)
        if (trackWidthPx > 0) {
            // Calculate the target left edge for the 32.dp handle
            val adjustedTrackWidth = maxOf(trackWidthPx, handleSizePx.toInt())
            val maxOffset = maxOf(0, adjustedTrackWidth - handleSizePx.toInt())
            val handleTargetLeftEdgePx = (progress * maxOffset).roundToInt()

            // Calculate the offset needed for the handle within its larger container (for the glow)
            // handleSizePx is the 32.dp handle, handleContainerSizeForGlowPx is the 48.dp container
            val handleContainerSizeForGlowPx = with(LocalDensity.current) { 48.dp.toPx() }
            // This is the space on one side of the handle inside its container, e.g., 8.dp.toPx()
            val handleVisualCenteringOffsetPx = (handleContainerSizeForGlowPx - handleSizePx) / 2f

            // HANDLE CONTAINER: Position is calculated independent of focus state
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            // Adjust the container's offset so the 32.dp handle inside it aligns correctly
                            (handleTargetLeftEdgePx - handleVisualCenteringOffsetPx).roundToInt(),
                            0
                        )
                    }
                    .size(48.dp) // Ensure the container size is fixed and large enough for the glow
                    .align(Alignment.CenterStart) // Align with the InnerBox (track) vertically
            ) {
                // FOCUS GLOW: Translucent highlight around handle when focused
                // Focus effect should only change appearance, not position
                if (isFocused.value) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            .align(Alignment.Center)
                    )
                }

                // HANDLE: Interactive draggable element
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .focusable(interactionSource = sliderInteractionSource)
                        .focusRequester(sliderFocusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionRight -> {
                                        if (value < valueRange.endInclusive) {
                                            onValueChange(
                                                minOf(
                                                    value + stepSize,
                                                    valueRange.endInclusive
                                                )
                                            )
                                        }
                                        true
                                    }

                                    Key.DirectionLeft -> {
                                        if (value > valueRange.start) {
                                            onValueChange(maxOf(value - stepSize, valueRange.start))
                                        }
                                        true
                                    }

                                    else -> false
                                }
                            } else false
                        }
                        // Fix drag calculation - track absolute position
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    sliderFocusRequester.requestFocus()
                                    isDragging = true
                                    // Calculate the initial absolute pointer position on the track
                                    dragPointerPositionOnTrack = handleTargetLeftEdgePx + offset.x
                                },
                                onDragEnd = {
                                    isDragging = false
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                // Update the absolute pointer position on the track
                                dragPointerPositionOnTrack += dragAmount.x
                                // Coerce the position to be within track bounds
                                val newPositionOnTrack =
                                    dragPointerPositionOnTrack.coerceIn(0f, trackWidth)
                                val newValue = positionToValue(newPositionOnTrack)
                                onValueChange(newValue)
                            }
                        }
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(handleColor)
                        .border(
                            width = 0.dp,
                            color = MaterialTheme.colorScheme.onSecondary,
                            shape = CircleShape
                        )
                        .align(Alignment.Center)
                )
            }
        }
    }
}

