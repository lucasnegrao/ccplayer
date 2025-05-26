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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.antiglitch.yetanothernotifier.ui.properties.*

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationPropertiesFragment(
    modifier: Modifier = Modifier
) {
    val repository = VisualPropertiesRepository.getInstance()
    val properties by repository.notificationProperties.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(16.dp)
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
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Duration: ${properties.duration}ms",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = properties.duration.toFloat(),
                    onValueChange = { repository.updateNotificationDuration(it.toLong()) },
                    valueRange = 1000f..10000f,
                    steps = 17
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
                Slider(
                    value = properties.scale,
                    onValueChange = { repository.updateNotificationScale(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 29
                )
            }
        }
        
        // Aspect Ratio Control
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Aspect: ${properties.aspect.displayName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AspectRatio.values().forEach { aspect ->
                        FilterChip(
                            onClick = { repository.updateNotificationAspect(aspect) },
                            label = { Text(aspect.displayName, style = MaterialTheme.typography.labelSmall) },
                            selected = properties.aspect == aspect,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
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
                
                Text(text = "W: ${properties.width}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = properties.width.value,
                    onValueChange = { 
                        repository.updateNotificationSize(it.dp, properties.height)
                    },
                    valueRange = 200f..600f,
                    steps = 39
                )
                
                Text(text = "H: ${properties.height}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = properties.height.value,
                    onValueChange = { 
                        repository.updateNotificationSize(properties.width, it.dp)
                    },
                    valueRange = 60f..300f,
                    steps = 23
                )
            }
        }
        
        // Gravity Control
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Gravity: ${properties.gravity.displayName}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Gravity.gravityGrid.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEach { gravity ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = properties.gravity == gravity,
                                        onClick = { repository.updateNotificationGravity(gravity) }
                                    )
                                    Text(
                                        text = gravity.displayName.replace(" ", "\n"),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                    Slider(
                        value = properties.cornerRadius.value,
                        onValueChange = { repository.updateCornerRadius(it.dp) },
                        valueRange = 0f..50f,
                        steps = 49
                    )
                }
            }
        }
    }
}
