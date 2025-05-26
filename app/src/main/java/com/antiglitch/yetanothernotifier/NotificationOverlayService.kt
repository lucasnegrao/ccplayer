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
import com.antiglitch.yetanothernotifier.ui.properties.toPx
import com.antiglitch.yetanothernotifier.ui.theme.YetAnotherNotifierTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationOverlayService : LifecycleService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private lateinit var savedStateRegistryOwner: ServiceSavedStateRegistryOwner

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "overlay_notification_channel"

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
        savedStateRegistryOwner.performRestore() // <-- Add this line

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Launch a coroutine that waits for the lifecycle to be CREATED before setting up Compose.
        lifecycleScope.launch {
            lifecycle.whenStateAtLeast(Lifecycle.State.CREATED) {
                // This block executes once the service's lifecycle is actually CREATED.
                // Initial add of the overlay view
                val repository = NotificationVisualPropertiesRepository.getInstance(applicationContext)
                val properties = repository.properties.first()

                val composeView = ComposeView(this@NotificationOverlayService).apply {
                    setViewTreeLifecycleOwner(this@NotificationOverlayService)
                    setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                        override val viewModelStore: ViewModelStore = ViewModelStore()
                    })
                    setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

                    setContent {
                        YetAnotherNotifierTheme {
                            // Force recomposition and maintain theme consistency
                            androidx.compose.runtime.key("notification_overlay") {
                                NotificationCard()
                            }
                        }
                    }
                }
                this@NotificationOverlayService.overlayView = composeView

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = properties.gravity.toAndroidGravity()
                    val marginPx = properties.margin.toPx(applicationContext)
                    x = marginPx
                    y = marginPx
                }

                // Add small delay to ensure theme is fully applied
                kotlinx.coroutines.delay(100)

                this@NotificationOverlayService.overlayView?.let { viewToAdd ->
                    windowManager.addView(viewToAdd, params)
                }

                // --- NEW: Observe gravity/margin changes and update overlay position ---
                serviceScope.launch {
                    repository.properties.collect { updatedProperties ->
                        val updatedParams = params.apply {
                            gravity = updatedProperties.gravity.toAndroidGravity()
                            val marginPx = updatedProperties.margin.toPx(applicationContext)
                            x = marginPx
                            y = marginPx
                        }
                        overlayView?.let { view ->
                            windowManager.updateViewLayout(view, updatedParams)
                        }
                    }
                }
                // --- END NEW ---
            }
        }

        // Call startForeground. This will eventually lead to ON_START.
        startForeground(NOTIFICATION_ID, createNotification())
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
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        serviceScope.cancel()
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
