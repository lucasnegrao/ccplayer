package com.antiglitch.yetanothernotifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Button
import com.antiglitch.yetanothernotifier.ui.properties.VisualPropertiesRepository
import com.antiglitch.yetanothernotifier.ui.components.NotificationCard
import com.antiglitch.yetanothernotifier.ui.fragments.NotificationPropertiesFragment

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape
            ) {
                NotificationDemo()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationDemo() {
    val repository = VisualPropertiesRepository.getInstance()
    val notificationProperties by repository.notificationProperties.collectAsState()
    var showControls by remember { mutableStateOf(false) }
    
    if (showControls) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Properties Panel (Left Side)
            NotificationPropertiesFragment(
                modifier = Modifier.weight(0.4f)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Notification Preview Area (Right Side)
            Box(modifier = Modifier.weight(0.6f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Preview",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = { showControls = false }) {
                        Text("Back to Demo")
                    }
                }
                
                // Show notification card in the preview area (non-blocking)
                NotificationCard()
            }
        }
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Notification Properties Demo",
                style = MaterialTheme.typography.headlineLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "Duration: ${notificationProperties.duration}ms")
            Text(text = "Scale: ${notificationProperties.scale}")
            Text(text = "Aspect: ${notificationProperties.aspect}")
            Text(text = "Size: ${notificationProperties.width} x ${notificationProperties.height}")
            Text(text = "Gravity: ${notificationProperties.gravity}")
            Text(text = "Rounded Corners: ${notificationProperties.roundedCorners}")
            Text(text = "Corner Radius: ${notificationProperties.cornerRadius}")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = { showControls = true }) {
                Text("Open Live Editor")
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NotificationDemo()
}