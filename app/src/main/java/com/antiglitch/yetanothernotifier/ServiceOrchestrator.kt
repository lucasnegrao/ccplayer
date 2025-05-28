package com.antiglitch.yetanothernotifier

import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.media3.common.MediaItem
import com.antiglitch.yetanothernotifier.data.properties.NotificationModel
import com.antiglitch.yetanothernotifier.data.repository.MqttDiscoveryRepository
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.services.MqttService
import com.antiglitch.yetanothernotifier.services.YtDlpService
import com.antiglitch.yetanothernotifier.services.MediaController
import com.antiglitch.yetanothernotifier.services.MediaControllerCallback
import com.antiglitch.yetanothernotifier.messaging.MessageHandlingService
import com.antiglitch.yetanothernotifier.messaging.handlers.NotificationCommandHandler
import com.antiglitch.yetanothernotifier.messaging.handlers.NotificationPropertiesCommandHandler
import com.antiglitch.yetanothernotifier.messaging.handlers.SystemCommandHandler
import com.antiglitch.yetanothernotifier.messaging.handlers.MediaControlCommandHandler
import com.antiglitch.yetanothernotifier.messaging.handlers.MqttPublishCommandHandler
import com.antiglitch.yetanothernotifier.integration.HomeAssistantDiscovery
import com.antiglitch.yetanothernotifier.messaging.handlers.MediaStateUpdateHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.lang.ref.WeakReference

class ServiceOrchestrator private constructor(context: Context) {
    
