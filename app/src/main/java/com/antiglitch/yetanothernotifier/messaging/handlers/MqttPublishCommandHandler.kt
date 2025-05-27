package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.util.Log
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.CommandResult
import com.antiglitch.yetanothernotifier.services.MqttService

/**
 * Handler for publishing MQTT messages
 */
class MqttPublishCommandHandler(private val context: Context) : CommandHandler {
    companion object {
        private const val TAG = "MqttPublishCmdHandler"
        private const val MQTT_PUBLISH = "mqtt_publish"
    }
    private val mqttService: MqttService by lazy {
        val repository =  MqttPropertiesRepository.getInstance(context)

        MqttService.getInstance(context, repository)
    }
    override val actionPattern = "mqtt_.*"
    
    override fun canHandle(command: Command): Boolean {
        return command.action == MQTT_PUBLISH
    }
    
    override suspend fun handle(command: Command): CommandResult {
        if (command.action != MQTT_PUBLISH) {
            return CommandResult.Error("Unsupported MQTT action: ${command.action}")
        }
        
        val topic = command.payload["topic"] as? String
            ?: return CommandResult.Error("Missing 'topic' in MQTT publish command")
            
        val message = command.payload["message"] as? String
            ?: return CommandResult.Error("Missing 'message' in MQTT publish command")
            
        val retain = command.payload["retain"] as? Boolean ?: false
        
        return try {

            if (!mqttService.isConnected.value) {
                return CommandResult.Error("MQTT service is not connected")
            }
            
            mqttService.publish(topic, message, retain)
            Log.d(TAG, "Published message to $topic")
            
            CommandResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish MQTT message", e)
            CommandResult.Error("Failed to publish MQTT message: ${e.message}", e)
        }
    }
}
