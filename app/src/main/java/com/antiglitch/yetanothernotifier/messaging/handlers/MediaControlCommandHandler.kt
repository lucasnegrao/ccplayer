package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.CommandResult
import com.antiglitch.yetanothernotifier.messaging.MessageHandlingService
import com.antiglitch.yetanothernotifier.services.HybridMediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Handler for media control commands
 */
class MediaControlCommandHandler(
    private val context: Context,
    private var hybridMediaController: HybridMediaController? = null
) : CommandHandler {
    companion object {
        private const val TAG = "MediaControlCmdHandler"
        
        // Action patterns
        private const val PLAY = "media_play"
        private const val PAUSE = "media_pause"
        private const val TOGGLE_PLAY = "media_toggle_play"
        private const val NEXT = "media_next"
        private const val PREVIOUS = "media_previous"
        private const val STOP = "media_stop"
        private const val GET_STATE = "media_get_state"
        private const val GET_METADATA = "media_get_metadata"
        private const val SEEK_TO = "media_seek_to"
        private const val ADJUST_VOLUME = "media_adjust_volume"
        private const val LOAD_URL = "media_load_url"
        private const val ENQUEUE_URL = "media_enqueue_url"
        private const val GET_QUEUE = "media_get_queue"
        private const val PLAY_FROM_QUEUE = "media_play_from_queue" // New action
    }
    
    private val messageHandler by lazy {
        MessageHandlingService.getInstance(context)
    }
    
    override val actionPattern = "media_.*"
    
    override fun canHandle(command: Command): Boolean {
        // Don't handle internal update commands - these are for broadcasting state changes
        if (command.action == "media_state_update" || 
            command.action == "media_metadata_update" || 
            command.action == "media_item_update" ||
            command.action == "media_queue_update") {
            return false
        }
        return command.action.startsWith("media_")
    }
    
    /**
     * Update the media controller
     */
    fun updateMediaController(controller: HybridMediaController?) {
        this.hybridMediaController = controller
        Log.d(TAG, "Media controller updated: ${controller?.let { "CustomMediaController with session: ${HybridMediaController.sessionToken}" } ?: "null"}")
    }
    
    /**
     * Get the current media controller
     */
    fun getMediaController(): HybridMediaController? = hybridMediaController
    
    /**
     * Check if we have a valid media controller
     */
    private fun hasValidController(): Boolean {
        return hybridMediaController?.mediaController != null
    }
    
    override suspend fun handle(command: Command): CommandResult {
        if (!hasValidController()) {
            return CommandResult.Error("No media controller available")
        }
        
        return when (command.action) {
            PLAY -> handlePlay()
            PAUSE -> handlePause()
            TOGGLE_PLAY -> handleTogglePlay()
            NEXT -> handleNext()
            PREVIOUS -> handlePrevious()
            STOP -> handleStop()
            GET_STATE -> handleGetState()
            GET_METADATA -> handleGetMetadata()
            SEEK_TO -> handleSeekTo(command.payload)
            ADJUST_VOLUME -> handleAdjustVolume(command.payload)
            LOAD_URL -> handleLoadUrl(command.payload)
            ENQUEUE_URL -> handleEnqueueUrl(command.payload)
            GET_QUEUE -> handleGetQueue()
            PLAY_FROM_QUEUE -> handlePlayFromQueue(command.payload) // New handler call
            else -> CommandResult.Error("Unknown media action: ${command.action}")
        }
    }
    
    /**
     * Handle play command
     */
    private suspend fun handlePlay(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        return@withContext try {
            controller.play()
            
            // Wait briefly for playback state to update
            delay(300)
            sendMediaStateUpdate()
            
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to play media: ${e.message}", e)
        }
    }
    
    /**
     * Handle pause command
     */
    private suspend fun handlePause(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        return@withContext try {
            controller.pause()
            
            // Wait briefly for playback state to update
            delay(300)
            sendMediaStateUpdate()
            
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to pause media: ${e.message}", e)
        }
    }
    
    /**
     * Handle toggle play/pause command
     */
    private suspend fun handleTogglePlay(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        return@withContext try {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
            
            // Wait briefly for playback state to update
            delay(300)
            sendMediaStateUpdate()
            
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to toggle play/pause: ${e.message}", e)
        }
    }
    
    /**
     * Handle next track command
     */
    private suspend fun handleNext(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        return@withContext try {
            controller.seekToNext()
            
            // Wait briefly for media info to update
            delay(500)
            // sendMediaMetadataUpdate()
            
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to skip to next track: ${e.message}", e)
        }
    }
    
    /**
     * Handle previous track command
     */
    private suspend fun handlePrevious(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        return@withContext try {
            controller.seekToPrevious()
            
            // Wait briefly for media info to update
            delay(500)
            // sendMediaMetadataUpdate()
            
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to skip to previous track: ${e.message}", e)
        }
    }
    
    /**
     * Handle stop command
     */
    private suspend fun handleStop(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        return@withContext try {
            controller.stop()
            
            // Wait briefly for playback state to update
            delay(300)
            sendMediaStateUpdate()
            
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to stop media: ${e.message}", e)
        }
    }
    
    /**
     * Handle get state command
     */
    private suspend fun handleGetState(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController
        
        return@withContext if (controller != null) {
            val stateInfo = getMediaStateInfo(controller)
            CommandResult.Success(stateInfo)
        } else {
            CommandResult.Success(mapOf(
                "active" to false,
                "state" to "no_controller"
            ))
        }
    }
    
    /**
     * Handle get metadata command
     */
    private suspend fun handleGetMetadata(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController
        
        return@withContext if (controller != null) {
            controller.currentMediaItem?.let { mediaItem ->
                val serializedMediaItem = serializeMediaItem(mediaItem)
                CommandResult.Success(mapOf(
                    "active" to true,
                    "sessionToken" to HybridMediaController.sessionToken?.toString(),
                    "packageName" to context.packageName,
                    "mediaItem" to serializedMediaItem
                ))
            } ?: CommandResult.Success(mapOf(
                "active" to true,
                "sessionToken" to HybridMediaController.sessionToken?.toString(),
                "packageName" to context.packageName,
                "mediaItem" to null
            ))
        } else {
            CommandResult.Success(mapOf(
                "active" to false,
                "mediaItem" to null
            ))
        }
    }
    
    /**
     * Handle seek to position command
     */
    private suspend fun handleSeekTo(payload: Map<String, Any?>): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        val position = when (val posValue = payload["position"]) {
            is Number -> posValue.toLong()
            is String -> posValue.toLongOrNull() 
                ?: return@withContext CommandResult.Error("Invalid position value: $posValue")
            else -> return@withContext CommandResult.Error("Missing or invalid position value")
        }
        
        return@withContext try {
            controller.seekTo(position)
            
            // Wait briefly for playback position to update
            delay(300)
            sendMediaStateUpdate()
            
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to seek to position: ${e.message}", e)
        }
    }
    
    /**
     * Handle adjust volume command
     */
    private suspend fun handleAdjustVolume(payload: Map<String, Any?>): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        // Extract direction of adjustment - support multiple parameter names
        val direction = when {
            payload.containsKey("direction") -> {
                when (val dirValue = payload["direction"]) {
                    is String -> when (dirValue.lowercase()) {
                        "up" -> 0.1f
                        "down" -> -0.1f
                        else -> return@withContext CommandResult.Error("Invalid direction value: $dirValue")
                    }
                    is Number -> dirValue.toFloat()
                    else -> return@withContext CommandResult.Error("Invalid direction value type: ${dirValue?.javaClass?.simpleName}")
                }
            }
            payload.containsKey("volume") -> {
                when (val volValue = payload["volume"]) {
                    is Number -> volValue.toFloat()
                    is String -> volValue.toFloatOrNull() 
                        ?: return@withContext CommandResult.Error("Invalid volume value: $volValue")
                    else -> return@withContext CommandResult.Error("Invalid volume value type: ${volValue?.javaClass?.simpleName}")
                }
            }
            payload.containsKey("delta") -> {
                when (val deltaValue = payload["delta"]) {
                    is Number -> deltaValue.toFloat()
                    is String -> deltaValue.toFloatOrNull() 
                        ?: return@withContext CommandResult.Error("Invalid delta value: $deltaValue")
                    else -> return@withContext CommandResult.Error("Invalid delta value type: ${deltaValue?.javaClass?.simpleName}")
                }
            }
            else -> {
                Log.w(TAG, "Volume adjustment payload: $payload")
                return@withContext CommandResult.Error("Missing volume parameter. Expected 'direction', 'volume', or 'delta'. Available keys: ${payload.keys}")
            }
        }
        
        return@withContext try {
            val newVolume = if (payload.containsKey("volume")) {
                // Absolute volume setting
                direction.coerceIn(0f, 1f)
            } else {
                // Relative volume adjustment
                val currentVolume = controller.volume
                (currentVolume + direction).coerceIn(0f, 1f)
            }
            
            controller.volume = newVolume
            
            CommandResult.Success(mapOf(
                "volume" to newVolume,
                "previousVolume" to controller.volume
            ))
        } catch (e: Exception) {
            CommandResult.Error("Failed to adjust volume: ${e.message}", e)
        }
    }
    
    /**
     * Handle load URL command
     */
    private suspend fun handleLoadUrl(payload: Map<String, Any?>): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        val url = when (val urlValue = payload["url"]) {
            is String -> urlValue.trim()
            else -> return@withContext CommandResult.Error("Missing or invalid URL value")
        }
        
        if (url.isEmpty()) {
            return@withContext CommandResult.Error("URL cannot be empty")
        }
        
        return@withContext try {
            Log.d(TAG, "Loading URL: $url")
            controller.loadUrl(url)
            
            // Wait briefly for media to start loading
            delay(1000)
            sendMediaStateUpdate()
            
            CommandResult.Success(mapOf("loadedUrl" to url))
        } catch (e: Exception) {
            CommandResult.Error("Failed to load URL: ${e.message}", e)
        }
    }
    
    /**
     * Handle enqueue URL command
     */
    private suspend fun handleEnqueueUrl(payload: Map<String, Any?>): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        val url = when (val urlValue = payload["url"]) {
            is String -> urlValue.trim()
            else -> return@withContext CommandResult.Error("Missing or invalid URL value")
        }
        
        if (url.isEmpty()) {
            return@withContext CommandResult.Error("URL cannot be empty")
        }
        
        return@withContext try {
            Log.d(TAG, "Enqueuing URL: $url")
            controller.enqueueUrl(url)
            
            // Wait briefly for media to be added to queue
            delay(500)
            sendMediaStateUpdate()
            
            CommandResult.Success(mapOf("enqueuedUrl" to url))
        } catch (e: Exception) {
            CommandResult.Error("Failed to enqueue URL: ${e.message}", e)
        }
    }
    
    /**
     * Handle play from queue command
     */
    private suspend fun handlePlayFromQueue(payload: Map<String, Any?>): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")

        val titleToPlay = payload["title"] as? String
            ?: return@withContext CommandResult.Error("Missing or invalid 'title' in payload")

        var itemFound = false
        for (i in 0 until controller.mediaItemCount) {
            val mediaItem = controller.getMediaItemAt(i)
            if (mediaItem.mediaMetadata.title?.toString() == titleToPlay) {
                controller.seekTo(i, 0L) // Seek to the beginning of the item
                controller.play() // Ensure playback starts
                itemFound = true
                break
            }
        }

        return@withContext if (itemFound) {
            // Wait briefly for playback state and metadata to update
            delay(500)
            sendMediaStateUpdate() // This will also trigger media_item and media_queue updates
            CommandResult.Success(mapOf("playedFromQueue" to titleToPlay))
        } else {
            CommandResult.Error("Media item with title '$titleToPlay' not found in queue.")
        }
    }
    
    /**
     * Handle get queue command
     */
    private suspend fun handleGetQueue(): CommandResult = withContext(Dispatchers.Main) {
        val controller = hybridMediaController?.mediaController ?:
            return@withContext CommandResult.Error("No media controller available")
        
        return@withContext try {
            val queueData = getQueueInfo(controller)
            
            // Also send as internal command for MQTT publishing
            messageHandler.sendInternalCommand("media_queue_update", queueData)
            
            CommandResult.Success(queueData)
        } catch (e: Exception) {
            CommandResult.Error("Failed to get queue: ${e.message}", e)
        }
    }
    
    /**
     * Get media state information using Media3 Player
     */
    private fun getMediaStateInfo(controller: androidx.media3.session.MediaController): Map<String, Any?> {
        val stateInfo = mutableMapOf<String, Any?>(
            "active" to true,
            "sessionToken" to HybridMediaController.sessionToken?.toString(),
            "packageName" to context.packageName
        )
        
        // Player state
        stateInfo["state"] = when (controller.playbackState) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> if (controller.playWhenReady) "playing" else "paused"
            Player.STATE_ENDED -> "ended"
            else -> "unknown"
        }
        
        stateInfo["isPlaying"] = controller.isPlaying
        stateInfo["playWhenReady"] = controller.playWhenReady
        stateInfo["position"] = controller.currentPosition
        stateInfo["duration"] = controller.duration
        stateInfo["bufferedPosition"] = controller.bufferedPosition
        stateInfo["playbackSpeed"] = controller.playbackParameters.speed
        stateInfo["volume"] = controller.volume
        stateInfo["currentMediaItemIndex"] = controller.currentMediaItemIndex
        stateInfo["mediaItemCount"] = controller.mediaItemCount
        
        // Add active queue item title for HA select component
        stateInfo["active_queue_item_title"] = controller.currentMediaItem?.mediaMetadata?.title?.toString()
        
        return stateInfo
    }
    
    /**
     * Get queue information from the media controller
     */
    private fun getQueueInfo(controller: androidx.media3.session.MediaController): Map<String, Any?> {
        val queueItems = mutableListOf<Map<String, Any?>>()
        
        // Get all media items in the queue
        for (i in 0 until controller.mediaItemCount) {
            val mediaItem = controller.getMediaItemAt(i)
            val serializedItem = serializeMediaItem(mediaItem).toMutableMap()
            
            // Add queue-specific information
            serializedItem["queueIndex"] = i
            serializedItem["isCurrentItem"] = (i == controller.currentMediaItemIndex)
            
            queueItems.add(serializedItem)
        }
        
        return mapOf(
            "queueSize" to controller.mediaItemCount,
            "currentIndex" to controller.currentMediaItemIndex,
            "hasNext" to controller.hasNextMediaItem(),
            "hasPrevious" to controller.hasPreviousMediaItem(),
            "shuffleModeEnabled" to controller.shuffleModeEnabled,
            "repeatMode" to when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> "off"
                Player.REPEAT_MODE_ONE -> "one"
                Player.REPEAT_MODE_ALL -> "all"
                else -> "unknown"
            },
            "items" to queueItems
        )
    }
    
    /**
     * Serialize complete MediaItem to Map
     */
    @OptIn(UnstableApi::class)
    private fun serializeMediaItem(mediaItem: MediaItem): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        result["mediaId"] = mediaItem.mediaId
        result["uri"] = mediaItem.localConfiguration?.uri?.toString()
        result["mimeType"] = mediaItem.localConfiguration?.mimeType
