package com.antiglitch.yetanothernotifier.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
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
    showValueInTrack: Boolean = true
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
    val halfHandleSizePx = handleSizePx / 2
    
    // Convert touch/pointer position to slider value
    val positionToValue = { position: Float ->
        val newProgress = (position / trackWidth).coerceIn(0f, 1f)
        val exactValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
        
        if (stepSize > 0) {
            val steps = ((exactValue - valueRange.start) / stepSize).roundToInt()
            (valueRange.start + steps * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            exactValue
        }
    }
    
    // Main slider container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface)
            .onGloballyPositioned {
                trackWidth = it.size.width.toFloat()
                trackWidthPx = it.size.width
            }
            // Use a standard border that doesn't change the internal layout
            .border(
                width = if (isFocused.value) 2.dp else 0.dp,
                color = if (isFocused.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (trackWidthPx > 0) {
            // Calculate handle position independent of focus state
            val adjustedTrackWidth = maxOf(trackWidthPx, handleSizePx.toInt())
            val maxOffset = maxOf(0, adjustedTrackWidth - handleSizePx.toInt())
            
            // Calculate handle position based solely on progress value
            val handleLeftEdge = (progress * maxOffset).roundToInt()
            val handleRightEdge = handleLeftEdge + handleSizePx
            val fillRatio = (handleRightEdge / adjustedTrackWidth).coerceIn(0f, 1f)

            // TRACK FILL: Colored portion that represents progress
            // Force a separate composable to ensure proper clipping
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillRatio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSecondary)
            )
            
            // VALUE TEXT: Numerical display in center of track
            if (showValueInTrack) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // HANDLE CONTAINER: Position is calculated independent of focus state
            Box(
                modifier = Modifier.offset {
                    IntOffset(handleLeftEdge, 0)
                }
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
                                            onValueChange(minOf(value + stepSize, valueRange.endInclusive))
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
                        // Add drag and tap support for mouse/touch
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newValue = positionToValue(offset.x)
                                onValueChange(newValue)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newValue = positionToValue(change.position.x)
                                onValueChange(newValue)
                            }
                        }
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
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

