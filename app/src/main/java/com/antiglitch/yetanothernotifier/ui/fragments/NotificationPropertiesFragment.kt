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
import com.antiglitch.yetanothernotifier.ui.properties.*

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationPropertiesFragment(
    onBack: () -> Unit
) {
    val repository = VisualPropertiesRepository.getInstance()
    val properties by repository.notificationProperties.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notification Properties",
                style = MaterialTheme.typography.headlineMedium
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }
        
        Divider()
        
        // Duration Control
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aspect Ratio: ${properties.aspect}",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspectRatio.values().forEach { aspect ->
                        FilterChip(
                            onClick = { repository.updateNotificationAspect(aspect) },
                            label = { Text(aspect.name) },
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Size",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // Width
                Text(text = "Width: ${properties.width}")
                Slider(
                    value = properties.width.value,
                    onValueChange = { 
                        repository.updateNotificationSize(it.dp, properties.height)
                    },
                    valueRange = 200f..600f,
                    steps = 39
                )
                
                // Height
                Text(text = "Height: ${properties.height}")
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Gravity: ${properties.gravity}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // Create a 3x3 grid for gravity options
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val gravityRows = listOf(
                        listOf(Gravity.TOP_START, Gravity.TOP_CENTER, Gravity.TOP_END),
                        listOf(Gravity.CENTER_START, Gravity.CENTER, Gravity.CENTER_END),
                        listOf(Gravity.BOTTOM_START, Gravity.BOTTOM_CENTER, Gravity.BOTTOM_END)
                    )
                    
                    gravityRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row.forEach { gravity ->
                                FilterChip(
                                    onClick = { repository.updateNotificationGravity(gravity) },
                                    label = { 
                                        Text(
                                            text = gravity.name.replace("_", " "),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    selected = properties.gravity == gravity,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Corner Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rounded Corners",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = properties.roundedCorners,
                        onCheckedChange = { repository.updateRoundedCorners(it) }
                    )
                }
                
                if (properties.roundedCorners) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Corner Radius: ${properties.cornerRadius}")
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