//        result["streamKeys"] = mediaItem.localConfiguration?.streamKeys?.map {
//            mapOf(
//                "groupIndex" to it.groupIndex,
//                "trackIndex" to it.trackIndex
//            )
//        }
        result["customCacheKey"] = mediaItem.localConfiguration?.customCacheKey
        result["subtitleConfigurations"] = mediaItem.localConfiguration?.subtitleConfigurations?.map {
            mapOf(
                "uri" to it.uri.toString(),
                "mimeType" to it.mimeType,
                "language" to it.language,
                "selectionFlags" to it.selectionFlags,
                "roleFlags" to it.roleFlags,
                "label" to it.label
            )
        }
        result["tag"] = mediaItem.localConfiguration?.tag?.toString()
        result["metadata"] = serializeMediaMetadata(mediaItem.mediaMetadata)
        result["requestMetadata"] = mediaItem.requestMetadata?.let { requestMetadata ->
            mapOf(
                "uri" to requestMetadata.mediaUri?.toString(),
                "extras" to requestMetadata.extras?.let { bundleToMap(it) }
            )
        }
        
        return result.filterValues { it != null }
    }
    
    /**
     * Convert Media3 MediaMetadata to a serializable Map
     */
    private fun serializeMediaMetadata(metadata: MediaMetadata): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        // Text fields
        metadata.title?.let { result["title"] = it.toString() }
        metadata.artist?.let { result["artist"] = it.toString() }
        metadata.albumTitle?.let { result["albumTitle"] = it.toString() }
        metadata.albumArtist?.let { result["albumArtist"] = it.toString() }
        metadata.displayTitle?.let { result["displayTitle"] = it.toString() }
        metadata.subtitle?.let { result["subtitle"] = it.toString() }
        metadata.description?.let { result["description"] = it.toString() }
        metadata.genre?.let { result["genre"] = it.toString() }
        metadata.composer?.let { result["composer"] = it.toString() }
        metadata.conductor?.let { result["conductor"] = it.toString() }
        metadata.writer?.let { result["writer"] = it.toString() }
        metadata.compilation?.let { result["compilation"] = it.toString() }
        metadata.station?.let { result["station"] = it.toString() }
        
        // Numeric fields
        metadata.releaseYear?.let { result["releaseYear"] = it }
        metadata.releaseMonth?.let { result["releaseMonth"] = it }
        metadata.releaseDay?.let { result["releaseDay"] = it }
        metadata.recordingYear?.let { result["recordingYear"] = it }
        metadata.recordingMonth?.let { result["recordingMonth"] = it }
        metadata.recordingDay?.let { result["recordingDay"] = it }
        metadata.trackNumber?.let { result["trackNumber"] = it }
        metadata.totalTrackCount?.let { result["totalTrackCount"] = it }
        metadata.discNumber?.let { result["discNumber"] = it }
        metadata.totalDiscCount?.let { result["totalDiscCount"] = it }
        metadata.durationMs?.let { result["durationMs"] = it }
        
        // Media type
        result["mediaType"] = metadata.mediaType
        
        // URIs
        metadata.artworkUri?.let { result["artworkUri"] = it.toString() }
        
        // Extras
        metadata.extras?.let { extras ->
            result["extras"] = bundleToMap(extras)
        }
        
        // Boolean flags
        result["isPlayable"] = metadata.isPlayable
        result["isBrowsable"] = metadata.isBrowsable
        
        // Filter out null values
        return result.filterValues { it != null }
    }
    
    /**
     * Convert Bundle to Map for serialization
     */
    private fun bundleToMap(bundle: Bundle): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (key in bundle.keySet()) {
            map[key] = bundle.get(key)
        }
        return map
    }
    
    /**
     * Send media state update as an internal command
     */
    private fun sendMediaStateUpdate() {
        // hybridMediaController?.mediaController?.let { controller ->
        //     val stateInfo = getMediaStateInfo(controller)
        //     messageHandler.sendInternalCommand("media_state_update", stateInfo)
        //     messageHandler.broadcastUpdate("media_state", stateInfo)
            
        //     // Send current media item on separate topic if available
        //     controller.currentMediaItem?.let { mediaItem ->
        //         val serializedMediaItem = serializeMediaItem(mediaItem)
        //         messageHandler.sendInternalCommand("media_item_update", serializedMediaItem)
        //         messageHandler.broadcastUpdate("media_item", serializedMediaItem)
        //     }
            
        //     // Also send queue update when state changes
        //     val queueInfo = getQueueInfo(controller)
        //     messageHandler.sendInternalCommand("media_queue_update", queueInfo)
        // }
    }
    
    /**
     * Send media metadata update as an internal command
     */
    private fun sendMediaMetadataUpdate() {
        hybridMediaController?.mediaController?.let { controller ->
            // Send complete media item instead of just metadata
            controller.currentMediaItem?.let { mediaItem ->
                val serializedMediaItem = serializeMediaItem(mediaItem)
                messageHandler.sendInternalCommand("media_item_update", serializedMediaItem)
                messageHandler.broadcastUpdate("media_item", serializedMediaItem)
            }
        }
    }
}
