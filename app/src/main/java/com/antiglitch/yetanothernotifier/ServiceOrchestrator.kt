package com.antiglitch.yetanothernotifier

import android.content.Context
import android.util.Log
import android.view.WindowManager
import com.antiglitch.yetanothernotifier.data.repository.MqttDiscoveryRepository
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

class ServiceOrchestrator private constructor(context: Context) {
    
    companion object {
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
    
    /**
     * Initialize all services and repositories in the correct order
     */
    fun initialize() {
        val context = contextRef.get() ?: run {
            Log.e("ServiceOrchestrator", "Context is null, cannot initialize")
            _initializationError.value = "Context is null"
            return
        }
        
        if (_isInitialized.value) {
            Log.d("ServiceOrchestrator", "Already initialized, skipping")
            return
        }
        
        orchestratorScope.launch {
            try {
                Log.d("ServiceOrchestrator", "Starting initialization sequence")
                
                // Step 1: Calculate screen dimensions
                calculateScreenDimensions(context)
                
                // Step 2: Initialize repositories
                initializeRepositories(context)
                
                // Step 3: Update screen dimensions in repository
                updateScreenDimensionsInRepository()
                
                // Step 4: Initialize overlay service
                initializeOverlayService(context)
                
                _isInitialized.value = true
                _initializationError.value = null
                Log.d("ServiceOrchestrator", "Initialization complete")
                
            } catch (e: Exception) {
                Log.e("ServiceOrchestrator", "Initialization failed", e)
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

        Log.d("ServiceOrchestrator", "Screen dimensions: ${screenWidthPx}x${screenHeightPx}px, density: $screenDensity")
    }
    
    private suspend fun initializeRepositories(context: Context) {
        Log.d("ServiceOrchestrator", "Initializing repositories")
        
        // Initialize notification properties repository
        notificationPropertiesRepository = NotificationVisualPropertiesRepository.getInstance(context)
        
        // Initialize MQTT discovery repository
        mqttDiscoveryRepository = MqttDiscoveryRepository.getInstance(context)
        
        Log.d("ServiceOrchestrator", "Repositories initialized")
    }
    
    private suspend fun updateScreenDimensionsInRepository() {
        val repository = notificationPropertiesRepository ?: return
        
        val screenWidthInDp = screenWidthPx / screenDensity
        val screenHeightInDp = screenHeightPx / screenDensity

        Log.d("ServiceOrchestrator", "Screen dimensions in DP: ${screenWidthInDp}x${screenHeightInDp}")

        // Wait for initial load and check if update is needed
        val currentProperties = repository.properties.value
        val tolerance = 1.0f
        val widthNeedsUpdate = kotlin.math.abs(currentProperties.screenWidthDp - screenWidthInDp) > tolerance
        val heightNeedsUpdate = kotlin.math.abs(currentProperties.screenHeightDp - screenHeightInDp) > tolerance

        if (widthNeedsUpdate || heightNeedsUpdate) {
            Log.d("ServiceOrchestrator", "Updating screen dimensions in repository")
            repository.updateScreenDimensions(screenWidthInDp, screenHeightInDp)
        } else {
            Log.d("ServiceOrchestrator", "Screen dimensions unchanged")
        }
    }
    
    private fun initializeOverlayService(context: Context) {
        Log.d("ServiceOrchestrator", "Starting overlay service")
        OverlayService.startService(context)
    }
    
    /**
     * Handle app going to foreground
     */
    fun onAppForeground() {
        Log.d("ServiceOrchestrator", "App moved to foreground")
        OverlayService.appForegroundState.value = true
    }
    
    /**
     * Handle app going to background
     */
    fun onAppBackground() {
        Log.d("ServiceOrchestrator", "App moved to background")
        OverlayService.appForegroundState.value = false
    }
    
    /**
     * Clean shutdown of all services
     */
    fun shutdown() {
        Log.d("ServiceOrchestrator", "Shutting down services")
        
        try {
            // Stop MQTT discovery
            mqttDiscoveryRepository?.stopDiscovery()
            
            // Stop overlay service if needed
            // Note: Consider if you want to stop overlay service on app destroy
            // contextRef.get()?.let { OverlayService.stopService(it) }
            
        } catch (e: Exception) {
            Log.e("ServiceOrchestrator", "Error during shutdown", e)
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
        contextRef.clear()
        notificationPropertiesRepository = null
        mqttDiscoveryRepository = null
    }
}
