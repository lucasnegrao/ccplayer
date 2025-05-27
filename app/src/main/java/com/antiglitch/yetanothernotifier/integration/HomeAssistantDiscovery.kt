package com.antiglitch.yetanothernotifier.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.MessageHandlingService
import com.antiglitch.yetanothernotifier.services.MqttService
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Handles the automatic discovery of app features in Home Assistant via MQTT
 */
class HomeAssistantDiscovery(
    private val context: Context,
    private val deviceId: String = UUID.randomUUID().toString(),
    private val commandPrefix: String = "yan/command",
    private val statusPrefix: String = "yan/status",
    private val availabilityTopic: String = "yan/availability"
) {

    companion object {
        private const val TAG = "HomeAssistantDiscovery"
        private const val HA_DISCOVERY_PREFIX = "homeassistant"
        private const val HA_DEVICE_AUTOMATION_PREFIX = "device_automation"
        private const val HA_DEVICE_MANUFACTURER = "Antiglitch"
        private const val HA_DEVICE_MODEL = "YetAnotherNotifier"
        private const val HA_DEVICE_NAME_PREFIX = "YAN "
        private const val HA_OBJECT_ID_PREFIX = "yan_"
        private const val HA_SW_VERSION = "1.0.0"
        private const val HA_PAYLOAD_AVAILABLE = "online"
        private const val HA_PAYLOAD_NOT_AVAILABLE = "offline"
        private const val DISCOVERY_PUBLISH_DELAY_MS = 100L
        
        // Event type constants
        const val EVENT_NOTIFICATION_SHOWN = "NOTIFICATION_SHOWN"
        const val EVENT_NOTIFICATION_DISMISSED = "NOTIFICATION_DISMISSED"
        const val EVENT_MEDIA_PLAYING = "MEDIA_PLAYING"
        const val EVENT_MEDIA_PAUSED = "MEDIA_PAUSED"
        const val EVENT_MEDIA_STOPPED = "MEDIA_STOPPED"
    }
    
    private val mqttService: MqttService by lazy {
        val repository =  MqttPropertiesRepository.getInstance(context)

        MqttService.getInstance(context, repository)
    }
    
    private val messageHandler: MessageHandlingService by lazy {
        MessageHandlingService.getInstance(context)
    }
    
    private val baseUniqueId = "${HA_OBJECT_ID_PREFIX}${deviceId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
    private val deviceJson = createDeviceConfigJson()
    private val registeredHandlers = mutableMapOf<String, CommandHandler>()
    
    // Metadata for the components we want to expose
    private val componentDefinitions = mutableListOf<ComponentDefinition>()
    
    /**
     * Initialize the discovery service
     */
    fun initialize() {
        Log.d(TAG, "Initializing Home Assistant discovery with deviceId: $deviceId")
        registerCommandHandlers()
        defineComponents()
    }
    
    /**
     * Register all command handlers that we want to expose to Home Assistant
     */
    private fun registerCommandHandlers() {
        // Get all registered handlers from MessageHandlingService
        val handlers = messageHandler.getRegisteredHandlers()
        
        handlers.forEach { handler ->
            val actionPattern = handler.actionPattern
            
            // Filter handlers that we want to expose
            if (actionPattern.contains("media_") || 
                actionPattern.contains("notification_") || 
                actionPattern.contains("system_")) {
                
                registeredHandlers[actionPattern] = handler
                Log.d(TAG, "Registered handler for HA discovery: $actionPattern")
            }
        }
    }
    
    /**
     * Define components to expose to Home Assistant
     */
    private fun defineComponents() {
        // --- Media Control Components ---
        
        // Media state and controls
        componentDefinitions.add(
            SensorDefinition(
                name = "Media Title", 
                uniqueIdSuffix = "media_title",
                stateTopic = "$statusPrefix/media_item",
                valueTemplate = "{{ value_json.metadata.title | default('No Title') }}",
                icon = "mdi:music-note",
                category = "diagnostic"
            )
        )
        
        componentDefinitions.add(
            SensorDefinition(
                name = "Media Artist", 
                uniqueIdSuffix = "media_artist",
                stateTopic = "$statusPrefix/media_item",
                valueTemplate = "{{ value_json.metadata.artist | default('Unknown Artist') }}",
                icon = "mdi:account-music",
                category = "diagnostic"
            )
        )
        
        componentDefinitions.add(
            SensorDefinition(
                name = "Media URI", 
                uniqueIdSuffix = "media_uri",
                stateTopic = "$statusPrefix/media_item",
                valueTemplate = "{{ value_json.uri | default('No Media') }}",
                icon = "mdi:link",
                category = "diagnostic"
            )
        )
        
        componentDefinitions.add(
            SensorDefinition(
                name = "Playback State", 
                uniqueIdSuffix = "playback_state",
                stateTopic = "$statusPrefix/media_state",
                valueTemplate = "{{ value_json.state }}",
                icon = "mdi:play-circle-outline",
                category = "diagnostic"
            )
        )
        
        componentDefinitions.add(
            SensorDefinition(
                name = "Media Position", 
                uniqueIdSuffix = "media_position",
                stateTopic = "$statusPrefix/media_state",
                valueTemplate = "{{ value_json.position | default(0) }}",
                icon = "mdi:clock-time-four-outline",
                category = "diagnostic",
                unitOfMeasurement = "ms"
            )
        )
        
        componentDefinitions.add(
            SensorDefinition(
                name = "Media Duration", 
                uniqueIdSuffix = "media_duration",
                stateTopic = "$statusPrefix/media_state",
                valueTemplate = "{{ value_json.duration | default(0) }}",
                icon = "mdi:timer-outline",
                category = "diagnostic",
                unitOfMeasurement = "ms"
            )
        )
        
        // Media control buttons
        componentDefinitions.add(
            ButtonDefinition(
                name = "Play",
                uniqueIdSuffix = "media_play",
                commandTopic = "$commandPrefix/media_play",
                payload = "{}",
                icon = "mdi:play"
            )
        )
        
        componentDefinitions.add(
            ButtonDefinition(
                name = "Pause",
                uniqueIdSuffix = "media_pause",
                commandTopic = "$commandPrefix/media_pause",
                payload = "{}",
                icon = "mdi:pause"
            )
        )
        
        componentDefinitions.add(
            ButtonDefinition(
                name = "Stop",
                uniqueIdSuffix = "media_stop",
                commandTopic = "$commandPrefix/media_stop",
                payload = "{}",
                icon = "mdi:stop"
            )
        )
        
        componentDefinitions.add(
            ButtonDefinition(
                name = "Next Track",
                uniqueIdSuffix = "media_next",
                commandTopic = "$commandPrefix/media_next",
                payload = "{}",
                icon = "mdi:skip-next"
            )
        )
        
        componentDefinitions.add(
            ButtonDefinition(
                name = "Previous Track",
                uniqueIdSuffix = "media_previous",
                commandTopic = "$commandPrefix/media_previous",
                payload = "{}",
                icon = "mdi:skip-previous"
            )
        )
        
        // Media volume control
        componentDefinitions.add(
            NumberDefinition(
                name = "Volume",
                uniqueIdSuffix = "media_volume",
                stateTopic = "$statusPrefix/media_state",
                commandTopic = "$commandPrefix/media_adjust_volume",
                valueTemplate = "{{ value_json.volume | default(0) }}",
                commandTemplate = "{\"volume\": {{ value }}}",
                min = 0,
                max = 1,
                step = 0.05,
                mode = "slider",
                icon = "mdi:volume-high"
            )
        )
        
        // Load URL text input
        componentDefinitions.add(
            TextDefinition(
                name = "Load Media URL",
                uniqueIdSuffix = "load_url",
                commandTopic = "$commandPrefix/media_load_url",
                commandTemplate = "{\"url\": \"{{ value }}\"}",
                icon = "mdi:web",
                mode = "text"
            )
        )
        
        // --- Notification Components ---
        
        // Notification properties
        componentDefinitions.add(
            SwitchDefinition(
                name = "Notification Enabled",
                uniqueIdSuffix = "notification_enabled",
                stateTopic = "$statusPrefix/notification_properties",
                commandTopic = "$commandPrefix/update_notification_properties",
                valueTemplate = "{{ value_json.enabled }}",
                payloadOn = "{\"enabled\": true}",
                payloadOff = "{\"enabled\": false}",
                icon = "mdi:bell"
            )
        )
        
        componentDefinitions.add(
            NumberDefinition(
                name = "Notification Duration",
                uniqueIdSuffix = "notification_duration",
                stateTopic = "$statusPrefix/notification_properties",
                commandTopic = "$commandPrefix/update_notification_properties",
                valueTemplate = "{{ value_json.defaultDurationMs | default(0) }}",
                commandTemplate = "{\"defaultDurationMs\": {{ value | int }}}",
                min = 1000,
                max = 60000,
                step = 1000,
                mode = "box",
                icon = "mdi:timer-outline",
                unitOfMeasurement = "ms"
            )
        )
        
        // Show notification command
        componentDefinitions.add(
            TextDefinition(
                name = "Show Notification",
                uniqueIdSuffix = "show_notification",
                commandTopic = "$commandPrefix/show_notification",
                commandTemplate = "{\"message\": \"{{ value }}\"}",
                icon = "mdi:bell-ring-outline"
            )
        )
        
        // Device triggers for notification events
        componentDefinitions.add(
            TriggerDefinition(
                triggerType = "notification",
                triggerSubtype = "shown",
                topic = "$statusPrefix/events",
                payload = EVENT_NOTIFICATION_SHOWN
            )
        )
        
        componentDefinitions.add(
            TriggerDefinition(
                triggerType = "notification",
                triggerSubtype = "dismissed",
                topic = "$statusPrefix/events",
                payload = EVENT_NOTIFICATION_DISMISSED
            )
        )
        
        // --- System Controls ---
        
        // System info
        componentDefinitions.add(
            ButtonDefinition(
                name = "Get System Info",
                uniqueIdSuffix = "get_system_info",
                commandTopic = "$commandPrefix/get_system_info",
                payload = "{}",
                icon = "mdi:information-outline",
                category = "diagnostic"
            )
        )
        
        // Clear cache
        componentDefinitions.add(
            ButtonDefinition(
                name = "Clear Cache",
                uniqueIdSuffix = "clear_cache",
                commandTopic = "$commandPrefix/clear_cache",
                payload = "{}",
                icon = "mdi:broom",
                category = "config"
            )
        )
    }
    
    /**
     * Publish all component definitions to Home Assistant
     */
    fun publishDiscovery() {
        if (!mqttService.isConnected.value) {
            Log.w(TAG, "MQTT service not connected. Skipping HA discovery.")
            return
        }
        
        val discoveryMessages = mutableListOf<Pair<String, String>>()
        
        // Convert all component definitions to MQTT messages
        componentDefinitions.forEach { definition ->
            when (definition) {
                is SensorDefinition -> queueSensor(discoveryMessages, definition)
                is ButtonDefinition -> queueButton(discoveryMessages, definition)
                is SwitchDefinition -> queueSwitch(discoveryMessages, definition)
                is NumberDefinition -> queueNumber(discoveryMessages, definition)
                is TextDefinition -> queueText(discoveryMessages, definition)
                is TriggerDefinition -> queueDeviceTrigger(discoveryMessages, definition)
            }
        }
        
        // Publish all messages with a delay between each to avoid flooding
        publishQueuedMessages(discoveryMessages, 0)
    }
    
    /**
     * Create the device configuration JSON
     */
    private fun createDeviceConfigJson(): JSONObject {
        val safeDeviceIdSuffix = deviceId.replace(Regex("[^a-zA-Z0-9_-]"), "_").takeLast(12)
        return JSONObject().apply {
            put("identifiers", JSONArray().put("${HA_OBJECT_ID_PREFIX}${deviceId}"))
            put("name", "$HA_DEVICE_NAME_PREFIX$safeDeviceIdSuffix")
            put("manufacturer", HA_DEVICE_MANUFACTURER)
            put("model", HA_DEVICE_MODEL)
            put("sw_version", HA_SW_VERSION)
        }
    }
    
    /**
     * Create a base payload for Home Assistant entities
     */
    private fun createBasePayload(
        name: String, 
        uniqueIdSuffix: String, 
        icon: String? = null, 
        category: String? = null
    ): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("unique_id", "${baseUniqueId}_$uniqueIdSuffix")
            put("availability_topic", availabilityTopic)
            put("payload_available", HA_PAYLOAD_AVAILABLE)
            put("payload_not_available", HA_PAYLOAD_NOT_AVAILABLE)
            put("device", deviceJson)
            icon?.let { put("icon", it) }
            category?.let { put("entity_category", it) }
        }
    }
    
    /**
     * Queue a sensor entity for publishing
     */
    private fun queueSensor(messages: MutableList<Pair<String, String>>, definition: SensorDefinition) {
        val payload = createBasePayload(
            definition.name, 
            definition.uniqueIdSuffix, 
            definition.icon, 
            definition.category
        ).apply {
            put("state_topic", definition.stateTopic)
            put("value_template", definition.valueTemplate)
            definition.unitOfMeasurement?.let { put("unit_of_measurement", it) }
            definition.deviceClass?.let { put("device_class", it) }
        }
        
        messages.add(Pair(
            "$HA_DISCOVERY_PREFIX/sensor/$baseUniqueId/${definition.uniqueIdSuffix}/config", 
            payload.toString()
        ))
    }
    
    /**
     * Queue a button entity for publishing
     */
    private fun queueButton(messages: MutableList<Pair<String, String>>, definition: ButtonDefinition) {
        val payload = createBasePayload(
            definition.name, 
            definition.uniqueIdSuffix, 
            definition.icon, 
            definition.category
        ).apply {
            put("command_topic", definition.commandTopic)
            put("payload_press", definition.payload)
        }
        
        messages.add(Pair(
            "$HA_DISCOVERY_PREFIX/button/$baseUniqueId/${definition.uniqueIdSuffix}/config", 
            payload.toString()
        ))
    }
    
    /**
     * Queue a switch entity for publishing
     */
    private fun queueSwitch(messages: MutableList<Pair<String, String>>, definition: SwitchDefinition) {
        val payload = createBasePayload(
            definition.name, 
            definition.uniqueIdSuffix, 
            definition.icon, 
            definition.category
        ).apply {
            put("state_topic", definition.stateTopic)
            put("command_topic", definition.commandTopic)
            put("value_template", definition.valueTemplate)
            put("payload_on", definition.payloadOn)
            put("payload_off", definition.payloadOff)
            put("state_on", true)
            put("state_off", false)
            definition.deviceClass?.let { put("device_class", it) }
        }
        
        messages.add(Pair(
            "$HA_DISCOVERY_PREFIX/switch/$baseUniqueId/${definition.uniqueIdSuffix}/config", 
            payload.toString()
        ))
    }
    
    /**
     * Queue a number entity for publishing
     */
    private fun queueNumber(messages: MutableList<Pair<String, String>>, definition: NumberDefinition) {
        val payload = createBasePayload(
            definition.name, 
            definition.uniqueIdSuffix, 
            definition.icon, 
            definition.category
        ).apply {
            put("state_topic", definition.stateTopic)
            put("command_topic", definition.commandTopic)
            put("value_template", definition.valueTemplate)
            put("command_template", definition.commandTemplate)
            put("min", definition.min)
            put("max", definition.max)
            put("step", definition.step)
            put("mode", definition.mode)
            definition.unitOfMeasurement?.let { put("unit_of_measurement", it) }
        }
        
        messages.add(Pair(
            "$HA_DISCOVERY_PREFIX/number/$baseUniqueId/${definition.uniqueIdSuffix}/config", 
            payload.toString()
        ))
    }
    
    /**
     * Queue a text entity for publishing
     */
    private fun queueText(messages: MutableList<Pair<String, String>>, definition: TextDefinition) {
        val payload = createBasePayload(
            definition.name, 
            definition.uniqueIdSuffix, 
            definition.icon, 
            definition.category
        ).apply {
            put("command_topic", definition.commandTopic)
            put("command_template", definition.commandTemplate)
            put("mode", definition.mode)
            definition.stateTopic?.let { put("state_topic", it) }
            definition.valueTemplate?.let { put("value_template", it) }
        }
        
        messages.add(Pair(
            "$HA_DISCOVERY_PREFIX/text/$baseUniqueId/${definition.uniqueIdSuffix}/config", 
            payload.toString()
        ))
    }
    
    /**
     * Queue a device trigger for publishing
     */
    private fun queueDeviceTrigger(messages: MutableList<Pair<String, String>>, definition: TriggerDefinition) {
        val payload = JSONObject().apply {
            put("automation_type", "trigger")
            put("topic", definition.topic)
            put("type", definition.triggerType)
            put("subtype", definition.triggerSubtype)
            put("payload", definition.payload)
            put("device", deviceJson)
        }
        
        val discoveryTopic = "$HA_DISCOVERY_PREFIX/$HA_DEVICE_AUTOMATION_PREFIX/$baseUniqueId/${definition.triggerType}_${definition.triggerSubtype}/config"
        messages.add(Pair(discoveryTopic, payload.toString()))
    }
    
    /**
     * Publish all queued messages with a delay between each
     */
    private fun publishQueuedMessages(messages: List<Pair<String, String>>, index: Int) {
        if (index >= messages.size) {
            Log.d(TAG, "All discovery messages published.")
            return
        }
        
        val (topic, payload) = messages[index]
        try {
            mqttService.publish(topic, payload, true) // Retained = true
            Log.d(TAG, "Published HA discovery (${index + 1}/${messages.size}) to $topic")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish HA discovery for $topic: ${e.message}", e)
        }
        
        Handler(Looper.getMainLooper()).postDelayed({
            publishQueuedMessages(messages, index + 1)
        }, DISCOVERY_PUBLISH_DELAY_MS)
    }
    
    /**
     * Send an availability message
     */
    fun publishAvailability(isAvailable: Boolean) {
        val payload = if (isAvailable) HA_PAYLOAD_AVAILABLE else HA_PAYLOAD_NOT_AVAILABLE
        try {
            mqttService.publish(availabilityTopic, payload, true)
            Log.d(TAG, "Published availability: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish availability", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        // Send offline status before destroying
        try {
            mqttService.publish(availabilityTopic, HA_PAYLOAD_NOT_AVAILABLE, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing offline status", e)
        }
    }
}

// Component definition classes
sealed class ComponentDefinition

data class SensorDefinition(
    val name: String,
    val uniqueIdSuffix: String,
    val stateTopic: String,
    val valueTemplate: String,
    val icon: String? = null,
    val category: String? = null,
    val unitOfMeasurement: String? = null,
    val deviceClass: String? = null
) : ComponentDefinition()

data class ButtonDefinition(
    val name: String,
    val uniqueIdSuffix: String,
    val commandTopic: String,
    val payload: String,
    val icon: String? = null,
    val category: String? = null
) : ComponentDefinition()

data class SwitchDefinition(
    val name: String,
    val uniqueIdSuffix: String,
    val stateTopic: String,
    val commandTopic: String,
    val valueTemplate: String,
    val payloadOn: String,
    val payloadOff: String,
    val icon: String? = null,
    val category: String? = null,
    val deviceClass: String? = null
) : ComponentDefinition()

data class NumberDefinition(
    val name: String,
    val uniqueIdSuffix: String,
    val stateTopic: String,
    val commandTopic: String,
    val valueTemplate: String,
    val commandTemplate: String,
    val min: Number,
    val max: Number,
    val step: Number,
    val mode: String,
    val icon: String? = null,
    val category: String? = null,
    val unitOfMeasurement: String? = null
) : ComponentDefinition()

data class TextDefinition(
    val name: String,
    val uniqueIdSuffix: String,
    val commandTopic: String,
    val commandTemplate: String,
    val icon: String? = null,
    val category: String? = null,
    val mode: String = "text",
    val stateTopic: String? = null,
    val valueTemplate: String? = null
) : ComponentDefinition()

data class TriggerDefinition(
    val triggerType: String,
    val triggerSubtype: String,
    val topic: String,
    val payload: String
) : ComponentDefinition()