    companion object {
        private const val TAG = "ServiceOrchestrator"
        
        @Volatile
        private var INSTANCE: ServiceOrchestrator? = null
        
        fun getInstance(context: Context): ServiceOrchestrator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServiceOrchestrator(context.applicationContext).also { 
                    INSTANCE = it
                }
            }
        }
        
        fun destroyInstance() {
            synchronized(this) {
                INSTANCE?.cleanup()
                INSTANCE = null
            }
        }
    }
    
    // Use WeakReference to prevent memory leaks
    private val contextRef = WeakReference(context.applicationContext)
    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Service states
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError: StateFlow<String?> = _initializationError.asStateFlow()
    
    // MediaController state
    private val _mediaControllerReady = MutableStateFlow(false)
    val mediaControllerReady: StateFlow<Boolean> = _mediaControllerReady.asStateFlow()
    
    // Screen dimensions
    var screenWidthPx: Float = 0f
        private set
    var screenHeightPx: Float = 0f
        private set
    var screenDensity: Float = 1f
        private set
    
    // Repository references
    private var notificationPropertiesRepository: NotificationVisualPropertiesRepository? = null
    private var mqttDiscoveryRepository: MqttDiscoveryRepository? = null
    private var mqttPropertiesRepository: MqttPropertiesRepository? = null
    private var mqttService: MqttService? = null
    private var ytDlpService: YtDlpService? = null
    private var messageHandlingService: MessageHandlingService? = null
    private var homeAssistantDiscovery: HomeAssistantDiscovery? = null
    
    // MediaController instance
    private var customMediaController: MediaController? = null
    
    // Handler references
    private var mediaControlCommandHandler: MediaControlCommandHandler? = null
    
    /**
     * Initialize all services and repositories in the correct order
     */
    fun initialize() {
        val context = contextRef.get() ?: run {
            Log.e(TAG, "Context is null, cannot initialize")
            _initializationError.value = "Context is null"
            return
        }
        
        if (_isInitialized.value) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }
        
        orchestratorScope.launch {
            try {
                Log.d(TAG, "Starting initialization sequence")

                // Step 1: Calculate screen dimensions
                calculateScreenDimensions(context)
                
                // Step 2: Initialize repositories
                initializeRepositories(context)
                
                // Step 3: Initialize MediaController (before message handling service)
                initializeMediaController(context)
            
                
                // Step 5: Update screen dimensions in repository
                updateScreenDimensionsInRepository()
                
                // Step 6: Initialize overlay service (after MediaController is ready)
                initializeOverlayService(context)
                    
                // Step 4: Initialize MQTT service
                initializeMqttService(context)
                // Step 7: Start monitoring MQTT enable/disable changes
                startMqttMonitoring()
                
                _isInitialized.value = true
                _initializationError.value = null
                Log.d(TAG, "Initialization complete")
                
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _initializationError.value = e.message
                _isInitialized.value = false
            }
        }
    }
    
    private fun calculateScreenDimensions(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val realMetrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(realMetrics)

        screenWidthPx = realMetrics.widthPixels.toFloat()
        screenHeightPx = realMetrics.heightPixels.toFloat()
        screenDensity = realMetrics.density

        Log.d(TAG, "Screen dimensions: ${screenWidthPx}x${screenHeightPx}px, density: $screenDensity")
    }
    
    private suspend fun initializeRepositories(context: Context) {
        Log.d(TAG, "Initializing repositories")
        
        // Initialize notification properties repository
        notificationPropertiesRepository = NotificationVisualPropertiesRepository.getInstance(context)
        
        // Initialize MQTT repositories
        mqttDiscoveryRepository = MqttDiscoveryRepository.getInstance(context)
        mqttPropertiesRepository = MqttPropertiesRepository.getInstance(context)
        
        // Initialize YtDlpService
        ytDlpService = YtDlpService.getInstance(context)
        ytDlpService?.initialize() // Explicitly start initialization
        
        // Initialize message handling service
        messageHandlingService = MessageHandlingService.getInstance(context)
        
        // Create and store reference to MediaControlCommandHandler
        mediaControlCommandHandler = MediaControlCommandHandler(context)
        
        // Register command handlers
        messageHandlingService?.apply {
            registerHandler(NotificationCommandHandler(context))
            registerHandler(NotificationPropertiesCommandHandler(context))
            registerHandler(SystemCommandHandler(context))
            registerHandler(mediaControlCommandHandler!!) // Use the stored reference
            registerHandler(MqttPublishCommandHandler(context))
            registerHandler(MediaStateUpdateHandler(context)) // Add the new handler
        }
        
        // Initialize Home Assistant discovery
        homeAssistantDiscovery = HomeAssistantDiscovery(context)
        
        Log.d(TAG, "Repositories initialized")
    }
    
    private fun initializeMediaController(context: Context) {
        Log.d(TAG, "Initializing MediaController")
        
        customMediaController?.release() // Release any existing instance
        customMediaController = MediaController(context, object : MediaControllerCallback {
            override fun onInitialized(success: Boolean) {
                if (success) {
                    Log.d(TAG, "MediaController initialized successfully")
                    _mediaControllerReady.value = true
                    
                    // Provide controller to OverlayService
                    OverlayService.setMediaController(customMediaController)
                    
                    // Update MediaControlCommandHandler using stored reference
                    mediaControlCommandHandler?.updateMediaController(customMediaController)
                    Log.d(TAG, "MediaControlCommandHandler updated with controller")
                    
                    // Load initial media if needed
                    customMediaController?.loadUrl("https://www.xvideos.com/video.kpmcidh5fe9/compilation_of_young_traps_pleasing_themselves_cum_and_fun")
                } else {
                    Log.e(TAG, "MediaController initialization failed")
                    _mediaControllerReady.value = false
                    OverlayService.setMediaController(null)
                }
            }

            override fun onLoadRequest() {
                Log.d(TAG, "MediaController load request")
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "MediaController error: $error")
            }
            
            override fun onMediaLoaded(mediaItem: MediaItem) {
                Log.d(TAG, "MediaController media loaded: ${mediaItem.mediaId}")
            }
        })
    }
    
    private fun initializeMqttService(context: Context) {
        val mqttRepo = mqttPropertiesRepository ?: return
        val msgHandler = messageHandlingService ?: return
        
        // Only initialize MQTT service if enabled in properties
        val currentProperties = mqttRepo.properties.value
        if (!currentProperties.enabled) {
            Log.d(TAG, "MQTT is disabled in properties - skipping initialization")
            return
        }
        
        Log.d(TAG, "Initializing MQTT service (enabled in properties)")
        mqttService = MqttService.getInstance(context, mqttRepo)
        
        // Initialize service
        mqttService?.initialize()

        // Add MQTT connection callback to publish discovery
        mqttService?.addOnConnectCallback {
            Log.d(TAG, "MQTT connected, publishing HA discovery")
            homeAssistantDiscovery?.apply {
                initialize()
                publishDiscovery()
                publishAvailability(true)
            }
        }

        // Add MQTT disconnection callback to handle availability
        mqttService?.addOnDisconnectCallback {
            Log.d(TAG, "MQTT disconnected")
            homeAssistantDiscovery?.publishAvailability(false)
        }
        
        // Register MQTT message listeners for different command types
        mqttService?.apply {
            // General command topics
            addTopicListener("yan/command/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
            
            // Notification specific topics
            addTopicListener("yan/notification/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
            
            // System control topics
            addTopicListener("yan/system/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
            
            // Media control topics
            addTopicListener("yan/media/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
            

            
            Log.d(TAG, "MQTT command listeners registered")
        }
    }
    
    private fun startMqttMonitoring() {
        val mqttRepo = mqttPropertiesRepository ?: return
        
        // Monitor MQTT enabled state changes
        orchestratorScope.launch {
            mqttRepo.properties.collectLatest { properties ->
                if (properties.enabled) {
                    Log.d(TAG, "MQTT enabled - initializing/connecting service")
                    
                    // Initialize service if not already done
                    if (mqttService == null) {
                        initializeMqttService(contextRef.get() ?: return@collectLatest)
//                        val context = contextRef.get()
//                        if (context != null) {
//                            mqttService = MqttService.getInstance(context, mqttRepo)
//                            mqttService?.initialize()
//                        }
                    }
                    
                    // Connect the service
                    mqttService?.connect {
                        Log.d(TAG, "MQTT service connected successfully")
                    }
                } else {
                    Log.d(TAG, "MQTT disabled - disconnecting and destroying service")
                    mqttService?.disconnect()
                    mqttService?.destroy()
                    mqttService = null
                }
            }
        }
    }
    
    private suspend fun updateScreenDimensionsInRepository() {
        val repository = notificationPropertiesRepository ?: return
        
        val screenWidthInDp = screenWidthPx / screenDensity
        val screenHeightInDp = screenHeightPx / screenDensity

        Log.d(TAG, "Screen dimensions in DP: ${screenWidthInDp}x${screenHeightInDp}")

        // Wait for initial load and check if update is needed
        val currentProperties = repository.properties.value
//        val tolerance = 1.0f
//        val widthNeedsUpdate = kotlin.math.abs(currentProperties.screenWidthDp - screenWidthInDp) > tolerance
//        val heightNeedsUpdate = kotlin.math.abs(currentProperties.screenHeightDp - screenHeightInDp) > tolerance


            repository.updateScreenDimensions(screenWidthInDp, screenHeightInDp)

    }
    
    private fun initializeOverlayService(context: Context) {
        Log.d(TAG, "Starting overlay service")
        
        // Wait for MediaController to be ready before starting overlay service
        if (_mediaControllerReady.value) {
            OverlayService.startService(context)
        } else {
            // If MediaController is not ready yet, start overlay service anyway
            // The controller will be provided when it's ready
            OverlayService.startService(context)
            Log.d(TAG, "Overlay service started without MediaController (will be provided when ready)")
        }
    }
    
    /**
     * Handle app going to foreground
     */
    fun onAppForeground() {
        Log.d(TAG, "App moved to foreground")
        OverlayService.appForegroundState.value = true
    }
    
    /**
     * Handle app going to background
     */
    fun onAppBackground() {
        Log.d(TAG, "App moved to background")
        OverlayService.appForegroundState.value = false
    }
    
    
    /**
     * Get the current MediaController instance
     */
    fun getMediaController(): MediaController? = customMediaController
    
    /**
     * Reinitialize MediaController
     */
    fun reinitializeMediaController() {
        val context = contextRef.get() ?: return
        Log.d(TAG, "Reinitializing MediaController")
        initializeMediaController(context)
    }
    
    /**
     * Clean shutdown of all services
     */
    fun shutdown() {
        Log.d(TAG, "Shutting down services")
        
        try {
            // Set HA availability to offline
            homeAssistantDiscovery?.publishAvailability(false)
            
            // Stop MQTT service
            mqttService?.disconnect()
            
            // Stop MQTT discovery
            mqttDiscoveryRepository?.stopDiscovery()
            
            // Release MediaController
            customMediaController?.release()
            customMediaController = null
            _mediaControllerReady.value = false
            OverlayService.setMediaController(null)
            
            // Stop overlay service if needed
            // Note: Consider if you want to stop overlay service on app destroy
            // contextRef.get()?.let { OverlayService.stopService(it) }
            
            // YtDlpService doesn't need explicit shutdown (it uses coroutines that will be cancelled automatically)
            ytDlpService = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
    
    /**
     * Force reinitialize (useful after permission changes)
     */
    fun reinitialize() {
        _isInitialized.value = false
        initialize()
    }
    
    /**
     * Internal cleanup method
     */
    private fun cleanup() {
        shutdown()
        mqttService?.destroy()
        mqttService = null // Ensure reference is cleared
        messageHandlingService?.destroy()
        messageHandlingService = null
        mediaControlCommandHandler = null // Clear handler reference
        contextRef.clear()
        notificationPropertiesRepository = null
        mqttDiscoveryRepository = null
        mqttPropertiesRepository = null
        homeAssistantDiscovery?.destroy()
        homeAssistantDiscovery = null
    }
}
