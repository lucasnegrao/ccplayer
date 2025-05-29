package com.antiglitch.yetanothernotifier.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.ExoPlayer
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.antiglitch.yetanothernotifier.player.ExoPlayerComposable
import com.antiglitch.yetanothernotifier.repository.MediaControllerStateRepository
import com.antiglitch.yetanothernotifier.repository.MediaTransportState
import com.antiglitch.yetanothernotifier.repository.MediaContentState
import com.antiglitch.yetanothernotifier.utils.StreamType

enum class HybridViewState {
    NONE,
    LOADING,
    PLAYER_ACTIVE,
    WEBVIEW_ACTIVE,
    ERROR
}

data class StatusMessage(
    val text: String = "",
    val isError: Boolean = false,
    val isVisible: Boolean = false
)

@OptIn(UnstableApi::class)
@Composable
fun HybridPlayerComposable(
    mediaController: MediaController?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    keepScreenOn: Boolean = true,
    videoTranslucency: Float = 1f,
    shape: Shape = RectangleShape
) {
    var viewState by remember { mutableStateOf(HybridViewState.NONE) }
    var statusMessage by remember { mutableStateOf(StatusMessage()) }
    var currentMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pendingWebViewLoad by remember { mutableStateOf<Pair<String, String>?>(null) }
    var webViewKey by remember { mutableStateOf(0) }
    val context = LocalContext.current
    
    // Access state repository
    val stateRepository = remember { MediaControllerStateRepository.getInstance() }
    val transportState by stateRepository.transportState.collectAsState()
    val contentState by stateRepository.contentState.collectAsState()
    val errorState by stateRepository.errorState.collectAsState()

    Log.d("HybridPlayer", "Composable recomposed - mediaController: $mediaController, viewState: $viewState")

    // Stabilize WebView factory with proper dependencies
    val createWebView = remember(context, backgroundColor, videoTranslucency, keepScreenOn) {
        {
            Log.d("HybridPlayer", "Creating WebView instance")
            createWebViewInstance(
                context = context,
                onPageStarted = { 
                    Log.d("HybridPlayer", "WebView page started - current state: $viewState")
                    if (viewState == HybridViewState.LOADING && 
                        (currentMediaItem?.mediaMetadata?.extras?.getString("content_type") != StreamType.VIDEO.name &&
                         currentMediaItem?.mediaMetadata?.extras?.getString("content_type") != StreamType.RTSP.name)) {
                        Log.d("HybridPlayer", "WebView page started - setting state to WEBVIEW_ACTIVE")
                        viewState = HybridViewState.WEBVIEW_ACTIVE
                    } else {
                        Log.d("HybridPlayer", "WebView page started but ignoring state change (current state: $viewState, content type: ${currentMediaItem?.mediaMetadata?.extras?.getString("content_type")})")
                    }
                },
                onPageFinished = { 
                    Log.d("HybridPlayer", "WebView page finished - current state: $viewState")
                    if (viewState == HybridViewState.LOADING && 
                        (currentMediaItem?.mediaMetadata?.extras?.getString("content_type") != StreamType.VIDEO.name &&
                         currentMediaItem?.mediaMetadata?.extras?.getString("content_type") != StreamType.RTSP.name)) {
                        Log.d("HybridPlayer", "Setting state from LOADING to WEBVIEW_ACTIVE")
                        viewState = HybridViewState.WEBVIEW_ACTIVE
                        mediaController?.play()
                    } else {
                        Log.d("HybridPlayer", "WebView page finished but ignoring state change")
                    }
                },
                onError = { error ->
                    Log.e("HybridPlayer", "WebView error occurred: $error")
                    if (viewState == HybridViewState.WEBVIEW_ACTIVE || viewState == HybridViewState.LOADING) {
                        Log.d("HybridPlayer", "Stopping MediaController due to WebView error")
                        mediaController?.stop()
                        statusMessage = StatusMessage("Failed to load webpage: $error", true, true)
                        viewState = HybridViewState.ERROR
                    }
                }
            )
        }
    }

    // Stabilize current media item to prevent unnecessary reloads
    val currentMediaItemStable = rememberUpdatedState(currentMediaItem)

    // Listen to state changes from repository for UI updates only
    LaunchedEffect(contentState.currentMediaItem) {
        val newMediaItem = contentState.currentMediaItem
        if (newMediaItem?.mediaId != currentMediaItem?.mediaId) {
            Log.d("HybridPlayer", "Media item changed via state repository: ${newMediaItem?.mediaId}")
            handleCurrentMediaItemDisplay(
                mediaItem = newMediaItem,
                onStateChange = { newState -> 
                    if (newState == HybridViewState.WEBVIEW_ACTIVE && viewState == HybridViewState.PLAYER_ACTIVE) {
                        webViewKey++
                        webView = null
                    }
                    viewState = newState 
                },
                onMediaItemChange = { item -> currentMediaItem = item },
                onWebViewLoad = { url, contentType -> pendingWebViewLoad = Pair(url, contentType) }
            )
        }
    }
    
    // Handle transport state changes for UI
    LaunchedEffect(transportState.playbackState, transportState.isPlaying) {
        when (transportState.playbackState) {
            Player.STATE_BUFFERING -> {
                if (viewState != HybridViewState.ERROR && viewState != HybridViewState.WEBVIEW_ACTIVE) {
                    viewState = HybridViewState.LOADING
                }
            }
            Player.STATE_READY -> {
                val contentType = currentMediaItem?.mediaMetadata?.extras?.getString("content_type")
                if ((contentType == StreamType.VIDEO.name || contentType == StreamType.RTSP.name) && 
                    transportState.isPlaying) {
                    Log.d("HybridPlayer", "Playback is ready, setting state to PLAYER_ACTIVE")
                    viewState = HybridViewState.PLAYER_ACTIVE
                } else if (contentType != StreamType.VIDEO.name && contentType != StreamType.RTSP.name) {
                    Log.d("HybridPlayer", "Playback is ready but content type is $contentType, maintaining WebView state")
                    if (viewState != HybridViewState.WEBVIEW_ACTIVE) {
                        viewState = HybridViewState.WEBVIEW_ACTIVE
                    }
                }
            }
            Player.STATE_ENDED -> {
                viewState = HybridViewState.NONE
            }
            Player.STATE_IDLE -> {
                if (viewState != HybridViewState.ERROR) {
                    viewState = HybridViewState.NONE
                }
            }
        }
    }
    
    // Handle errors from state repository
    LaunchedEffect(errorState.error) {
        errorState.error?.let { error ->
            Log.e("HybridPlayer", "Error from state repository: ${errorState.errorMessage}")
            statusMessage = StatusMessage("Playback error: ${errorState.errorMessage}", true, true)
            viewState = HybridViewState.ERROR
        }
    }

    // Update WebView states when WebView becomes active
    LaunchedEffect(viewState, webView) {
        if (viewState == HybridViewState.WEBVIEW_ACTIVE && webView != null) {
            Log.d("HybridPlayer", "WebView is active, updating transport state")
            updateWebViewPlaybackState(stateRepository, Player.STATE_READY, isPlaying = true)
        } else if (viewState == HybridViewState.LOADING && currentMediaItem != null) {
            val contentType = currentMediaItem?.mediaMetadata?.extras?.getString("content_type")
            if (contentType != StreamType.VIDEO.name && contentType != StreamType.RTSP.name) {
                Log.d("HybridPlayer", "WebView content loading, updating transport state")
                updateWebViewPlaybackState(stateRepository, Player.STATE_BUFFERING)
            }
        }
    }

    // Cleanup when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { wv ->
                wv.loadUrl("about:blank")
                wv.clearCache(true)
                wv.clearHistory()
                wv.destroy()
            }
        }
    }

    // Consolidate lifecycle management to reduce conflicts
    LaunchedEffect(viewState) {
        when (viewState) {
            HybridViewState.PLAYER_ACTIVE -> {
                Log.d("HybridPlayer", "Managing WebView for PLAYER_ACTIVE state")
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause()
                    wv.pauseTimers()
                }
                pendingWebViewLoad = null
            }
            HybridViewState.ERROR, HybridViewState.NONE -> {
                Log.d("HybridPlayer", "Cleaning up for ERROR/NONE state")
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause()
                    wv.pauseTimers()
                }
                mediaController?.stop()
                pendingWebViewLoad = null
            }
            HybridViewState.WEBVIEW_ACTIVE -> {
                Log.d("HybridPlayer", "WebView is becoming active, ensuring it's resumed.")
                webView?.let { wv ->
                    wv.onResume()
                    wv.resumeTimers()
                }
            }
            else -> {
                // No action needed for LOADING state to prevent conflicts
            }
        }
    }


    // Centralized UI rendering
    HybridPlayerContent(
        viewState = viewState,
        statusMessage = statusMessage,
        currentMediaItem = currentMediaItem,
        mediaController = mediaController,
        webView = webView,
        webViewKey = webViewKey,
        pendingWebViewLoad = pendingWebViewLoad,
        modifier = modifier,
        backgroundColor = backgroundColor,
        keepScreenOn = keepScreenOn,
        videoTranslucency = videoTranslucency,
        shape = shape,
        onWebViewCreated = { newWebView -> 
            webView = newWebView
            // Update state when WebView is created for non-video content
            if (viewState == HybridViewState.LOADING && currentMediaItem != null) {
                val contentType = currentMediaItem?.mediaMetadata?.extras?.getString("content_type")
                if (contentType != StreamType.VIDEO.name && contentType != StreamType.RTSP.name) {
                    updateWebViewPlaybackState(stateRepository, Player.STATE_READY, isPlaying = true)
                }
            }
        },
        onWebViewLoadCleared = { pendingWebViewLoad = null },
        onStateChange = { newState ->
            if (newState == HybridViewState.WEBVIEW_ACTIVE && viewState == HybridViewState.PLAYER_ACTIVE) {
                webViewKey++
                webView = null
            }
            viewState = newState
        },
        createWebView = createWebView
    )
}

