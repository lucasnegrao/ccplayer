package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.util.Log
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
            val properties = repository.properties.value
            val propsMap = properties.toMap()
            Log.d(TAG, "Retrieved notification properties: $propsMap")
            CommandResult.Success(propsMap)
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
            val currentProps = repository.properties.value
            val updatedProps = updatePropertiesWithPayload(currentProps, payload)
            
            withContext(Dispatchers.IO) {
                repository.updateProperties(updatedProps)
            }
            
            Log.d(TAG, "Updated notification properties: $payload")
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
    
    /**
     * Update properties using explicit property mapping (avoiding reflection issues)
     */
    private fun updatePropertiesWithPayload(
        currentProps: NotificationVisualProperties,
        updates: Map<String, Any?>
    ): NotificationVisualProperties {
        var updatedProps = currentProps
        
        // Handle each property explicitly with proper type conversion
        updates.forEach { (key, value) ->
            try {
                updatedProps = when (key) {
                    "duration" -> updatedProps.copy(
                        duration = convertToLong(value) ?: currentProps.duration
                    )
                    "scale" -> updatedProps.copy(
                        scale = convertToFloat(value) ?: currentProps.scale
                    )
                    "aspect" -> updatedProps.copy(
                        aspect = convertToAspectRatio(value) ?: currentProps.aspect
                    )
                    "cornerRadius" -> updatedProps.copy(
                        cornerRadius = convertToDp(value) ?: currentProps.cornerRadius
                    )
                    "gravity" -> updatedProps.copy(
                        gravity = convertToGravity(value) ?: currentProps.gravity
                    )
                    "roundedCorners" -> updatedProps.copy(
                        roundedCorners = convertToBoolean(value) ?: currentProps.roundedCorners
                    )
                    "margin" -> updatedProps.copy(
                        margin = convertToDp(value) ?: currentProps.margin
                    )
                    "transparency" -> updatedProps.copy(
                        transparency = convertToFloat(value) ?: currentProps.transparency
                    )
                    "screenWidthDp" -> updatedProps.copy(
                        screenWidthDp = convertToFloat(value) ?: currentProps.screenWidthDp
                    )
                    "screenHeightDp" -> updatedProps.copy(
                        screenHeightDp = convertToFloat(value) ?: currentProps.screenHeightDp
                    )
                    else -> {
                        Log.w(TAG, "Unknown property: $key")
                        updatedProps
                    }
                }
                Log.d(TAG, "Updated property: $key = $value")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating property $key with value $value", e)
            }
        }
        
        return updatedProps
    }
    
    /**
     * Type conversion helpers
     */
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
        is String -> value.lowercase() == "true"
        is Number -> value.toInt() != 0
        else -> null
    }
    
    private fun convertToDp(value: Any?) = convertToFloat(value)?.dp
    
    private fun convertToAspectRatio(value: Any?): AspectRatio? = when (value) {
        is String -> AspectRatio.values().find { it.name == value || it.displayName == value }
        else -> null
    }
    
    private fun convertToGravity(value: Any?): Gravity? = when (value) {
        is String -> Gravity.values().find { it.name == value || it.displayName == value }
        else -> null
    }
    
    /**
     * Convert properties object to a map
     */
    private fun NotificationVisualProperties.toMap(): Map<String, Any?> {
        return mapOf(
            "duration" to duration,
            "scale" to scale,
            "aspect" to aspect.name,
            "cornerRadius" to cornerRadius.value,
            "gravity" to gravity.name,
            "roundedCorners" to roundedCorners,
            "margin" to margin.value,
            "transparency" to transparency,
            "screenWidthDp" to screenWidthDp,
            "screenHeightDp" to screenHeightDp,
            "width" to width.value,
            "height" to height.value
        )
    }
    
    /**
     * Start monitoring property changes to broadcast updates
     */
    private fun startPropertyMonitoring() {
        propertyScope.launch {
            try {
                repository.properties.collect { properties ->
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
        val propsMap = properties.toMap()
        
        // Use the message handler utility to broadcast updates
        messageHandler.broadcastUpdate("notification_properties", propsMap)
        
        // For detailed logging
        Log.d(TAG, "Broadcasting notification properties update: ${propsMap.size} properties")
    }
    
    /**
     * Clean up resources when no longer needed
     */
    fun destroy() {
        propertyUpdateJob.cancel()
    }
}
