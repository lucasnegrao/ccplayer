package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antiglitch.yetanothernotifier.data.properties.AspectRatio
import com.antiglitch.yetanothernotifier.data.properties.Gravity
import com.antiglitch.yetanothernotifier.data.properties.NotificationVisualProperties
import com.antiglitch.yetanothernotifier.data.repository.NotificationVisualPropertiesRepository
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.CommandResult
import com.antiglitch.yetanothernotifier.messaging.MessageHandlingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handler for getting and updating notification properties dynamically
 */
class NotificationPropertiesCommandHandler(private val context: Context) : CommandHandler {
    companion object {
        private const val TAG = "NotificationPropsCmdH"
        
        // Action patterns
        private const val GET_PROPERTIES = "get_notification_properties"
        private const val UPDATE_PROPERTIES = "update_notification_properties"
        private const val RESET_PROPERTIES = "reset_notification_properties"
    }
    
    private val repository by lazy {
        NotificationVisualPropertiesRepository.getInstance(context)
    }
    
    private val messageHandler by lazy {
        MessageHandlingService.getInstance(context)
    }
    
    private val propertyUpdateJob = SupervisorJob()
    private val propertyScope = CoroutineScope(propertyUpdateJob + Dispatchers.Default)
    
    init {
        startPropertyMonitoring()
    }
    
    override val actionPattern = ".*notification_properties.*"
    
    override fun canHandle(command: Command): Boolean {
        return command.action == GET_PROPERTIES ||
               command.action == UPDATE_PROPERTIES ||
               command.action == RESET_PROPERTIES
    }
    
    override suspend fun handle(command: Command): CommandResult {
        return when (command.action) {
            GET_PROPERTIES -> handleGetProperties()
            UPDATE_PROPERTIES -> handleUpdateProperties(command.payload)
            RESET_PROPERTIES -> handleResetProperties()
            else -> CommandResult.Error("Unknown action: ${command.action}")
        }
    }
    
