package com.antiglitch.yetanothernotifier.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import com.antiglitch.yetanothernotifier.player.ExoPlayerComposable
import com.antiglitch.yetanothernotifier.ui.properties.*
import com.antiglitch.yetanothernotifier.service.MediaController
import com.antiglitch.yetanothernotifier.service.MediaControllerCallback

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NotificationCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = NotificationVisualPropertiesRepository.getInstance(context)
    val properties by repository.properties.collectAsState()
    
    var mediaController by remember { mutableStateOf<androidx.media3.session.MediaController?>(null) }
    var customMediaController by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(Unit) {
        initializeMediaController(
            context = context,
            onInitialized = { controller, customController ->
                mediaController = controller
                customMediaController = customController
                customMediaController?.loadUrl("https://www.xvideos.com/video.ohkpfhke770/priscila_araujo_fucked_her_friend_gordinho_twice_in_the_same_day")
            }
        )
    }

    Column(
        modifier = Modifier
            .size(
                width = properties.width,
                height = properties.height
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = if (properties.roundedCorners) {
                    RoundedCornerShape(properties.cornerRadius)
                } else {
                    RectangleShape
                }
            )
    ) {
        val shape = if (properties.roundedCorners) {
            RoundedCornerShape(properties.cornerRadius)
        } else {
            RectangleShape
        }
        
        ExoPlayerComposable(
            player = mediaController,
            shape = shape
        )
    }
}

private fun initializeMediaController(
    context: Context,
    onInitialized: (androidx.media3.session.MediaController?, MediaController?) -> Unit
) {
    var mediaControllerInstance: MediaController? = null
    
    mediaControllerInstance = MediaController(context, object : MediaControllerCallback {
        override fun onInitialized(success: Boolean) {
            if (success) {
                onInitialized(mediaControllerInstance?.mediaController, mediaControllerInstance)
            } else {
                onInitialized(null, null)
            }
        }

        override fun onLoadRequest() {

        }

        override fun onError(error: String) {

        }

        override fun onMediaLoaded(mediaItem: MediaItem) {

        }
    })
}