package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.util.Log
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.CommandResult
import com.antiglitch.yetanothernotifier.services.MqttService
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Handler for internal media state update commands that publishes to MQTT
 */
class MediaStateUpdateHandler(
    private val context: Context
) : CommandHandler {
    
    companion object {
        private const val TAG = "MediaStateUpdateHandler"
    }
    
    override val actionPattern = "media_(state|item)_update"
    
    private val handlerScope = CoroutineScope(Dispatchers.IO)
    
    override fun canHandle(command: Command): Boolean {
        return command.action == "media_state_update" || command.action == "media_item_update"
    }
    
    override suspend fun handle(command: Command): CommandResult {
        return when (command.action) {
            "media_state_update" -> handleMediaStateUpdate(command.payload)
            "media_item_update" -> handleMediaItemUpdate(command.payload)
            else -> CommandResult.Error("Unknown internal media command: ${command.action}")
        }
    }
    
    /**
     * Handle media state update - publishes player state to MQTT
     */
    private suspend fun handleMediaStateUpdate(payload: Map<String, Any?>): CommandResult {
        return try {
            Log.d(TAG, "Publishing media state update to MQTT")
            
            // Publish to MQTT if available and enabled
            publishToMqtt("yan/status/media_state", payload)
            
            CommandResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling media state update", e)
            CommandResult.Error("Failed to publish media state: ${e.message}", e)
        }
    }
    
    /**
     * Handle media item update - publishes current media item info to MQTT
     */
    private suspend fun handleMediaItemUpdate(payload: Map<String, Any?>): CommandResult {
        return try {
            Log.d(TAG, "Publishing media item update to MQTT")
            
            // Publish to MQTT if available and enabled
            publishToMqtt("yan/status/media_item", payload)
            
            CommandResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling media item update", e)
            CommandResult.Error("Failed to publish media item: ${e.message}", e)
        }
    }
    
    /**
     * Publish data to MQTT if service is available and enabled
     */
    private fun publishToMqtt(topic: String, data: Map<String, Any?>) {
        handlerScope.launch {
            try {
                val mqttRepo = MqttPropertiesRepository.getInstance(context)
                val properties = mqttRepo.properties.value
                
                if (!properties.enabled) {
                    Log.d(TAG, "MQTT not enabled, skipping publish to $topic")
                    return@launch
                }
                
                val mqttService = MqttService.getInstance(context, mqttRepo)
                
                // Convert Map to JSON string
                val jsonPayload = mapToJsonString(data)
                mqttService.publish(topic, jsonPayload)
                
                Log.d(TAG, "Published to MQTT topic: $topic")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish to MQTT topic $topic", e)
            }
        }
    }
    
    /**
     * Convert Map to JSON string for MQTT publishing
     */
    private fun mapToJsonString(data: Map<String, Any?>): String {
        return try {
            val jsonObject = JSONObject()
            data.forEach { (key, value) ->
                when (value) {
                    null -> jsonObject.put(key, JSONObject.NULL)
                    is Map<*, *> -> jsonObject.put(key, mapToJsonObject(value as Map<String, Any?>))
                    is List<*> -> jsonObject.put(key, value)
                    else -> jsonObject.put(key, value)
                }
            }
            jsonObject.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting map to JSON", e)
            "{\"error\": \"Failed to serialize data\"}"
        }
    }
    
    /**
     * Convert nested Map to JSONObject
     */
    private fun mapToJsonObject(data: Map<String, Any?>): JSONObject {
        val jsonObject = JSONObject()
        data.forEach { (key, value) ->
            when (value) {
                null -> jsonObject.put(key, JSONObject.NULL)
                is Map<*, *> -> jsonObject.put(key, mapToJsonObject(value as Map<String, Any?>))
                is List<*> -> jsonObject.put(key, value)
                else -> jsonObject.put(key, value)
            }
        }
        return jsonObject
    }
}
