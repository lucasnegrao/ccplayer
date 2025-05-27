package com.antiglitch.yetanothernotifier

import android.content.Context
import android.util.Log
import android.view.WindowManager
import com.antiglitch.yetanothernotifier.data.repository.MqttDiscoveryRepository
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.services.MqttService
import com.antiglitch.yetanothernotifier.services.YtDlpService
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
                
                // Step 3: Initialize MQTT service
                initializeMqttService(context)
                
                // Step 4: Update screen dimensions in repository
                updateScreenDimensionsInRepository()
                
                // Step 5: Initialize overlay service
                initializeOverlayService(context)
                
                // Step 6: Start monitoring MQTT enable/disable changes
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
        
        Log.d(TAG, "Repositories initialized")
    }
    
    private fun initializeMqttService(context: Context) {
        val mqttRepo = mqttPropertiesRepository ?: return
        
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
        
        // Register listeners for specific topics if needed
        // Example: mqttService?.addTopicListener("yan/control/+") { topic, message ->
        //     handleControlMessage(topic, message)
        // }
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
                        val context = contextRef.get()
                        if (context != null) {
                            mqttService = MqttService.getInstance(context, mqttRepo)
                            mqttService?.initialize()
                        }
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
        val tolerance = 1.0f
        val widthNeedsUpdate = kotlin.math.abs(currentProperties.screenWidthDp - screenWidthInDp) > tolerance
        val heightNeedsUpdate = kotlin.math.abs(currentProperties.screenHeightDp - screenHeightInDp) > tolerance

        if (widthNeedsUpdate || heightNeedsUpdate) {
            Log.d(TAG, "Updating screen dimensions in repository")
            repository.updateScreenDimensions(screenWidthInDp, screenHeightInDp)
        } else {
            Log.d(TAG, "Screen dimensions unchanged")
        }
    }
    
    private fun initializeOverlayService(context: Context) {
        Log.d(TAG, "Starting overlay service")
        OverlayService.startService(context)
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
            // Stop MQTT service
            mqttService?.disconnect()
            
            // Stop MQTT discovery
            mqttDiscoveryRepository?.stopDiscovery()
            
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
        contextRef.clear()
        notificationPropertiesRepository = null
        mqttDiscoveryRepository = null
        mqttPropertiesRepository = null
    }
}
