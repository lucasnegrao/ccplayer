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
    val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isFocused = sliderInteractionSource.collectIsFocusedAsState()
    val sliderFocusRequester = remember { FocusRequester() }
    
    // Track drag state
    var trackWidth by remember { mutableStateOf(0f) }
    var trackWidthPx by remember { mutableStateOf(0) }
    val handleSizePx = with(LocalDensity.current) { 32.dp.toPx() }
    val halfHandleSizePx = handleSizePx / 2
    
    // Function to convert position to value
    val positionToValue = { position: Float ->
        val newProgress = (position / trackWidth).coerceIn(0f, 1f)
        val exactValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
        
        // Snap to steps
        if (stepSize > 0) {
            val steps = ((exactValue - valueRange.start) / stepSize).roundToInt()
            (valueRange.start + steps * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            exactValue
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Get track width for calculations
            .onGloballyPositioned {
                trackWidth = it.size.width.toFloat()
                trackWidthPx = it.size.width
            }
            // Remove the border when unfocused to fix the thin border issue
            .then(
                if (isFocused.value) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Calculate handle position for filling alignment
        val handlePosition = progress * (trackWidthPx - handleSizePx) + handleSizePx
        val fillRatio = handlePosition / trackWidthPx

        // Filled portion of the track - aligned to handle center
        Box(
            modifier = Modifier
                .fillMaxWidth(fillRatio)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        
        // Value text in the middle of the track
        if (showValueInTrack) {
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Handle with centered positioning and improved focus
        Box(
            modifier = Modifier
                .focusable(interactionSource = sliderInteractionSource)
                .focusRequester(sliderFocusRequester)
                .offset {
                    // Position handle so it moves correctly along the track
                    val handleLeftEdgePosition = (progress * (trackWidthPx - handleSizePx)).roundToInt()
                    IntOffset(handleLeftEdgePosition, 0)
                }
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
                .size(32.dp)
                .padding(4.dp)
                .clip(CircleShape)
                .background(
                    if (isFocused.value) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.secondary
                )
                .border(
                    width = 2.dp,
                    color = if (isFocused.value) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.onSecondary,
                    shape = CircleShape
                )
        )
    }
}

