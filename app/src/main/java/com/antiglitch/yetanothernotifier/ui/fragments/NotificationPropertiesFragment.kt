package com.antiglitch.yetanothernotifier.ui.fragments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Card // Keep this for inner cards
import androidx.tv.material3.CardDefaults // Keep this for inner cards
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import com.antiglitch.yetanothernotifier.R
import com.antiglitch.yetanothernotifier.ui.properties.*
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlySlider
import com.antiglitch.yetanothernotifier.ui.components.TvFriendlyChipsSelect
import androidx.compose.foundation.shape.RoundedCornerShape // Import for RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationPropertiesFragment(
    modifier: Modifier = Modifier
) {
    val repository = VisualPropertiesRepository.getInstance()
    val properties by repository.notificationProperties.collectAsState()

    androidx.tv.material3.Card( // Use androidx.tv.material3.Card for the outer container
        modifier = modifier, // Apply the modifier passed to the fragment here
        shape = CardDefaults.shape(
            shape = if (properties.roundedCorners) {
                RoundedCornerShape(properties.cornerRadius)
            } else {
                RectangleShape
            }
        ),
        onClick = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize() // Column fills the parent Card
                .padding(16.dp) // Internal padding for the content within the Card
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = "Properties",
                style = MaterialTheme.typography.headlineMedium
            )

            // Duration Control
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) { // Corrected: Use local padding
                    Text(
                        text = "Duration: ${properties.duration}ms",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TvFriendlySlider(
                        value = properties.duration.toFloat(),
                        onValueChange = { repository.updateNotificationDuration(it.toLong()) },
                        valueRange = 1000f..10000f,
                        stepSize = 500f
                    )
                }
            }

            // Scale Control
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Scale: ${String.format("%.2f", properties.scale)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TvFriendlySlider(
                        value = properties.scale,
                        onValueChange = { repository.updateNotificationScale(it) },
                        valueRange = 0.1f..1.0f, // Corrected scale range
                        stepSize = 0.05f,
                        formatValue = { String.format("%.2f", it) }
                    )
                }
            }

            // Aspect Ratio Control - Replace with TvFriendlyChipsSelect
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TvFriendlyChipsSelect(
                    title = "Aspect Ratio",
                    options = AspectRatio.values().toList(),
                    selectedOption = properties.aspect,
                    onOptionSelected = { repository.updateNotificationAspect(it) },
                    optionLabel = { it.displayName }
                )
            }

            // Size Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Size",
                        style = MaterialTheme.typography.titleMedium
                    )



                }
            }

            // Gravity Control - Replace with TvFriendlyChipsSelect
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TvFriendlyChipsSelect(
                    title = "Gravity",
                    options = Gravity.values().toList(),
                    selectedOption = properties.gravity,
                    onOptionSelected = { repository.updateNotificationGravity(it) },
                    optionLabel = { it.displayName }
                )
            }

            // Corner Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rounded",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = properties.roundedCorners,
                            onCheckedChange = { repository.updateRoundedCorners(it) }
                        )
                    }

                    if (properties.roundedCorners) {
                        Text(text = "Radius: ${properties.cornerRadius}", style = MaterialTheme.typography.bodySmall)
                        TvFriendlySlider(
                            value = properties.cornerRadius.value,
                            onValueChange = { repository.updateCornerRadius(it.dp) },
                            valueRange = 0f..50f,
                            stepSize = 1f,
                            formatValue = { "${it.toInt()}dp" }
                        )
                    }
                }
            }

            // Margin control
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Margin: ${properties.margin.value.toInt()}dp",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TvFriendlySlider(
                        value = properties.margin.value,
                        onValueChange = {
                            val newMargin = it.dp // No need for a hard-coded range in properties, just clamp here if needed
                            repository.updateNotificationMargin(newMargin)
                        },
                        valueRange = 0f..64f, // Simple and direct, 0 to 64 dp
                        stepSize = 2f,
                        formatValue = { "${it.toInt()}dp" }
                    )
                }
            }
        }
    }
}