    /**
     * Handle getting current notification properties
     */
    private suspend fun handleGetProperties(): CommandResult {
        return try {
            val currentNVP = repository.properties.value
            val modelPropsMap = repository.dynamicPropertiesMap.value // Map<String, Property<*>>

            val resultMap = mutableMapOf<String, Any?>()
            modelPropsMap.forEach { (key, property) ->
                resultMap[key] = when (val value = property.value) {
                    is Dp -> value.value // Store Dp as Float for serialization
                    is AspectRatio -> value.name // Store enums as names
                    is Gravity -> value.name
                    else -> value
                }
            }
            resultMap["screenWidthDp"] = currentNVP.screenWidthDp
            resultMap["screenHeightDp"] = currentNVP.screenHeightDp
            
            Log.d(TAG, "Retrieved notification properties: $resultMap")
            CommandResult.Success(resultMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting notification properties", e)
            CommandResult.Error("Failed to get notification properties: ${e.message}", e)
        }
    }
    
    /**
     * Handle updating notification properties
     */
    private suspend fun handleUpdateProperties(payload: Map<String, Any?>): CommandResult {
        if (payload.isEmpty()) {
            return CommandResult.Error("No properties provided to update")
        }
        
        return try {
            val currentNVP = repository.properties.value 
            val modelPropertyDefinitions = repository.dynamicPropertiesMap.value // Map<String, Property<*>>

            var receivedScreenWidth: Float? = null
            var receivedScreenHeight: Float? = null
            var screenDimensionsPotentiallyChanged = false

            payload.forEach { (key, value) ->
                try {
                    when {
                        key == "screenWidthDp" -> { // Handle screen dimensions separately
                            receivedScreenWidth = convertToFloat(value)
                            if (receivedScreenWidth != null) screenDimensionsPotentiallyChanged = true
                        }
                        key == "screenHeightDp" -> {
                            receivedScreenHeight = convertToFloat(value)
                            if (receivedScreenHeight != null) screenDimensionsPotentiallyChanged = true
                        }
                        modelPropertyDefinitions.containsKey(key) -> {
                            val propertyDefinition = modelPropertyDefinitions[key]!! 
                            val convertedValue: Any? = when (propertyDefinition.defaultValue) {
                                is Long -> convertToLong(value)
                                is Float -> convertToFloat(value)
                                is AspectRatio -> convertToAspectRatio(value)
                                is Dp -> convertToDp(value)
                                is Gravity -> convertToGravity(value)
                                is Boolean -> convertToBoolean(value)
                                else -> {
                                    Log.w(TAG, "Unsupported property type for key $key in model: ${propertyDefinition.defaultValue!!::class.java.simpleName}")
                                    null 
                                }
                            }
                            
                            if (convertedValue != null) {
                                repository.updatePropertyByKey(key, convertedValue as Any)
                                Log.d(TAG, "Updated model property via key: $key = $convertedValue")
                            } else {
                                Log.w(TAG, "Failed to convert value for $key: '$value', or value was null after conversion.")
                            }
                        }
                        else -> {
                            Log.w(TAG, "Unknown property key in payload: $key")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing property update for $key with value '$value'", e)
                }
            }

            if (screenDimensionsPotentiallyChanged) {
                val finalScreenWidth = receivedScreenWidth ?: currentNVP.screenWidthDp
                val finalScreenHeight = receivedScreenHeight ?: currentNVP.screenHeightDp
                if (finalScreenWidth != currentNVP.screenWidthDp || finalScreenHeight != currentNVP.screenHeightDp) {
                    repository.updateScreenDimensions(finalScreenWidth, finalScreenHeight)
                    Log.d(TAG, "Updated screen dimensions: W=$finalScreenWidth, H=$finalScreenHeight")
                }
            }
            
            Log.d(TAG, "Update process completed for properties: $payload")
            CommandResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification properties", e)
            CommandResult.Error("Failed to update notification properties: ${e.message}", e)
        }
    }
    
    /**
     * Handle resetting notification properties to defaults
     */
    private suspend fun handleResetProperties(): CommandResult {
        return try {
            withContext(Dispatchers.IO) {
                repository.resetToDefaults()
            }
            
            Log.d(TAG, "Reset notification properties to defaults")
            CommandResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting notification properties", e)
            CommandResult.Error("Failed to reset notification properties: ${e.message}", e)
        }
    }
    
    private fun convertToLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    
    private fun convertToFloat(value: Any?): Float? = when (value) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }
    
    private fun convertToBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }
    
    private fun convertToDp(value: Any?) = convertToFloat(value)?.dp
    
    private fun convertToAspectRatio(value: Any?): AspectRatio? = when (value) {
        is AspectRatio -> value
        is String -> AspectRatio.values().find { it.name.equals(value, ignoreCase = true) }
        else -> null
    }
    
    private fun convertToGravity(value: Any?): Gravity? = when (value) {
        is Gravity -> value
        is String -> Gravity.values().find { it.name.equals(value, ignoreCase = true) }
        else -> null
    }

    /**
     * Start monitoring property changes to broadcast updates
     */
    private fun startPropertyMonitoring() {
        propertyScope.launch {
            try {
                repository.properties.collect { properties -> // properties is NotificationVisualProperties
                    broadcastPropertyUpdate(properties)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring property changes", e)
            }
        }
    }
    
    /**
     * Broadcast property update to MQTT
     */
    private fun broadcastPropertyUpdate(properties: NotificationVisualProperties) {
        // Construct a map of configurable properties for broadcasting
        val propsMapToSend = mutableMapOf<String, Any?>()
        properties.model.properties.forEach { (key, prop) ->
            propsMapToSend[key] = when (val value = prop.value) {
                is Dp -> value.value // Store Dp as Float
                is AspectRatio -> value.name // Store enums as names
                is Gravity -> value.name
                else -> value
            }
        }
        propsMapToSend["screenWidthDp"] = properties.screenWidthDp
        propsMapToSend["screenHeightDp"] = properties.screenHeightDp
        
        messageHandler.broadcastUpdate("notification_properties", propsMapToSend)
        Log.d(TAG, "Broadcasting notification properties update: ${propsMapToSend.size} properties")
    }
    
    /**
     * Clean up resources when no longer needed
     */
    fun destroy() {
        propertyUpdateJob.cancel()
    }
}
