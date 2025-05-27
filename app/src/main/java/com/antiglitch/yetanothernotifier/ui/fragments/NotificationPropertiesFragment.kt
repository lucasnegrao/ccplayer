package com.antiglitch.yetanothernotifier.ui.fragments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
//import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.*
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlySlider
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlyChipsSelect
import androidx.compose.foundation.shape.RoundedCornerShape // Import for RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester // Added import
import androidx.compose.ui.focus.focusRequester // Added import
import androidx.compose.ui.focus.onFocusChanged // Added import
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import android.util.Log
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.antiglitch.yetanothernotifier.ui.properties.*
import androidx.compose.foundation.background
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlySwitch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationPropertiesFragment(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val properties by repository.properties.collectAsState()
    val scrollState = rememberScrollState()
    var isGravitySelectorTrigger by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Scroll to top and request initial focus on launch


    // Replace Card with Column
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Add a back button at the top
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        )   {
            IconButton(
                onClick = {
                    Log.d("MqttPropertiesFragment", "Back button clicked, calling onBackPressed")
                    onBackPressed()
                },
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )

            }
            
            Text(
                text = "Properties",
                style = MaterialTheme.typography.headlineMedium
            )

        }

        // Duration Control
        // The Card wrapper is removed. The Column below is now a direct child.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp) // Adjust corner radius as needed
                )
                .padding(12.dp) // Inner padding for the content
        ) { // Added fillMaxWidth, kept padding
            Text(
                text = "Duration: ${properties.duration}ms",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant // Explicitly set text color
            )
            TvFriendlySlider(
                modifier = Modifier
                    .focusRequester(focusRequester)
                ,
                value = properties.duration.toFloat(),
                onValueChange = { repository.updateDuration(it.toLong()) },
                valueRange = 1000f..10000f,
                stepSize = 500f
            )
        }
    LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        
    }
        // Scale Control
        // Replaced Card with a Column having a background modifier
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp) // Adjust corner radius as needed
                )
                .padding(12.dp) // Inner padding for the content
        ) {
            Text(
                text = "Scale: ${String.format("%.2f", properties.scale)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant // Explicitly set text color
            )
            TvFriendlySlider(
                // Remove focusRequester from here - only the first element should have it
                value = properties.scale,
                onValueChange = { repository.updateScale(it) },
                valueRange = 0.1f..1.0f, // Corrected scale range
                stepSize = 0.05f,
                formatValue = { String.format("%.2f", it) }
            )
        }

        // Aspect Ratio Control - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                TvFriendlyChipsSelect(
                    title = "Aspect Ratio",
                    options = AspectRatio.values().toList(),
                    selectedOption = properties.aspect,
                    onOptionSelected = { repository.updateAspect(it) },
                    optionLabel = { it.displayName }
                )
            }
        }


        // Gravity Control - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                TvFriendlyChipsSelect(
                    title = "Gravity",
                    options = Gravity.values().toList(),
                    selectedOption = properties.gravity,
                    onOptionSelected = {  isGravitySelectorTrigger = true
                        repository.updateGravity(it)  },
                    optionLabel = { it.displayName }
                )
            }
        }

        // Corner Controls - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rounded",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TvFriendlySwitch(
                    checked = properties.roundedCorners,
                    onCheckedChange = { repository.updateRoundedCorners(it) }
                )
            }

            if (properties.roundedCorners) {
                Text(
                    text = "Radius: ${properties.cornerRadius}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Adjusted color
                )
                TvFriendlySlider(
                    value = properties.cornerRadius.value,
                    onValueChange = { repository.updateCornerRadius(it.dp) },
                    valueRange = 0f..50f,
                    stepSize = 1f,
                    formatValue = { "${it.toInt()}dp" }
                )
            }
        }

        // Margin control - Replaced Card with Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = "Margin: ${properties.margin.value.toInt()}dp",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TvFriendlySlider(
                value = properties.margin.value,
                onValueChange = {
                    val newMargin = it.dp // No need for a hard-coded range in properties, just clamp here if needed
                    repository.updateMargin(newMargin)
                },
                valueRange = 0f..64f, // Simple and direct, 0 to 64 dp
                stepSize = 2f,
                formatValue = { "${it.toInt()}dp" }
            )
        }

        // Transparency Control
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = "Transparency: ${String.format("%.2f", properties.transparency)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TvFriendlySlider(
                value = properties.transparency,
                onValueChange = { repository.updateTransparency(it) },
                valueRange = PropertyRanges.TRANSPARENCY,
                stepSize = PropertyRanges.TRANSPARENCY_STEP,
                formatValue = { String.format("%.2f", it) }
            )
        }
    }
}
