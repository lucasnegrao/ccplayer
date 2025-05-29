package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.CommandResult
import com.antiglitch.yetanothernotifier.repository.MediaControllerStateRepository
import com.antiglitch.yetanothernotifier.repository.MediaControllerStateListener
import com.antiglitch.yetanothernotifier.repository.MediaTransportState
import com.antiglitch.yetanothernotifier.repository.MediaContentState
import com.antiglitch.yetanothernotifier.repository.MediaErrorState
import com.antiglitch.yetanothernotifier.repository.MediaControllerConnectionState
import com.antiglitch.yetanothernotifier.services.MqttService
import com.antiglitch.yetanothernotifier.data.repository.MqttPropertiesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handler for internal media state update commands that publishes to MQTT
 * and listens to state repository changes
 */
class MediaStateUpdateHandler(
    private val context: Context
) : CommandHandler, MediaControllerStateListener {
    
    companion object {
        private const val TAG = "MediaStateUpdateHandler"
    }
    
    override val actionPattern = "media_(state|item|queue)_update"
    
    private val handlerScope = CoroutineScope(Dispatchers.IO)
    private val stateRepository = MediaControllerStateRepository.getInstance()
    private var homeAssistantDiscovery: com.antiglitch.yetanothernotifier.integration.HomeAssistantDiscovery? = null
    private var lastPlaylistTitles: List<String> = emptyList() // Track previous playlist
    
    init {
        // Subscribe to state repository changes
        stateRepository.addListener(this)
        
        // Initialize Home Assistant discovery integration
        try {
            homeAssistantDiscovery = com.antiglitch.yetanothernotifier.integration.HomeAssistantDiscovery(context)
        } catch (e: Exception) {
            Log.w(TAG, "Home Assistant discovery not available: ${e.message}")
        }
        
        Log.d(TAG, "MediaStateUpdateHandler initialized and listening to state changes")
    }
    
    // State repository listener implementations
    override fun onTransportStateChanged(state: MediaTransportState) {
        Log.d(TAG, "Transport state changed: ${state.playbackStateString}, playing: ${state.isPlaying}")
        handlerScope.launch {
            val payload = transportStateToMap(state)
            publishToMqtt("yan/status/media_state", payload)
        }
    }
    
    override fun onContentStateChanged(state: MediaContentState) {
        Log.d(TAG, "Content state changed: currentItem: ${state.currentMediaItem?.mediaId}, count: ${state.mediaItemCount}")
        handlerScope.launch {
            val itemPayload = contentStateToMap(state)
            publishToMqtt("yan/status/media_item", itemPayload)
            
            val queuePayload = playlistToMap(state)
            publishToMqtt("yan/status/media_queue", queuePayload)
            
            // Only update Home Assistant if playlist actually changed
            updateHomeAssistantQueueIfChanged(state)
        }
    }
    
    override fun onErrorStateChanged(state: MediaErrorState) {
        Log.d(TAG, "Error state changed: ${state.errorMessage}")
        handlerScope.launch {
            val payload = errorStateToMap(state)
            publishToMqtt("yan/status/media_error", payload)
        }
    }
    
    override fun onConnectionStateChanged(state: MediaControllerConnectionState) {
        Log.d(TAG, "Connection state changed: connected: ${state.isConnected}, initialized: ${state.isInitialized}")
        handlerScope.launch {
            val payload = connectionStateToMap(state)
            publishToMqtt("yan/status/media_connection", payload)
        }
    }
    
    override fun canHandle(command: Command): Boolean {
        return command.action == "media_state_update" || 
               command.action == "media_item_update" ||
               command.action == "media_queue_update"
    }
    
    override suspend fun handle(command: Command): CommandResult {
        return when (command.action) {
            "media_state_update" -> handleMediaStateUpdate(command.payload)
            "media_item_update" -> handleMediaItemUpdate(command.payload)
            "media_queue_update" -> handleMediaQueueUpdate(command.payload)
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
     * Handle media queue update - publishes current queue items to MQTT
     */
    private suspend fun handleMediaQueueUpdate(payload: Map<String, Any?>): CommandResult {
        return try {
            Log.d(TAG, "Publishing media queue update to MQTT")
            
            // Publish to MQTT if available and enabled
            publishToMqtt("yan/status/media_queue", payload)
            
            CommandResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling media queue update", e)
            CommandResult.Error("Failed to publish media queue: ${e.message}", e)
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
                    is List<*> -> jsonObject.put(key, listToJsonArray(value))
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
     * Convert List to JSONArray
     */
    private fun listToJsonArray(list: List<*>): JSONArray {
        val jsonArray = JSONArray()
        list.forEach { item ->
            when (item) {
                null -> jsonArray.put(JSONObject.NULL)
                is Map<*, *> -> jsonArray.put(mapToJsonObject(item as Map<String, Any?>))
                is List<*> -> jsonArray.put(listToJsonArray(item))
                else -> jsonArray.put(item)
            }
        }
        return jsonArray
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
                is List<*> -> jsonObject.put(key, listToJsonArray(value))
                else -> jsonObject.put(key, value)
            }
        }
        return jsonObject
    }
    
    private fun transportStateToMap(state: MediaTransportState): Map<String, Any?> {
        return mapOf(
            "playbackState" to state.playbackState,
            "playbackStateString" to state.playbackStateString,
            "playWhenReady" to state.playWhenReady,
            "isPlaying" to state.isPlaying,
            "currentPosition" to state.currentPosition,
            "duration" to state.duration,
            "bufferedPosition" to state.bufferedPosition,
            "playbackSpeed" to state.playbackSpeed,
            "repeatMode" to state.repeatMode,
            "repeatModeString" to state.repeatModeString,
            "shuffleModeEnabled" to state.shuffleModeEnabled,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    private fun contentStateToMap(state: MediaContentState): Map<String, Any?> {
        return mapOf(
            "currentMediaItem" to mediaItemToMap(state.currentMediaItem),
            "currentIndex" to state.currentIndex,
            "mediaItemCount" to state.mediaItemCount,
            "hasNextMediaItem" to state.hasNextMediaItem,
            "hasPreviousMediaItem" to state.hasPreviousMediaItem,
            "nextMediaItemIndex" to state.nextMediaItemIndex,
            "previousMediaItemIndex" to state.previousMediaItemIndex,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    /**
     * Update Home Assistant queue component only if playlist has changed
     */
    private fun updateHomeAssistantQueueIfChanged(state: MediaContentState) {
        try {
            // Extract titles from playlist - handle both String and MediaItem types
            val currentPlaylistTitles = when {
                state.playlist.isNotEmpty() -> {
                    state.playlist.mapIndexed { index, item ->
                        when (item) {
                            is String -> item
                            is MediaItem -> {
                                val title = item.mediaMetadata.title?.toString()
                                if (title.isNullOrEmpty()) "Item ${index + 1}" else title
                            }
                            else -> "Item ${index + 1}"
                        }
                    }
                }
                else -> emptyList()
            }
            
            // Check if playlist has actually changed
            if (currentPlaylistTitles != lastPlaylistTitles) {
                lastPlaylistTitles = currentPlaylistTitles
                
                // Update the Home Assistant component
                homeAssistantDiscovery?.let { discovery ->
                    discovery.updateMediaQueueComponent(currentPlaylistTitles)
                    discovery.republishMediaQueueComponent()
                    Log.d(TAG, "Playlist changed - Updated Home Assistant queue component with ${currentPlaylistTitles.size} items")
                }
            } else {
                Log.d(TAG, "Playlist unchanged - skipping Home Assistant update")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Home Assistant queue component", e)
        }
    }
    
    private fun playlistToMap(state: MediaContentState): Map<String, Any?> {
        // Enhanced playlist mapping with index information for HA template
        val playlistWithIndex = state.playlist.mapIndexed { index, item ->
            when (item) {
                is String -> mapOf(
                    "title" to item, 
                    "index" to index
                )
                is MediaItem -> {
                    val itemMap = mediaItemToMap(item)?.toMutableMap() ?: mutableMapOf<String, Any?>()
                    itemMap["index"] = index
                    itemMap.toMap() // Ensure it's a proper Map
                }
                else -> mapOf(
                    "title" to "Item ${index + 1}", 
                    "index" to index
                )
            }
        }
        
        return mapOf(
            "playlist" to playlistWithIndex,
            "currentIndex" to state.currentIndex,
            "totalItems" to state.mediaItemCount,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    private fun errorStateToMap(state: MediaErrorState): Map<String, Any?> {
        return mapOf(
            "hasError" to (state.error != null),
            "errorMessage" to state.errorMessage,
            "errorCode" to state.errorCode,
            "errorCodeString" to state.errorCodeString,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    private fun connectionStateToMap(state: MediaControllerConnectionState): Map<String, Any?> {
        return mapOf(
            "isConnected" to state.isConnected,
            "isInitialized" to state.isInitialized,
            "timestamp" to System.currentTimeMillis()
        )
    }
    
    private fun mediaItemToMap(mediaItem: MediaItem?): Map<String, Any?>? {
        return mediaItem?.let { item ->
            mapOf(
                "mediaId" to item.mediaId,
                "uri" to item.localConfiguration?.uri?.toString(),
                "title" to item.mediaMetadata.title?.toString(),
                "artist" to item.mediaMetadata.artist?.toString(),
                "artworkUri" to item.mediaMetadata.artworkUri?.toString(),
                "contentType" to item.mediaMetadata.extras?.getString("content_type"),
                "mediaType" to item.mediaMetadata.mediaType,
                "tag" to item.localConfiguration?.tag?.toString()
            )
        }
    }
    
    /**
     * Cleanup method to remove listener
     */
    fun cleanup() {
        stateRepository.removeListener(this)
        homeAssistantDiscovery = null
        lastPlaylistTitles = emptyList() // Clear cached playlist
        Log.d(TAG, "MediaStateUpdateHandler cleaned up")
    }
}
