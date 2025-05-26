package com.antiglitch.yetanothernotifier.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.antiglitch.yetanothernotifier.ui.properties.NotificationVisualPropertiesRepository


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val properties by repository.properties.collectAsState()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .size(
                    width = properties.width,
                    height = properties.height
                )
                .wrapContentHeight(),
            shape = if (properties.roundedCorners) {
                RoundedCornerShape(properties.cornerRadius)
            } else {
                RectangleShape
            },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Notification Dialog",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Column {
                    Text(text = "Duration: ${properties.duration}ms")
                    Text(text = "Scale: ${properties.scale}")
                    Text(text = "Rounded: ${properties.roundedCorners}")
                    if (properties.roundedCorners) {
                        Text(text = "Radius: ${properties.cornerRadius}")
                    }
                }
                
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
