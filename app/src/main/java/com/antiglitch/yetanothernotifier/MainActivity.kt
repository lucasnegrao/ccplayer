package com.antiglitch.yetanothernotifier

import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.antiglitch.yetanothernotifier.ui.theme.YetAnotherNotifierTheme
import com.antiglitch.yetanothernotifier.ui.properties.getAlignmentForGravity
import com.antiglitch.yetanothernotifier.ui.properties.getOppositeAlignment
import com.antiglitch.yetanothernotifier.ui.properties.getOppositeOrientation
import com.antiglitch.yetanothernotifier.ui.properties.Orientation
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YetAnotherNotifierTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    // Explicitly set surface color to ensure dark background
//                    color = MaterialTheme.colorScheme.background
                ) {
                    NotificationPropertiesScreen()
                    
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationPropertiesScreen() {
    val repository = VisualPropertiesRepository.getInstance()
    val notificationProperties by repository.notificationProperties.collectAsState()
    val gravity = notificationProperties.gravity
    val margin = notificationProperties.margin
    
    // Log gravity value to verify it's changing
    Log.d("NotificationScreen", "Current gravity: ${gravity.name}, Margin: $margin")

    Box(modifier = Modifier.fillMaxSize()) {

        NotificationCard(
            modifier = Modifier
                .align(getAlignmentForGravity(gravity))
                .padding(margin) // Use margin directly
        )

        // Properties panel on the opposite side, less than half screen
        NotificationPropertiesFragment(
            modifier = Modifier
                .then(
                    when (getOppositeOrientation(gravity)) {
                        Orientation.Horizontal -> Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.4f)
                            .align(getOppositeAlignment(gravity))
                        Orientation.Vertical -> Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.4f)
                            .align(getOppositeAlignment(gravity))
                    }
                )
                .padding(8.dp)
        )
    }
}
