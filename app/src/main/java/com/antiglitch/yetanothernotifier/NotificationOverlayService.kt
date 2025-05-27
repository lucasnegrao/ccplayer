package com.antiglitch.yetanothernotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics // Import DisplayMetrics
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.whenStateAtLeast
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.antiglitch.yetanothernotifier.ui.components.NotificationCard
import com.antiglitch.yetanothernotifier.ui.properties.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.ui.properties.toAndroidGravity
import com.antiglitch.yetanothernotifier.ui.properties.toPx // Ensure this is imported
import com.antiglitch.yetanothernotifier.ui.theme.YetAnotherNotifierTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.antiglitch.yetanothernotifier.service.MediaController as CustomMediaController
import com.antiglitch.yetanothernotifier.service.MediaControllerCallback
import androidx.media3.common.MediaItem

class NotificationOverlayService : LifecycleService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var savedStateRegistryOwner: ServiceSavedStateRegistryOwner

    // Renamed to reflect pixel values
    private var screenWidthPx: Float = 0f
    private var screenHeightPx: Float = 0f
    private var screenDensity: Float = 1f


    private var customMediaControllerInstance: CustomMediaController? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "overlay_notification_channel"
        val appForegroundState = MutableStateFlow(false)
        val playerControllerState = MutableStateFlow<androidx.media3.session.MediaController?>(null) // StateFlow for the player

        fun startService(context: Context) {
            val intent = Intent(context, NotificationOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NotificationOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryOwner = ServiceSavedStateRegistryOwner(this)
        savedStateRegistryOwner.performRestore()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val realMetrics = android.util.DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
        } else {
            windowManager.defaultDisplay.getMetrics(realMetrics)
        }
        
        screenWidthPx = realMetrics.widthPixels.toFloat()
        screenHeightPx = realMetrics.heightPixels.toFloat()
        screenDensity = realMetrics.density // Store density

        createNotificationChannel()
        initializePlayerAndLoadMedia() // Initialize player

        lifecycleScope.launch {
            lifecycle.whenStateAtLeast(Lifecycle.State.CREATED) {
                val repository = NotificationVisualPropertiesRepository.getInstance(applicationContext)
                
                val composeView = ComposeView(this@NotificationOverlayService).apply {
                    setViewTreeLifecycleOwner(this@NotificationOverlayService)
                    setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                        override val viewModelStore: ViewModelStore = ViewModelStore()
                    })
                    setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
                    setContent {
                        YetAnotherNotifierTheme {
                            androidx.compose.runtime.key("notification_overlay") {
                                NotificationCard()
                            }
                        }
                    }
                }
                this@NotificationOverlayService.overlayView = composeView

                // Initialize params with basic settings. The combine block will set the actual values.
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, // Initial width
                    WindowManager.LayoutParams.WRAP_CONTENT, // Initial height
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                )
                // Gravity and x/y will be set in the combine block

                combine(
                    repository.properties,
                    appForegroundState // Observe the StateFlow from companion
                ) { currentProps, appIsCurrentlyInForeground ->
                    // Convert screen pixels to Dp for withScreenDimensions
                    val screenWidthInDp = screenWidthPx / screenDensity
                    val screenHeightInDp = screenHeightPx / screenDensity
                    val propertiesWithScreen = currentProps.withScreenDimensions(screenWidthInDp, screenHeightInDp)
                    
                    propertiesWithScreen to appIsCurrentlyInForeground
                }.collect { (propertiesWithScreen, appIsCurrentlyInForeground) ->
                    val currentOverlayViewNonNull = this@NotificationOverlayService.overlayView ?: return@collect

                    params.apply {
                        // propertiesWithScreen.width and .height are already Dp, convert to Px for WindowManager
                        width = propertiesWithScreen.width.toPx(applicationContext) 
                        height = propertiesWithScreen.height.toPx(applicationContext)
                        val androidGravityValue = propertiesWithScreen.gravity.toAndroidGravity()
                        gravity = androidGravityValue
                        val marginPxValue = propertiesWithScreen.margin.toPx(applicationContext)
                        x = marginPxValue
                        y = if ((androidGravityValue and android.view.Gravity.VERTICAL_GRAVITY_MASK) == android.view.Gravity.CENTER_VERTICAL) {
                            0
                        } else {
                            marginPxValue
                        }
                    }

                    if (!appIsCurrentlyInForeground) {
                        if (currentOverlayViewNonNull.windowToken == null && currentOverlayViewNonNull.parent == null) {
                            try {
                                windowManager.addView(currentOverlayViewNonNull, params)
                            } catch (e: Exception) { e.printStackTrace() }
                        } else if (currentOverlayViewNonNull.windowToken != null) { 
                            try {
                                windowManager.updateViewLayout(currentOverlayViewNonNull, params)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    } else {
                        if (currentOverlayViewNonNull.windowToken != null) {
                            try {
                                windowManager.removeView(currentOverlayViewNonNull)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }
            }
        }
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun initializePlayerAndLoadMedia() {
        customMediaControllerInstance?.release() // Release any existing instance first
        customMediaControllerInstance = CustomMediaController(applicationContext, object : MediaControllerCallback {
            override fun onInitialized(success: Boolean) {
                if (success) {
                    playerControllerState.value = customMediaControllerInstance?.mediaController
                    // Load the URL after initialization
                    customMediaControllerInstance?.loadUrl("https://www.xvideos.com/video.kpmcidh5fe9/compilation_of_young_traps_pleasing_themselves_cum_and_fun")
                } else {
                    playerControllerState.value = null
                }
            }

            override fun onLoadRequest() { /* TODO: Handle if needed */ }
            override fun onError(error: String) { /* TODO: Handle error */ }
            override fun onMediaLoaded(mediaItem: MediaItem) { /* TODO: Handle if needed */ }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the notification overlay visible"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Notification Overlay")
            .setContentText("Displaying notification overlay")
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        customMediaControllerInstance?.release()
        customMediaControllerInstance = null
        playerControllerState.value = null

        overlayView?.let {
            if (it.windowToken != null) { // Check if view is actually attached
                try {
                    windowManager.removeView(it)
                } catch (e: IllegalArgumentException) {
                    // View not attached or other issue, log or ignore
                    e.printStackTrace()
                }
            }
            overlayView = null
        }
        // serviceScope.cancel() // Not needed, lifecycleScope handles cancellation.
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

/**
 * A SavedStateRegistryOwner implementation that properly initializes with the service lifecycle.
 */
private class ServiceSavedStateRegistryOwner(
    private val serviceLifecycleOwner: LifecycleOwner // This is the Service instance
) : SavedStateRegistryOwner {

    private val controller: SavedStateRegistryController = SavedStateRegistryController.create(this)

    fun performRestore() {
        controller.performRestore(null)
    }

    init {
        serviceLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val outBundle = Bundle()
                    controller.performSave(outBundle)
                    // This bundle isn't automatically persisted by the system for a service.
                    // However, Compose's SaveableStateRegistry uses this mechanism to trigger saving.
                }
                else -> {
                    // No-op for other events
                }
            }
        })
    }

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    override val lifecycle: Lifecycle
        get() = serviceLifecycleOwner.lifecycle
}
