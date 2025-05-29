package com.antiglitch.yetanothernotifier.ui.components

import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import com.antiglitch.yetanothernotifier.player.ExoPlayerComposable
import com.antiglitch.yetanothernotifier.repository.MediaControllerStateRepository
import com.antiglitch.yetanothernotifier.utils.StreamType

@Composable
fun HybridPlayerContent(
    viewState: HybridViewState,
    statusMessage: StatusMessage,
    currentMediaItem: MediaItem?,
    mediaController: MediaController?,
    webView: WebView?,
    webViewKey: Int,
    pendingWebViewLoad: Pair<String, String>?,
    modifier: Modifier,
    backgroundColor: Color,
    keepScreenOn: Boolean,
    videoTranslucency: Float,
    shape: Shape,
    onWebViewCreated: (WebView?) -> Unit,
    onWebViewLoadCleared: () -> Unit,
    onStateChange: (HybridViewState) -> Unit,
    createWebView: () -> WebView
) {
    // Access state repository for debugging/monitoring
    val stateRepository = remember { MediaControllerStateRepository.getInstance() }
    val transportState by stateRepository.transportState.collectAsState()
    
    Log.d("HybridPlayer", "HybridPlayerContent - viewState: $viewState, transport: ${transportState.playbackStateString}")

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        when (viewState) {
            HybridViewState.PLAYER_ACTIVE -> {
                PlayerContent(
                    mediaController = mediaController,
                    shape = shape
                )
            }
            
            HybridViewState.WEBVIEW_ACTIVE -> {
                WebViewContent(
                    currentMediaItem = currentMediaItem,
                    webView = webView,
                    webViewKey = webViewKey,
                    pendingWebViewLoad = pendingWebViewLoad,
                    backgroundColor = backgroundColor,
                    videoTranslucency = videoTranslucency,
                    keepScreenOn = keepScreenOn,
                    shape = shape,
                    onWebViewCreated = onWebViewCreated,
                    onWebViewLoadCleared = onWebViewLoadCleared,
                    onStateChange = onStateChange,
                    createWebView = createWebView
                )
            }
            
            HybridViewState.LOADING -> {
                LoadingContent()
            }
            
            HybridViewState.ERROR, HybridViewState.NONE -> {
                ErrorContent(
                    viewState = viewState,
                    onWebViewLoadCleared = onWebViewLoadCleared
                )
            }
        }

        // Status overlay
        StatusOverlay(statusMessage = statusMessage)
    }
}

@Composable
private fun PlayerContent(
    mediaController: MediaController?,
    shape: Shape
) {
    Log.d("HybridPlayer", "Rendering PLAYER_ACTIVE state")
    
    mediaController?.let { controller ->
        Log.d("HybridPlayer", "Rendering ExoPlayerComposable with MediaController: $controller")
        ExoPlayerComposable(
            player = controller,
            shape = shape,
            modifier = Modifier.fillMaxSize()
        )
    } ?: Log.w("HybridPlayer", "MediaController is null in PLAYER_ACTIVE state")
}

@Composable
private fun WebViewContent(
    currentMediaItem: MediaItem?,
    webView: WebView?,
    webViewKey: Int,
    pendingWebViewLoad: Pair<String, String>?,
    backgroundColor: Color,
    videoTranslucency: Float,
    keepScreenOn: Boolean,
    shape: Shape,
    onWebViewCreated: (WebView?) -> Unit,
    onWebViewLoadCleared: () -> Unit,
    onStateChange: (HybridViewState) -> Unit,
    createWebView: () -> WebView
) {
    Log.d("HybridPlayer", "Rendering WEBVIEW_ACTIVE state")
    
    val contentType = currentMediaItem?.mediaMetadata?.extras?.getString("content_type")
    if (contentType != StreamType.VIDEO.name && contentType != StreamType.RTSP.name) {
        // Force recreation when webViewKey changes
        LaunchedEffect(webViewKey) {
            Log.d("HybridPlayer", "WebView recreation triggered by key change: $webViewKey")
            onWebViewCreated(null) // Clear old instance
        }
        
        AndroidView(
            factory = { context ->
                Log.d("HybridPlayer", "AndroidView factory called for WebView (key: $webViewKey)")
                createWebView().also { wv ->
                    onWebViewCreated(wv)
                    wv.setBackgroundColor(backgroundColor.toArgb())
                    wv.alpha = videoTranslucency
                    wv.keepScreenOn = keepScreenOn
                    Log.d("HybridPlayer", "WebView created and configured, resuming.")
                    wv.onResume()
                    wv.resumeTimers()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) { view ->
            Log.d("HybridPlayer", "AndroidView update callback called")
        }
    } else {
        Log.w("HybridPlayer", "WEBVIEW_ACTIVE state but content type is $contentType, switching to PLAYER_ACTIVE")
        LaunchedEffect(Unit) {
            onStateChange(HybridViewState.PLAYER_ACTIVE)
        }
    }
}

@Composable
private fun LoadingContent() {
    Log.d("HybridPlayer", "Rendering LOADING state")
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ErrorContent(
    viewState: HybridViewState,
    onWebViewLoadCleared: () -> Unit
) {
    Log.d("HybridPlayer", "Rendering ERROR/NONE state: $viewState")
    // Don't render any views, just cleanup
    LaunchedEffect(viewState) {
        Log.d("HybridPlayer", "Cleaning up resources for ERROR/NONE state")
        onWebViewLoadCleared()
    }
    // Render nothing - empty space
}

@Composable
private fun StatusOverlay(statusMessage: StatusMessage) {
    if (statusMessage.isVisible) {
        Log.d("HybridPlayer", "Rendering status message: ${statusMessage.text}, isError: ${statusMessage.isError}")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (statusMessage.isError) 
                            Color.Red.copy(alpha = 0.9f) 
                        else 
                            Color.Blue.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusMessage.text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
