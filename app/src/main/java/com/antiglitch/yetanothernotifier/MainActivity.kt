package com.antiglitch.yetanothernotifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.tv.material3.Text
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
import com.antiglitch.yetanothernotifier.ui.theme.YetAnotherNotifierTheme
import com.antiglitch.yetanothernotifier.ui.properties.VisualPropertiesRepository
import com.antiglitch.yetanothernotifier.ui.components.NotificationDialog
import com.antiglitch.yetanothernotifier.ui.fragments.NotificationPropertiesFragment

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YetAnotherNotifierTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    NotificationDemo()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationDemo() {
    val repository = VisualPropertiesRepository.getInstance()
    val notificationProperties by repository.notificationProperties.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var showPropertiesFragment by remember { mutableStateOf(false) }
    
    if (showPropertiesFragment) {
        NotificationPropertiesFragment(
            onBack = { showPropertiesFragment = false }
        )
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Notification Properties Demo")
            Text(text = "Duration: ${notificationProperties.duration}ms")
            Text(text = "Scale: ${notificationProperties.scale}")
            Text(text = "Aspect: ${notificationProperties.aspect}")
            Text(text = "Size: ${notificationProperties.width} x ${notificationProperties.height}")
            Text(text = "Gravity: ${notificationProperties.gravity}")
            Text(text = "Rounded Corners: ${notificationProperties.roundedCorners}")
            Text(text = "Corner Radius: ${notificationProperties.cornerRadius}")
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = { showDialog = true }) {
                Text("Show Notification Dialog")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(onClick = { showPropertiesFragment = true }) {
                Text("Edit Properties")
            }
            
            if (showDialog) {
                NotificationDialog(
                    onDismiss = { showDialog = false }
                )
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
    YetAnotherNotifierTheme {
        NotificationDemo()
    }
}