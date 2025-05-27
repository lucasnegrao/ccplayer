package com.antiglitch.yetanothernotifier.services

import android.content.Context
import android.util.Log
import com.antiglitch.yetanothernotifier.data.properties.MqttProperties
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

typealias MessageCallback = (topic: String, message: String) -> Unit
typealias ConnectionStateCallback = (isConnected: Boolean) -> Unit

class MqttService private constructor(
    private val context: Context,
    private val mqttRepository: MqttPropertiesRepository
) {
    companion object {
        private const val TAG = "MQTTService"
        
        @Volatile
        private var INSTANCE: MqttService? = null
        
        fun getInstance(context: Context, mqttRepository: MqttPropertiesRepository): MqttService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MqttService(context, mqttRepository).also { INSTANCE = it }
            }
        }
    }
    
    private var mqttClient: MqttAsyncClient? = null
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var reconnectTask: ScheduledFuture<*>? = null
    private var isConnecting = false
    private var reconnectAttempts = 0
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    val mqttProperties: StateFlow<MqttProperties> = mqttRepository.mqttProperties
    val connectionState: StateFlow<Boolean> = mqttRepository.connectionState
    val lastMessage: StateFlow<Pair<String, String>?> = mqttRepository.lastMessage
    
    // Topic-specific listeners
    private val topicListeners = ConcurrentHashMap<String, MutableList<MessageCallback>>()
    
    // Pattern-based listeners (for wildcards like +, #)
    private val patternListeners = ConcurrentHashMap<String, MutableList<MessageCallback>>()
    
    // Global message listeners (receive all messages)
    private val globalListeners = mutableListOf<MessageCallback>()
    
    // Connection state listeners
    private val connectionListeners = mutableListOf<ConnectionStateCallback>()
    
    // Legacy single callback for backward compatibility
    private var legacyMessageCallback: MessageCallback? = null
    
    fun initialize(callback: MessageCallback? = null) {
        legacyMessageCallback = callback
        Log.d(TAG, "MqttService initialized")
    }
    
    /**
     * Register a listener for a specific topic
     */
    fun addTopicListener(topic: String, callback: MessageCallback) {
        if (topic.contains('+') || topic.contains('#')) {
            // Handle wildcard topics
            patternListeners.getOrPut(topic) { mutableListOf() }.add(callback)
            Log.d(TAG, "Added pattern listener for topic: $topic")
        } else {
            // Handle exact topic match
            topicListeners.getOrPut(topic) { mutableListOf() }.add(callback)
            Log.d(TAG, "Added exact listener for topic: $topic")
        }
    }
    
    /**
     * Remove a specific listener for a topic
     */
    fun removeTopicListener(topic: String, callback: MessageCallback) {
        if (topic.contains('+') || topic.contains('#')) {
            patternListeners[topic]?.remove(callback)
            if (patternListeners[topic]?.isEmpty() == true) {
                patternListeners.remove(topic)
            }
        } else {
            topicListeners[topic]?.remove(callback)
            if (topicListeners[topic]?.isEmpty() == true) {
                topicListeners.remove(topic)
            }
        }
        Log.d(TAG, "Removed listener for topic: $topic")
    }
    
    /**
     * Remove all listeners for a specific topic
     */
    fun removeAllTopicListeners(topic: String) {
        if (topic.contains('+') || topic.contains('#')) {
            patternListeners.remove(topic)
        } else {
            topicListeners.remove(topic)
        }
        Log.d(TAG, "Removed all listeners for topic: $topic")
    }
    
    /**
     * Add a global listener that receives all messages
     */
    fun addGlobalListener(callback: MessageCallback) {
        globalListeners.add(callback)
        Log.d(TAG, "Added global message listener")
    }
    
    /**
     * Remove a global listener
     */
    fun removeGlobalListener(callback: MessageCallback) {
        globalListeners.remove(callback)
        Log.d(TAG, "Removed global message listener")
    }
    
    /**
     * Add a connection state listener
     */
    fun addConnectionListener(callback: ConnectionStateCallback) {
        connectionListeners.add(callback)
        Log.d(TAG, "Added connection state listener")
    }
    
    /**
     * Remove a connection state listener
     */
    fun removeConnectionListener(callback: ConnectionStateCallback) {
        connectionListeners.remove(callback)
        Log.d(TAG, "Removed connection state listener")
    }
    
    /**
     * Subscribe to additional topics beyond the default subscribe topic
     */
    fun subscribeToTopic(topic: String, qos: Int? = null) {
        if (!connectionState.value) {
            Log.w(TAG, "Cannot subscribe to $topic - not connected to MQTT broker")
            return
        }
        
        val actualQos = qos ?: mqttProperties.value.qos.value
        subscribeTo(topic, actualQos)
    }
    
    /**
     * Unsubscribe from a topic
     */
    fun unsubscribeFromTopic(topic: String) {
        if (!connectionState.value) {
            Log.w(TAG, "Cannot unsubscribe from $topic - not connected to MQTT broker")
            return
        }
        
        try {
            mqttClient?.unsubscribe(topic, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Unsubscribed from topic: $topic")
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to unsubscribe from topic: $topic", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error unsubscribing from topic: $topic", e)
        }
    }
    
    private fun notifyListeners(topic: String, message: String) {
        serviceScope.launch(Dispatchers.Main) {
            try {
                // Notify exact topic listeners
                topicListeners[topic]?.forEach { callback ->
                    try {
                        callback(topic, message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in topic listener for $topic", e)
                    }
                }
                
                // Notify pattern listeners
                patternListeners.forEach { (pattern, callbacks) ->
                    if (topicMatches(topic, pattern)) {
                        callbacks.forEach { callback ->
                            try {
                                callback(topic, message)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in pattern listener for $pattern", e)
                            }
                        }
                    }
                }
                
                // Notify global listeners
                globalListeners.forEach { callback ->
                    try {
                        callback(topic, message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in global listener", e)
                    }
                }
                
                // Legacy callback for backward compatibility
                legacyMessageCallback?.let { callback ->
                    try {
                        callback(topic, message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in legacy callback", e)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listeners", e)
            }
        }
    }
    
    private fun notifyConnectionListeners(isConnected: Boolean) {
        serviceScope.launch(Dispatchers.Main) {
            connectionListeners.forEach { callback ->
                try {
                    callback(isConnected)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in connection listener", e)
                }
            }
        }
    }
    
    /**
     * Check if a topic matches a pattern with MQTT wildcards
     */
    private fun topicMatches(topic: String, pattern: String): Boolean {
        val topicParts = topic.split("/")
        val patternParts = pattern.split("/")
        
        var topicIndex = 0
        var patternIndex = 0
        
        while (topicIndex < topicParts.size && patternIndex < patternParts.size) {
            val patternPart = patternParts[patternIndex]
            
            when (patternPart) {
                "#" -> return true // Multi-level wildcard matches everything after
                "+" -> {
                    // Single-level wildcard, move to next part
                    topicIndex++
                    patternIndex++
                }
                else -> {
                    if (topicParts[topicIndex] != patternPart) {
                        return false
                    }
                    topicIndex++
                    patternIndex++
                }
            }
        }
        
        // Check if we've consumed all parts of both topic and pattern
        return topicIndex == topicParts.size && patternIndex == patternParts.size
    }
    
    fun connect(onSuccessCallback: (() -> Unit)? = null) {
        val properties = mqttProperties.value
        if (!properties.enabled) {
            Log.d(TAG, "MQTT is disabled in settings")
            return
        }
        
        if (isConnecting || connectionState.value) {
            Log.d(TAG, "Already connecting or connected")
            if (connectionState.value && onSuccessCallback != null) {
                onSuccessCallback()
            }
            return
        }
        
        serviceScope.launch {
            isConnecting = true
            Log.d(TAG, "Attempting to connect to MQTT broker: ${properties.serverUri}")
            
            try {
                createMqttClient(properties)
                val connOpts = createConnectionOptions(properties)
                
                mqttClient?.connect(connOpts, null, createConnectionListener(onSuccessCallback))
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during connect initialization", e)
                handleConnectionError(e)
            }
        }
    }
    
    private fun createMqttClient(properties: MqttProperties) {
        val persistence = MemoryPersistence()
        Log.d(TAG, "Creating new client with URI: ${properties.serverUri}, ClientID: ${properties.clientId}")
        mqttClient = MqttAsyncClient(properties.serverUri, properties.clientId, persistence)
        mqttClient?.setCallback(createMqttCallback())
    }
    
    private fun createConnectionOptions(properties: MqttProperties): MqttConnectOptions {
        Log.d(TAG, "Creating connection options")
        return MqttConnectOptions().apply {
            isCleanSession = properties.cleanSession
            connectionTimeout = properties.connectionTimeout
            keepAliveInterval = properties.keepAlive
            
            setupCredentials(this, properties)
            setupLastWill(this, properties)
        }
    }
    
    private fun setupCredentials(options: MqttConnectOptions, properties: MqttProperties) {
        if (properties.username.isNotEmpty()) {
            Log.d(TAG, "Setting username: ${properties.username}")
            options.userName = properties.username
            
            if (properties.password.isNotEmpty()) {
                Log.d(TAG, "Setting password (masked)")
                // Password is already decrypted by the repository
                options.setPassword(properties.password.toCharArray())
            }
        }
    }

    private fun setupLastWill(options: MqttConnectOptions, properties: MqttProperties) {
        if (properties.availabilityTopic.isNotBlank()) {
            options.setWill(properties.availabilityTopic, properties.offlinePayload.toByteArray(), properties.qos.value, true)
            Log.d(TAG, "LWT configured: Topic=${properties.availabilityTopic}, Payload=${properties.offlinePayload}")
        }
    }
    
    private fun createMqttCallback(): MqttCallback {
        return object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.e(TAG, "MQTT Connection lost", cause)
                mqttRepository.updateConnectionState(false)
                isConnecting = false
                
                // Notify connection listeners
                notifyConnectionListeners(false)
                
                val properties = mqttProperties.value
                if (properties.autoReconnect) {
                    scheduleReconnectWithBackoff()
                }
            }
            
            override fun messageArrived(topic: String, message: MqttMessage) {
                val payload = String(message.payload)
                Log.d(TAG, "Message arrived: $topic - $payload")
                
                mqttRepository.updateLastMessage(topic, payload)
                
                // Notify all registered listeners
                notifyListeners(topic, payload)
            }
            
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Message delivery complete
            }
        }
    }
    
    private fun createConnectionListener(onSuccessCallback: (() -> Unit)? = null): IMqttActionListener {
        return object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                val properties = mqttProperties.value
                Log.d(TAG, "Connected to MQTT broker: ${properties.serverUri}")
                mqttRepository.updateConnectionState(true)
                isConnecting = false
                
                handleSuccessfulConnection()
                onSuccessCallback?.invoke()
            }
            
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val properties = mqttProperties.value
                Log.e(TAG, "Failed to connect to MQTT broker: ${properties.serverUri}", exception)
                handleConnectionError(exception)
            }
        }
    }
    
    private fun handleSuccessfulConnection() {
        reconnectAttempts = 0
        val properties = mqttProperties.value
        
        subscribeTo(properties.subscribeTopic)
        publishAvailabilityStatus(true)
        
        // Notify connection listeners
        notifyConnectionListeners(true)
    }
    
    private fun publishAvailabilityStatus(isOnline: Boolean) {
        if (!connectionState.value && isOnline) {
            Log.w(TAG, "Cannot publish availability status - not connected to MQTT broker")
            return
        }
        
        val properties = mqttProperties.value
        if (properties.availabilityTopic.isNotBlank()) {
            val payload = if (isOnline) properties.onlinePayload else properties.offlinePayload
            publishInternal(properties.availabilityTopic, payload, true)
            Log.d(TAG, "Published to availability topic: Topic=${properties.availabilityTopic}, Payload=$payload")
        }
    }
    
    private fun handleConnectionError(exception: Throwable?) {
        Log.e(TAG, "Failed to connect to MQTT broker", exception)
        mqttRepository.updateConnectionState(false)
        isConnecting = false
        reconnectAttempts++
        
        val properties = mqttProperties.value
        if (properties.autoReconnect) {
            scheduleReconnectWithBackoff()
        }
    }
    
    private fun scheduleReconnectWithBackoff() {
        reconnectTask?.cancel(false)
        
        val properties = mqttProperties.value
        val delay = calculateReconnectDelay(properties)
        
        Log.d(TAG, "Scheduling reconnect attempt #$reconnectAttempts in ${delay}s")
        
        val reconnectRunnable = Runnable {
            Log.d(TAG, "Attempting to reconnect to MQTT broker (attempt #$reconnectAttempts)...")
            connect()
        }
        
        reconnectTask = executorService.schedule(reconnectRunnable, delay, TimeUnit.SECONDS)
    }
    
    private fun calculateReconnectDelay(properties: MqttProperties): Long {
        val delay = (properties.baseReconnectInterval * Math.pow(properties.reconnectMultiplier, (reconnectAttempts - 1).toDouble())).toLong()
        return minOf(delay, properties.maxReconnectInterval)
    }
    
    fun disconnect() {
        serviceScope.launch {
            try {
                reconnectTask?.cancel(false)
                reconnectAttempts = 0
                
                publishAvailabilityStatus(false)

                mqttClient?.let {
                    if (it.isConnected) {
                        it.disconnect(null, object : IMqttActionListener {
                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                Log.d(TAG, "Disconnected from MQTT broker")
                                mqttRepository.updateConnectionState(false)
                            }
                            
                            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                Log.e(TAG, "Failed to disconnect from MQTT broker", exception)
                            }
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from MQTT broker", e)
            } finally {
                mqttRepository.updateConnectionState(false)
                isConnecting = false
            }
        }
    }
    
    private fun subscribeTo(topic: String, qos: Int? = null) {
        try {
            val actualQos = qos ?: mqttProperties.value.qos.value
            mqttClient?.subscribe(topic, actualQos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to topic: $topic")
                }
                
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to subscribe to topic: $topic", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topic: $topic", e)
        }
    }
    
    /**
     * Clear all listeners
     */
    fun clearAllListeners() {
        topicListeners.clear()
        patternListeners.clear()
        globalListeners.clear()
        connectionListeners.clear()
        legacyMessageCallback = null
        Log.d(TAG, "Cleared all listeners")
    }
    
    fun publish(message: String) {
        val properties = mqttProperties.value
        publish(properties.publishTopic, message, false)
    }

    fun publish(topic: String, message: String, retained: Boolean = false) {
        if (!connectionState.value) {
            Log.w(TAG, "Cannot publish message to $topic - not connected to MQTT broker")
            return
        }
        publishInternal(topic, message, retained)
    }
    
    private fun publishInternal(topic: String, message: String, retained: Boolean) {
        try {
            val properties = mqttProperties.value
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttMessage.qos = properties.qos.value
            mqttMessage.isRetained = retained
            
            mqttClient?.publish(topic, mqttMessage, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Message published to topic: $topic (retained: $retained)")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to publish message to topic: $topic (retained: $retained)", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing message", e)
        }
    }
    
    /**
     * Test connection with current or provided properties without affecting the main connection
     */
    fun testConnection(
        testProperties: MqttProperties? = null,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        serviceScope.launch {
            val properties = testProperties ?: mqttProperties.value
            var testClient: MqttAsyncClient? = null
            
            try {
                Log.d(TAG, "Testing connection to MQTT broker: ${properties.serverUri}")
                
                // Create a temporary client for testing
                val persistence = MemoryPersistence()
                val testClientId = "${properties.clientId}_test_${System.currentTimeMillis()}"
                testClient = MqttAsyncClient(properties.serverUri, testClientId, persistence)
                
                val connOpts = createConnectionOptions(properties)
                
                // Set a shorter timeout for testing
                connOpts.connectionTimeout = 10
                
                testClient.connect(connOpts, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "Test connection successful")
                        
                        // Immediately disconnect the test client
                        try {
                            testClient?.disconnect(null, object : IMqttActionListener {
                                override fun onSuccess(asyncActionToken: IMqttToken?) {
                                    Log.d(TAG, "Test client disconnected successfully")
                                }
                                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                    Log.w(TAG, "Failed to disconnect test client", exception)
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Error disconnecting test client", e)
                        }
                        
                        onResult(true, "Connection test successful!")
                    }
                    
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Test connection failed", exception)
                        val errorMessage = when {
                            exception?.message?.contains("Connection refused", ignoreCase = true) == true -> 
                                "Connection refused - check server address and port"
                            exception?.message?.contains("timeout", ignoreCase = true) == true -> 
                                "Connection timeout - server may be unreachable"
                            exception?.message?.contains("authentication", ignoreCase = true) == true -> 
                                "Authentication failed - check username and password"
                            exception?.message?.contains("not authorized", ignoreCase = true) == true -> 
                                "Not authorized - check credentials"
                            else -> "Connection failed: ${exception?.message ?: "Unknown error"}"
                        }
                        onResult(false, errorMessage)
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during test connection", e)
                val errorMessage = when {
                    e.message?.contains("MalformedURLException", ignoreCase = true) == true -> 
                        "Invalid server URL format"
                    e.message?.contains("UnknownHostException", ignoreCase = true) == true -> 
                        "Unknown host - check server address"
                    else -> "Test failed: ${e.message ?: "Unknown error"}"
                }
                onResult(false, errorMessage)
            }
        }
    }
    
    fun destroy() {
        disconnect()
        clearAllListeners()
        serviceScope.cancel()
        executorService.shutdown()
    }
}
