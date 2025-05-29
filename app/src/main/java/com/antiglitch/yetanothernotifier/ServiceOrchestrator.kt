package com.antiglitch.yetanothernotifier

import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.media3.common.MediaItem
import com.antiglitch.yetanothernotifier.data.repository.MqttDiscoveryRepository
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.services.MqttService
import com.antiglitch.yetanothernotifier.services.YtDlpService
import com.antiglitch.yetanothernotifier.services.HybridMediaController
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
    private var customHybridMediaController: HybridMediaController? = null
    
    // Handler references
    private var mediaControlCommandHandler: MediaControlCommandHandler? = null
    
    // Initialization progress tracking
    data class InitializationStep(
        val name: String,
        val description: String,
        var completed: Boolean = false,
        var error: String? = null
    )
    
    private val _initializationProgress = MutableStateFlow(0f)
    val initializationProgress: StateFlow<Float> = _initializationProgress.asStateFlow()
    
    private val _currentStep = MutableStateFlow("")
    val currentStep: StateFlow<String> = _currentStep.asStateFlow()
    
    private val initializationSteps = listOf(
        InitializationStep("screen_calc", "Calculating screen dimensions"),
        InitializationStep("repositories", "Initializing repositories"),
        InitializationStep("media_controller", "Setting up media controller"),
        InitializationStep("screen_update", "Updating screen dimensions"),
        InitializationStep("overlay_service", "Starting overlay service"),
        InitializationStep("mqtt_service", "Connecting MQTT service"),
        InitializationStep("mqtt_monitoring", "Setting up MQTT monitoring"),
        InitializationStep("broadcast_updates", "Broadcasting current states"),
        InitializationStep("complete", "Initialization complete")
    )
    
    // Step completion flags
    private var repositoriesReady = false
    private var mediaControllerInitialized = false
    private var screenDimensionsReady = false
    private var overlayServiceReady = false
    private var mqttServiceReady = false
    
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
        
        // Reset progress tracking
        initializationSteps.forEach { it.completed = false; it.error = null }
        repositoriesReady = false
        mediaControllerInitialized = false
        screenDimensionsReady = false
        overlayServiceReady = false
        mqttServiceReady = false
        
        orchestratorScope.launch {
            try {
                Log.d(TAG, "Starting initialization sequence")
                
                // Step 1: Calculate screen dimensions
                if (!executeStep(0) { calculateScreenDimensions(context) }) return@launch
                
                // Step 2: Initialize repositories (must be first)
                if (!executeStep(1) { initializeRepositories(context) }) return@launch
                
                // Step 3: Initialize MediaController (async - we'll wait for completion)
                if (!executeStep(2) { initializeMediaControllerAsync(context) }) return@launch
                
                // Step 4: Update screen dimensions in repository (depends on repos + screen calc)
                if (!executeStep(3) { updateScreenDimensionsInRepository() }) return@launch
                
                // Step 5: Initialize overlay service (depends on screen + media controller)
                if (!executeStep(4) { initializeOverlayServiceAsync(context) }) return@launch
                
                // Step 6: Initialize MQTT service (depends on repos + message handling)
                if (!executeStep(5) { initializeMqttServiceAsync(context) }) return@launch
                
                // Step 7: Start MQTT monitoring
                if (!executeStep(6) { startMqttMonitoring() }) return@launch
                
                // Step 8: Broadcast all current states
                if (!executeStep(7) { broadcastCurrentStates() }) return@launch
                
                // Step 9: Mark as complete
                executeStep(8) { 
                    _isInitialized.value = true
                    _initializationError.value = null
                    Log.d(TAG, "Initialization complete - all systems ready")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                _initializationError.value = e.message
                _isInitialized.value = false
                updateProgress(-1f) // Error state
            }
        }
    }
    
    private suspend fun executeStep(stepIndex: Int, action: suspend () -> Unit): Boolean {
        return try {
            val step = initializationSteps[stepIndex]
            _currentStep.value = step.description
            Log.d(TAG, "Executing step ${stepIndex + 1}/${initializationSteps.size}: ${step.description}")
            
            action()
            
            step.completed = true
            updateProgress((stepIndex + 1).toFloat() / initializationSteps.size)
            Log.d(TAG, "Step completed: ${step.description}")
            true
        } catch (e: Exception) {
            val step = initializationSteps[stepIndex]
            step.error = e.message
            Log.e(TAG, "Step failed: ${step.description}", e)
            _initializationError.value = "Failed at step: ${step.description} - ${e.message}"
            false
        }
    }
    
    private fun updateProgress(progress: Float) {
        _initializationProgress.value = progress
    }
    
    private fun calculateScreenDimensions(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val realMetrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(realMetrics)

        screenWidthPx = realMetrics.widthPixels.toFloat()
        screenHeightPx = realMetrics.heightPixels.toFloat()
        screenDensity = realMetrics.density

        Log.d(TAG, "Screen dimensions: ${screenWidthPx}x${screenHeightPx}px, density: $screenDensity")
        screenDimensionsReady = true
    }
    
    private suspend fun initializeRepositories(context: Context) {
        Log.d(TAG, "Initializing repositories")
        
        // Initialize notification properties repository
        notificationPropertiesRepository = NotificationVisualPropertiesRepository.getInstance(context)
        
        // Initialize MQTT repositories
        mqttDiscoveryRepository = MqttDiscoveryRepository.getInstance(context)
        mqttPropertiesRepository = MqttPropertiesRepository.getInstance(context)
        
        // Initialize YtDlpService with timeout
        ytDlpService = YtDlpService.getInstance(context)
        ytDlpService?.initialize()
        
        // Wait for YtDlp to be ready (with timeout)
        withTimeout(10000) { // 10 second timeout
            while (ytDlpService?.isInitialized != true) {
                delay(100)
            }
        }
        
        // Initialize message handling service
        messageHandlingService = MessageHandlingService.getInstance(context)
        
        // Create and store reference to MediaControlCommandHandler
        mediaControlCommandHandler = MediaControlCommandHandler(context)
        
        // Register command handlers
        messageHandlingService?.apply {
            registerHandler(NotificationCommandHandler(context))
            registerHandler(NotificationPropertiesCommandHandler(context))
            registerHandler(SystemCommandHandler(context))
            registerHandler(mediaControlCommandHandler!!)
            registerHandler(MqttPublishCommandHandler(context))
            registerHandler(MediaStateUpdateHandler(context))
        }
        
        // Initialize Home Assistant discovery
        homeAssistantDiscovery = HomeAssistantDiscovery(context)
        
        repositoriesReady = true
        Log.d(TAG, "Repositories initialized successfully")
    }
    
    private suspend fun initializeMediaControllerAsync(context: Context) {
        if (!repositoriesReady) {
            throw IllegalStateException("Repositories must be initialized before MediaController")
        }
        
        Log.d(TAG, "Initializing MediaController")
        
        customHybridMediaController?.release()
        
        // Create MediaController with completion callback
        var mediaControllerInitComplete = false
        var mediaControllerError: String? = null
        
        customHybridMediaController = HybridMediaController(context, object : MediaControllerCallback {
            override fun onInitialized(success: Boolean) {
                mediaControllerInitComplete = true
                if (success) {
                    Log.d(TAG, "MediaController initialized successfully")
                    _mediaControllerReady.value = true
                    mediaControllerInitialized = true
                    
                    // Update MediaControlCommandHandler
                    mediaControlCommandHandler?.updateMediaController(customHybridMediaController)
                    Log.d(TAG, "MediaControlCommandHandler updated with controller")
                } else {
                    Log.e(TAG, "MediaController initialization failed")
                    _mediaControllerReady.value = false
                    mediaControllerError = "MediaController initialization failed"
                }
            }

            override fun onLoadRequest() {
                Log.d(TAG, "MediaController load request")
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "MediaController error: $error")
                mediaControllerError = error
            }
            
            override fun onMediaLoaded(mediaItem: MediaItem) {
                Log.d(TAG, "MediaController media loaded: ${mediaItem.mediaId}")
            }
        })
        
        // Wait for MediaController initialization with timeout
        withTimeout(15000) { // 15 second timeout
            while (!mediaControllerInitComplete) {
                delay(100)
            }
        }
        
        if (mediaControllerError != null) {
            throw Exception("MediaController failed: $mediaControllerError")
        }
        
        if (!mediaControllerInitialized) {
            throw Exception("MediaController not ready after initialization")
        }
    }
    
    private suspend fun updateScreenDimensionsInRepository() {
        if (!repositoriesReady || !screenDimensionsReady) {
            throw IllegalStateException("Repositories and screen dimensions must be ready")
        }
        
        val repository = notificationPropertiesRepository ?: 
            throw IllegalStateException("NotificationPropertiesRepository not initialized")
        
        val screenWidthInDp = screenWidthPx / screenDensity
        val screenHeightInDp = screenHeightPx / screenDensity

        Log.d(TAG, "Screen dimensions in DP: ${screenWidthInDp}x${screenHeightInDp}")
        repository.updateScreenDimensions(screenWidthInDp, screenHeightInDp)
    }
    
    private suspend fun initializeOverlayServiceAsync(context: Context) {
        if (!mediaControllerInitialized || !screenDimensionsReady) {
            throw IllegalStateException("MediaController and screen dimensions must be ready before overlay service")
        }
        
        Log.d(TAG, "Starting overlay service")
        
        // Provide controller to OverlayService
        OverlayService.setMediaController(customHybridMediaController)
        
        // Start overlay service
        OverlayService.startService(context)
        
        // Wait a bit for service to start
        delay(1000)
        
        overlayServiceReady = true
        Log.d(TAG, "Overlay service initialized successfully")
    }
    
    private suspend fun initializeMqttServiceAsync(context: Context) {
        if (!repositoriesReady) {
            throw IllegalStateException("Repositories must be initialized before MQTT service")
        }
        
        val mqttRepo = mqttPropertiesRepository ?: 
            throw IllegalStateException("MqttPropertiesRepository not initialized")
        val msgHandler = messageHandlingService ?: 
            throw IllegalStateException("MessageHandlingService not initialized")
        
        // Only initialize MQTT service if enabled in properties
        val currentProperties = mqttRepo.properties.value
        if (!currentProperties.enabled) {
            Log.d(TAG, "MQTT is disabled in properties - skipping initialization")
            mqttServiceReady = true // Mark as ready (disabled)
            return
        }
        
        Log.d(TAG, "Initializing MQTT service (enabled in properties)")
        mqttService = MqttService.getInstance(context, mqttRepo)
        
        // Initialize service
        mqttService?.initialize()
        
        var mqttConnected = false
        var mqttConnectionError: String? = null
        
        // Add connection callbacks
        mqttService?.addOnConnectCallback {
            Log.d(TAG, "MQTT connected, publishing HA discovery")
            mqttConnected = true
            
            homeAssistantDiscovery?.apply {
                initialize()
                publishDiscovery()
                publishAvailability(true)
            }
        }

        mqttService?.addOnDisconnectCallback {
            Log.d(TAG, "MQTT disconnected")
            homeAssistantDiscovery?.publishAvailability(false)
        }
        
        // Register topic listeners
        mqttService?.apply {
            addTopicListener("yan/command/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
            addTopicListener("yan/notification/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
            addTopicListener("yan/system/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
            addTopicListener("yan/media/#") { topic, message ->
                msgHandler.receiveMqttMessage(topic, message)
            }
        }
        
        // Connect and wait for connection (fix callback signature)
        mqttService?.connect {
            // Connection callback - no parameters
            Log.d(TAG, "MQTT connect callback triggered")
        }
        
        // Wait for connection with timeout
        withTimeout(10000) { // 10 second timeout
            while (!mqttConnected && mqttConnectionError == null) {
                delay(100)
            }
        }
        
        if (mqttConnectionError != null) {
            Log.w(TAG, "MQTT connection failed but continuing: $mqttConnectionError")
            // Don't throw - allow initialization to continue without MQTT
        }
        
        mqttServiceReady = true
        Log.d(TAG, "MQTT service initialization completed")
    }
    
    private fun startMqttMonitoring() {
        val mqttRepo = mqttPropertiesRepository ?: return
        
        // Monitor MQTT enabled state changes
        orchestratorScope.launch {
            mqttRepo.properties.collectLatest { properties ->
                if (!_isInitialized.value) return@collectLatest // Don't handle changes during initialization
                
                if (properties.enabled) {
                    Log.d(TAG, "MQTT enabled - initializing/connecting service")
                    
                    if (mqttService == null) {
                        val context = contextRef.get() ?: return@collectLatest
                        initializeMqttServiceAsync(context)
                    } else {
                        mqttService?.connect { 
                            // Connection callback - no parameters
                            Log.d(TAG, "MQTT reconnect callback triggered")
                        }
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
    
    private suspend fun broadcastCurrentStates() {
        val msgHandler = messageHandlingService ?: 
            throw IllegalStateException("MessageHandlingService not initialized")
        
        Log.d(TAG, "Broadcasting current media and notification states")
        
        try {
            // Broadcast all media updates
            msgHandler.broadcastMediaUpdates()
            
            // Broadcast all notification status updates  
            msgHandler.broadcastNotificationStatus()
            
            // Small delay to ensure broadcasts are processed
            delay(500)
            
            Log.d(TAG, "State broadcasts completed")
        } catch (e: Exception) {
            Log.w(TAG, "Error broadcasting states: ${e.message}")
            // Don't fail initialization for broadcast errors
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
            customHybridMediaController?.release()
            customHybridMediaController = null
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