// Helper function to manually update WebView playback states
private fun updateWebViewPlaybackState(
    stateRepository: MediaControllerStateRepository,
    playbackState: Int,
    isPlaying: Boolean = false,
    isLoading: Boolean = false,
    position: Long = 0L
) {

    stateRepository.updateTransportState(
        MediaTransportState(
            playbackState = playbackState,
            playbackStateString = MediaTransportState.getPlaybackStateString(playbackState,isPlaying,isLoading),
            playWhenReady = isPlaying,
            isPlaying = isPlaying,
            currentPosition = position,
            duration = -1L, // Unknown duration for WebView content
            bufferedPosition = if (playbackState == Player.STATE_READY) -1L else 0L,
            playbackSpeed = 1.0f,
            repeatMode = Player.REPEAT_MODE_OFF,
            repeatModeString = MediaTransportState.getRepeatModeString(Player.REPEAT_MODE_OFF),
            shuffleModeEnabled = false
        )
    )
}

private fun createWebViewInstance(
    context: android.content.Context,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onError: (String) -> Unit
): WebView {
    return WebView(context).apply {
          settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              loadWithOverviewMode = true
              useWideViewPort = true
              builtInZoomControls = false
              displayZoomControls = false
              setSupportZoom(false)
              cacheMode = WebSettings.LOAD_NO_CACHE
              mediaPlaybackRequiresUserGesture = false

              // Disable features that can cause background issues
              allowContentAccess = false
              allowFileAccess = false
              allowFileAccessFromFileURLs = false
              allowUniversalAccessFromFileURLs = false
              blockNetworkImage = false
              blockNetworkLoads = false

              // Disable text selection and context menus (reduces clipboard access)
              textZoom = 100
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  safeBrowsingEnabled = false
              }
         }

        // Disable all user interactions to minimize potential triggers
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false // Keep this to prevent context menu via long press
        setOnTouchListener { _, _ -> true } // Consume all touch events
        setOnLongClickListener { true } // Keep this to prevent context menu

        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("HybridPlayer", "WebView page started - updating state to buffering")
                onPageStarted()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("HybridPlayer", "WebView page finished - updating state to ready")
                onPageFinished()
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Log.e("HybridPlayer", "WebView error $errorCode: $description for $failingUrl")
                onError("WebView error: $description")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }

        webChromeClient = object : WebChromeClient() {
            // Disable JS alerts, prompts, and confirms that might access clipboard
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                result?.cancel()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?): Boolean {
                result?.cancel()
                return true
            }
        }
    }
}

