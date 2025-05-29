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
import androidx.compose.ui.unit.dp
import com.antiglitch.yetanothernotifier.player.ExoPlayerComposable
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
    var webViewKey by remember { mutableStateOf(0) } // Force recreation of AndroidView
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Log.d("HybridPlayer", "Composable recomposed - mediaController: $mediaController, viewState: $viewState")

    // Create WebView factory
    val createWebView = remember {
        {
            Log.d("HybridPlayer", "Creating WebView instance")
            createWebViewInstance(
                context = context,
                onPageStarted = { 
                    Log.d("HybridPlayer", "WebView page started - current state: $viewState")
                    // Only change to WEBVIEW_ACTIVE if we're expecting WebView content
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
                    // Only change state if we're in loading and expecting WebView content
                    if (viewState == HybridViewState.LOADING && 
                        (currentMediaItem?.mediaMetadata?.extras?.getString("content_type") != StreamType.VIDEO.name &&
                         currentMediaItem?.mediaMetadata?.extras?.getString("content_type") != StreamType.RTSP.name)) {
                        Log.d("HybridPlayer", "Setting state from LOADING to WEBVIEW_ACTIVE")
                        viewState = HybridViewState.WEBVIEW_ACTIVE
                    } else {
                        Log.d("HybridPlayer", "WebView page finished but ignoring state change")
                    }
                },
                onError = { error ->
                    Log.e("HybridPlayer", "WebView error occurred: $error")
                    // Only handle error if we're currently using WebView
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

    // Handle media controller changes and listeners
    LaunchedEffect(mediaController) {
        Log.d("HybridPlayer", "LaunchedEffect for mediaController: $mediaController")
        mediaController?.let { controller ->
            Log.d("HybridPlayer", "Adding listener to media controller")
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    Log.d("HybridPlayer", "Media item transition: ${mediaItem?.mediaId}, reason: $reason")
                    handleCurrentMediaItemDisplay(
                        mediaItem = mediaItem,
                        onStateChange = { newState -> 
                            Log.d("HybridPlayer", "State change: $viewState -> $newState")
                            // Force WebView recreation when switching to WEBVIEW_ACTIVE
                            if (newState == HybridViewState.WEBVIEW_ACTIVE && viewState != HybridViewState.WEBVIEW_ACTIVE) {
                                webViewKey++
                                webView = null
                            }
                            viewState = newState 
                        },
                        onMediaItemChange = { item -> 
                            Log.d("HybridPlayer", "Media item changed: ${item?.mediaId}")
                            currentMediaItem = item 
                        },
                        onWebViewLoad = { url, contentType ->
                            Log.d("HybridPlayer", "WebView load requested: $url, type: $contentType")
                            pendingWebViewLoad = Pair(url, contentType)
                        }
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    val errorMessage = "Playback failed: ${error.message}"
                    Log.d("HybridPlayer", "$errorMessage, ${androidx.media3.exoplayer.ExoPlaybackException.getErrorCodeName(error.errorCode)}")

                    if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                        currentMediaItem?.localConfiguration?.uri?.let { uri ->
                            Log.d("HybridPlayer", "Falling back to WebView for unsupported format")
                            webView?.loadUrl(uri.toString())
                            viewState = HybridViewState.LOADING
                        } ?: run {
                            statusMessage = StatusMessage("Unsupported media format", true, true)
                            viewState = HybridViewState.ERROR
                        }
                    } else {
                        statusMessage = StatusMessage("Video playback error", true, true)
                        viewState = HybridViewState.ERROR
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            if (viewState != HybridViewState.ERROR) {
                                viewState = HybridViewState.LOADING
                            }
                        }
                        Player.STATE_READY -> {
                            val contentType = currentMediaItem?.mediaMetadata?.extras?.getString("content_type")
                            if ((contentType == StreamType.VIDEO.name || contentType == StreamType.RTSP.name) && 
                                controller.isPlaying) {
                                Log.d("HybridPlayer", "Playback is ready, setting state to PLAYER_ACTIVE")
                                viewState = HybridViewState.PLAYER_ACTIVE
                            } else if (contentType != StreamType.VIDEO.name && contentType != StreamType.RTSP.name) {
                                Log.d("HybridPlayer", "Playback is ready but content type is $contentType, setting state to WEBVIEW_ACTIVE")
                                viewState = HybridViewState.WEBVIEW_ACTIVE
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
            }
            
            controller.addListener(listener)
            
            // Trigger initial display if there's already a current media item
            val initialMediaItem = controller.currentMediaItem
            if (initialMediaItem != null) {
                Log.d("HybridPlayer", "Controller has initial media item: ${initialMediaItem.mediaId}")
                handleCurrentMediaItemDisplay(
                    mediaItem = initialMediaItem,
                    onStateChange = { newState -> 
                        Log.d("HybridPlayer", "Initial state change: $viewState -> $newState")
                        if (newState == HybridViewState.WEBVIEW_ACTIVE && viewState != HybridViewState.WEBVIEW_ACTIVE) {
                            webViewKey++
                            webView = null
                        }
                        viewState = newState 
                    },
                    onMediaItemChange = { item -> 
                        Log.d("HybridPlayer", "Initial media item: ${item?.mediaId}")
                        currentMediaItem = item 
                    },
                    onWebViewLoad = { url, contentType ->
                        Log.d("HybridPlayer", "Initial WebView load: $url, type: $contentType")
                        pendingWebViewLoad = Pair(url, contentType)
                    }
                )
            } else {
                Log.d("HybridPlayer", "Controller has no initial media item")
            }
        } ?: Log.w("HybridPlayer", "MediaController is null, no listener added")
    }

    // Load pending WebView content when WebView becomes available
    LaunchedEffect(webView, pendingWebViewLoad) {
        if (webView != null && pendingWebViewLoad != null) {
            val (url, contentType) = pendingWebViewLoad!!
            Log.d("HybridPlayer", "Loading pending WebView content: $url, type: $contentType")
            loadInWebView(webView!!, url, contentType)
            pendingWebViewLoad = null
        }
    }

    // Handle status message auto-hide
    LaunchedEffect(statusMessage) {
        Log.d("HybridPlayer", "Status message LaunchedEffect triggered: ${statusMessage}")
        if (statusMessage.isVisible && !statusMessage.isError) {
            Log.d("HybridPlayer", "Status message will auto-hide in 5 seconds")
            delay(5000)
            statusMessage = statusMessage.copy(isVisible = false)
            Log.d("HybridPlayer", "Status message auto-hidden (non-error)")
        } else if (statusMessage.isVisible && statusMessage.isError) {
            Log.d("HybridPlayer", "Error status message will auto-hide in 10 seconds")
            delay(10000)
            statusMessage = statusMessage.copy(isVisible = false)
            Log.d("HybridPlayer", "Status message auto-hidden (error)")
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

    // More aggressive cleanup when switching away from WEBVIEW_ACTIVE
    LaunchedEffect(viewState) {
        when (viewState) {
            HybridViewState.PLAYER_ACTIVE -> {
                Log.d("HybridPlayer", "Aggressively cleaning up WebView for PLAYER_ACTIVE state")
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause() // Pause WebView when not active
                    wv.pauseTimers() // Pause timers
                    wv.clearCache(true)
                    wv.clearHistory()
                }
                pendingWebViewLoad = null
            }
            HybridViewState.ERROR, HybridViewState.NONE -> {
                Log.d("HybridPlayer", "Cleaning up WebView for ERROR/NONE state")
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause() // Pause WebView when not active
                    wv.pauseTimers() // Pause timers
                    wv.clearCache(true)
                }
                mediaController?.stop()
                pendingWebViewLoad = null
            }
            HybridViewState.LOADING -> {
                // Clean up any existing views when entering loading state
                webView?.let { wv ->
                    Log.d("HybridPlayer", "Cleaning up WebView for LOADING state")
                    wv.stopLoading()
                    // If loading is for a new WebView content, it will be resumed in factory.
                    // If loading is for player, WebView should be paused.
                    val contentType = currentMediaItem?.mediaMetadata?.extras?.getString("content_type")
                    if (contentType == StreamType.VIDEO.name || contentType == StreamType.RTSP.name) {
                        wv.onPause()
                        wv.pauseTimers()
                    }
                }
            }
            HybridViewState.WEBVIEW_ACTIVE -> {
                Log.d("HybridPlayer", "WebView is becoming active, ensuring it's resumed.")
                // WebView is created/resumed in the AndroidView factory
                webView?.let { wv ->
                    wv.onResume()
                    wv.resumeTimers()
                }
            }
        }
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when (viewState) {
            HybridViewState.PLAYER_ACTIVE -> {
                Log.d("HybridPlayer", "Rendering PLAYER_ACTIVE state")
                
                // Only render ExoPlayer when actually needed
                mediaController?.let { controller ->
                    Log.d("HybridPlayer", "Rendering ExoPlayerComposable with MediaController: $controller")
                    ExoPlayerComposable(
                        player = controller,
                        shape = shape,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Log.w("HybridPlayer", "MediaController is null in PLAYER_ACTIVE state")
            }
            
            HybridViewState.WEBVIEW_ACTIVE -> {
                Log.d("HybridPlayer", "Rendering WEBVIEW_ACTIVE state")
                
                // Only render WebView if content type is actually for WebView
                val contentType = currentMediaItem?.mediaMetadata?.extras?.getString("content_type")
                if (contentType != StreamType.VIDEO.name && contentType != StreamType.RTSP.name) {
                    // Force recreation when webViewKey changes
                    LaunchedEffect(webViewKey) {
                        Log.d("HybridPlayer", "WebView recreation triggered by key change: $webViewKey")
                        webView = null // Ensure old instance is cleared if key changes
                    }
                    
                    AndroidView(
                        factory = { context ->
                            Log.d("HybridPlayer", "AndroidView factory called for WebView (key: $webViewKey)")
                            createWebView().also { wv ->
                                webView = wv
                                wv.setBackgroundColor(backgroundColor.toArgb())
                                wv.alpha = videoTranslucency
                                wv.keepScreenOn = keepScreenOn
                                Log.d("HybridPlayer", "WebView created and configured, resuming.")
                                wv.onResume() // Resume when factory creates it
                                wv.resumeTimers() // Resume timers

                                // Load pending content immediately if available
                                pendingWebViewLoad?.let { (url, streamType) ->
                                    Log.d("HybridPlayer", "Loading pending content in new WebView: $url")
                                    loadInWebView(wv, url, streamType)
                                    pendingWebViewLoad = null
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                    ) { view ->
                        Log.d("HybridPlayer", "AndroidView update callback called")
                        // Don't reassign webView here since it's already set in factory
                    }
                } else {
                    Log.w("HybridPlayer", "WEBVIEW_ACTIVE state but content type is $contentType, switching to PLAYER_ACTIVE")
                    LaunchedEffect(Unit) {
                        viewState = HybridViewState.PLAYER_ACTIVE
                    }
                }
            }
            
            HybridViewState.LOADING -> {
                Log.d("HybridPlayer", "Rendering LOADING state")
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            HybridViewState.ERROR, HybridViewState.NONE -> {
                Log.d("HybridPlayer", "Rendering ERROR/NONE state: $viewState")
                // Don't render any views, just cleanup
                LaunchedEffect(viewState) {
                    Log.d("HybridPlayer", "Cleaning up resources for ERROR/NONE state")
                    webView?.let { wv ->
                        wv.loadUrl("about:blank")
                        wv.clearCache(true)
                    }
                    pendingWebViewLoad = null
                }
                // Render nothing - empty space
            }
        }

        // Status overlay - only render when visible
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
                onPageStarted()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Removed JavaScript injection
                onPageFinished() // Call the original callback
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                onError("WebView error: $description")
                Log.e("HybridPlayer", "WebView error $errorCode: $description for $failingUrl")
            }

            // Prevent new windows/popups that might trigger clipboard access
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false // Let WebView handle the URL
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
