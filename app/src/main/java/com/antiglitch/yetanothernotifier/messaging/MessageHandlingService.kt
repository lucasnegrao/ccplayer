package com.antiglitch.yetanothernotifier.messaging

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Service that handles commands from multiple sources and routes them to appropriate handlers
 */
class MessageHandlingService private constructor(context: Context) {
    companion object {
        private const val TAG = "MessageHandlingService"
        
        @Volatile
        private var INSTANCE: MessageHandlingService? = null
        
        fun getInstance(context: Context): MessageHandlingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessageHandlingService(context.applicationContext).also { 
                    INSTANCE = it
                }
            }
        }
        
        fun destroyInstance() {
            synchronized(this) {
                INSTANCE?.destroy()
                INSTANCE = null
            }
        }
    }
    
    private val contextRef = WeakReference(context)
    private val handlers = mutableListOf<CommandHandler>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Register a command handler
     */
    fun registerHandler(handler: CommandHandler) {
        synchronized(handlers) {
            handlers.add(handler)
            Log.d(TAG, "Registered handler for action pattern: ${handler.actionPattern}")
        }
    }
    
    /**
     * Unregister a command handler
     */
    fun unregisterHandler(handler: CommandHandler) {
        synchronized(handlers) {
            handlers.remove(handler)
            Log.d(TAG, "Unregistered handler for action pattern: ${handler.actionPattern}")
        }
    }
    
    /**
     * Process a command
     */
    fun processCommand(command: Command) {
        Log.d(TAG, "Processing command: ${command.action} from ${command.source}")
        
        serviceScope.launch {
            val matchingHandlers = findMatchingHandlers(command)
            if (matchingHandlers.isEmpty()) {
                Log.w(TAG, "No handler found for command: ${command.action} from ${command.source}")
                return@launch
            }
            
            matchingHandlers.forEach { handler ->
                try {
                    val result = handler.handle(command)
                    when(result) {
                        is CommandResult.Success -> 
                            Log.d(TAG, "Command ${command.action} handled successfully")
                        is CommandResult.Error -> 
                            Log.e(TAG, "Error handling command ${command.action}: ${result.message}", result.exception)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while handling command ${command.action}", e)
                }
            }
        }
    }
    
    /**
     * Find all handlers that can process this command
     */
    private fun findMatchingHandlers(command: Command): List<CommandHandler> {
        return synchronized(handlers) {
            handlers.filter { it.canHandle(command) }
        }
    }
    
    /**
     * Create a command from MQTT message
     */
    fun createMqttCommand(topic: String, message: String): Command {
        val action = extractActionFromTopic(topic)
        val payload = parseMqttPayload(message)
        
        return object : Command {
            override val source = CommandSource.MQTT
            override val action = action
            override val payload = payload
        }
    }
    
    /**
     * Create a command from Intent
     */
    fun createIntentCommand(intent: Intent): Command {
        val action = intent.action ?: "unknown"
        val payload = intentExtrasToMap(intent)
        
        return object : Command {
            override val source = CommandSource.INTENT
            override val action = action
            override val payload = payload
        }
    }
    
    /**
     * Extract the action from an MQTT topic
     * Example: "yan/command/play_video" -> "play_video"
     */
    private fun extractActionFromTopic(topic: String): String {
        val parts = topic.split("/")
        return if (parts.size >= 3) parts[2] else topic
    }
    
    /**
     * Parse JSON message to map
     */
    private fun parseMqttPayload(message: String): Map<String, Any?> {
        return try {
            val json = JSONObject(message)
            val result = mutableMapOf<String, Any?>()
            
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.get(key)
            }
            
            result
        } catch (e: Exception) {
            mapOf("rawMessage" to message)
        }
    }
    
    /**
     * Convert Intent extras to map
     */
    private fun intentExtrasToMap(intent: Intent): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        intent.extras?.keySet()?.forEach { key ->
            result[key] = intent.extras?.get(key)
        }
        
        return result
    }
    
    /**
     * Receive an intent message and process it
     * @param intent The intent containing the command
     * @return True if the intent was handled, false otherwise
     */
    fun receiveIntent(intent: Intent): Boolean {
        Log.d(TAG, "Received intent with action: ${intent.action}")
        
        if (intent.action.isNullOrEmpty()) {
            Log.w(TAG, "Received intent with null or empty action, ignoring")
            return false
        }
        
        val command = createIntentCommand(intent)
        processCommand(command)
        return true
    }
    
    /**
     * Receive an MQTT message and process it
     * @param topic The MQTT topic
     * @param message The message payload
     */
    fun receiveMqttMessage(topic: String, message: String) {
        Log.d(TAG, "Received MQTT message on topic: $topic")
        val command = createMqttCommand(topic, message)
        processCommand(command)
    }
    
    /**
     * Receive a REST API message and process it
     * @param endpoint The REST endpoint that was called
     * @param payload The JSON payload as a string
     * @param headers Optional map of headers
     */
    fun receiveRestMessage(endpoint: String, payload: String, headers: Map<String, String> = emptyMap()) {
        Log.d(TAG, "Received REST message on endpoint: $endpoint")
        
        // Extract action from endpoint
        // For example: "/api/notifications/show" -> "notification_show"
        val action = endpoint.trim('/').replace("/", "_").lowercase()
        
        // Parse payload similar to MQTT
        val parsedPayload = try {
            val json = JSONObject(payload)
            val result = mutableMapOf<String, Any?>()
            
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.get(key)
            }
            
            // Add headers as metadata
            if (headers.isNotEmpty()) {
                result["_headers"] = headers
            }
            
            result
        } catch (e: Exception) {
            mapOf(
                "rawMessage" to payload,
                "_headers" to headers
            )
        }
        
        // Create and process command
        val command = object : Command {
            override val source = CommandSource.REST
            override val action = action
            override val payload = parsedPayload
        }
        
        processCommand(command)
    }
    
    /**
     * Directly send a command to the system from internal components
     * @param action The command action
     * @param payload The command parameters
     */
    fun sendInternalCommand(action: String, payload: Map<String, Any?>) {
        Log.d(TAG, "Sending internal command: $action")
        
        val command = object : Command {
            override val source = CommandSource.INTERNAL
            override val action = action
            override val payload = payload
        }
        
        processCommand(command)
    }
    
    /**
     * Broadcast state updates to MQTT if enabled
     */
    fun broadcastUpdate(topic: String, payload: Map<String, Any?>) {
        // Get context to access MQTT service
        val context = contextRef.get() ?: return
        
        // Convert payload to JSON
        val jsonPayload = JSONObject(payload).toString()
        
        // Internal command for sending MQTT message
        val mqttPayload = mapOf(
            "topic" to "yan/status/$topic",
            "message" to jsonPayload,
            "retain" to false
        )
        
        // Send internal command to publish MQTT message
        sendInternalCommand("mqtt_publish", mqttPayload)
        
        Log.d(TAG, "Broadcasting update to topic: yan/status/$topic")
    }
    // Add these methods to MessageHandlingService class
suspend fun broadcastMediaUpdates() {
    // Implement broadcasting of current media state
    Log.d("MessageHandlingService", "Broadcasting media updates")
    // Send current media state via MQTT if connected
    // sendInternalCommand("media_state_update", ")
}

suspend fun broadcastNotificationStatus() {
    // Implement broadcasting of current notification status
    Log.d("MessageHandlingService", "Broadcasting notification status")
    // Send current notification states via MQTT if connected
}
    /**
     * Convenience method for broadcasting media-related updates
     * This is a specialized version of broadcastUpdate for media state changes
     */
    fun broadcastMediaUpdate(mediaType: String, payload: Map<String, Any?>) {
        broadcastUpdate("media/$mediaType", payload)
        Log.d(TAG, "Broadcasting media update for type: $mediaType")
    }
    
    /**
     * Get a list of all registered command handlers
     * Used by HomeAssistantDiscovery to determine what to expose
     */
    fun getRegisteredHandlers(): List<CommandHandler> {
        return synchronized(handlers) {
            handlers.toList()
        }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        serviceScope.cancel()
        synchronized(handlers) {
            handlers.clear()
        }
    }
}