private fun handleCurrentMediaItemDisplay(
    mediaItem: MediaItem?,
    onStateChange: (HybridViewState) -> Unit,
    onMediaItemChange: (MediaItem?) -> Unit,
    onWebViewLoad: (String, String) -> Unit
) {
    Log.d("HybridPlayer", "Handling media item display: ${mediaItem?.mediaId}")
    
    if (mediaItem == null) {
        Log.d("HybridPlayer", "Media item is null, setting state to NONE")
        onStateChange(HybridViewState.NONE)
        onMediaItemChange(null)
        return
    }

    val extras = mediaItem.mediaMetadata.extras
    val contentType = extras?.getString("content_type")
    
    Log.d("HybridPlayer", "Media item extras: $extras")
    Log.d("HybridPlayer", "Content type: $contentType")

    onMediaItemChange(mediaItem)

    if (contentType != null) {
        Log.d("HybridPlayer", "Setting initial state to LOADING")
        onStateChange(HybridViewState.LOADING)

        if (contentType == StreamType.VIDEO.name || contentType == StreamType.RTSP.name) {
            Log.d("HybridPlayer", "Content type is VIDEO/RTSP, setting up PlayerView")
            onStateChange(HybridViewState.PLAYER_ACTIVE)
        } else {
            Log.d("HybridPlayer", "Content type is $contentType, setting up WebView")
            onWebViewLoad(mediaItem.mediaId, contentType) // Restored
        }
    } else {
        Log.w("HybridPlayer", "Content type is null, defaulting to WebView")
        onWebViewLoad(mediaItem.mediaId, StreamType.WEBPAGE.name) // Restored
    }
}

private fun loadInWebView(webView: WebView, url: String, streamType: String) {
    Log.d("HybridPlayer", "Loading in WebView: $url, streamType: $streamType")
    
    if (streamType == StreamType.MJPEG.name) {
        Log.d("HybridPlayer", "Loading MJPEG stream with HTML wrapper")
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        margin: 0; 
                        padding: 0; 
                        background: black; 
                        display: flex; 
                        justify-content: center; 
                        align-items: center; 
                        height: 100vh; 
                    }
                    img { 
                        max-width: 100%; 
                        max-height: 100%; 
                        object-fit: contain; 
                    }
                </style>
            </head>
            <body>
                <img src="$url" alt="MJPEG Stream">
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        Log.d("HybridPlayer", "MJPEG stream loading started")
    } else {
        Log.d("HybridPlayer", "Loading URL directly: $url")
        webView.loadUrl(url)
    }
}

private fun handleError(
    message: String,
    onError: (String, Boolean) -> Unit
) {
    Log.e("HybridPlayer", "Error occurred: $message")
    Log.d("HybridPlayer", "Calling onError callback with message: $message")
    onError(message, true)
}
