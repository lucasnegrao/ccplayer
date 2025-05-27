package com.antiglitch.yetanothernotifier.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.MainScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> TvFriendlyChipsSelect(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    selectedOptions: List<T> = listOf(selectedOption),
    onOptionsSelected: ((List<T>) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val chipFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Helper functions to avoid code duplication
    val handleMainChipToggle: () -> Unit = {
        expanded = !expanded
        if (expanded) {
            MainScope().launch {
                delay(100)
                focusManager.moveFocus(FocusDirection.Down)
            }
        } else {
            chipFocusRequester.requestFocus()
        }
    }
    
    val handleOptionSelection: (T) -> Unit = { option: T ->
        if (multiSelect && onOptionsSelected != null) {
            val isSelected = selectedOptions.contains(option)
            val newSelection = if (isSelected) {
                selectedOptions - option
            } else {
                selectedOptions + option
            }
            onOptionsSelected(newSelection)
        } else {
            onOptionSelected(option)
            expanded = false
            chipFocusRequester.requestFocus()
        }
    }

    // Just use a simple Column with padding
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Single row with title and expandable chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title - non-focusable
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            
            // Expandable chip - this should be the primary focus
            FilterChip(
                selected = true,
                onClick = handleMainChipToggle,
                // Use proper TV-friendly focus settings
                modifier = Modifier
                    .focusRequester(chipFocusRequester)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                chipFocusRequester.requestFocus()
                                handleMainChipToggle()
                            }
                        )
                    },
                colors = FilterChipDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface, // Default non-selected state
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer, // Color when selected (always true for this chip)
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
           
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current selection - smaller and centered text
                    Text(
                        text = if (multiSelect) 
                            "${selectedOptions.size} selected"
                        else 
                            optionLabel(selectedOption),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Dropdown indicator
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp 
                                     else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp) // Smaller icon
                    )
                }
            }
        }
        
        // Expanded options
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // For TV navigation, a simple grid works better than FlowRow
            val chunkedOptions = options.chunked(3)
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunkedOptions.forEachIndexed { rowIndex, rowOptions ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowOptions.forEachIndexed { colIndex, option ->
                            val isSelected = if (multiSelect) {
                                selectedOptions.contains(option)
                            } else {
                                option == selectedOption
                            }
                            
                            // Make sure each chip is properly focusable
                            FilterChip(
                                selected = isSelected,
                                onClick = { handleOptionSelection(option) },
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(option) {
                                        detectTapGestures(
                                            onTap = {
                                                // Request focus first, then handle selection
                                                handleOptionSelection(option)
                                            }
                                        )
                                    },
                                colors = FilterChipDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                            ) {
                                // Centered, single-line text
                                Text(
                                    text = optionLabel(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        // Fill remaining space in row with empty spaces
                        repeat(3 - rowOptions.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            // Done button for multi-select mode
            if (multiSelect) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { expanded = false },
                    modifier = Modifier
                        .align(Alignment.End)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { expanded = false }
                            )
                        }
                ) {
                    Text("Done")
                }
            }
        }
    }
    
    // // Request focus on the chip when component is first displayed
    // LaunchedEffect(Unit) {
    //     chipFocusRequester.requestFocus()
    //     isInitialized = true
    // }
}
