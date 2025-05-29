package com.antiglitch.yetanothernotifier.services

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionToken
import com.antiglitch.yetanothernotifier.repository.MediaControllerStateRepository
import com.antiglitch.yetanothernotifier.repository.MediaTransportState
import com.antiglitch.yetanothernotifier.repository.MediaContentState
import com.antiglitch.yetanothernotifier.repository.MediaErrorState
import com.antiglitch.yetanothernotifier.repository.MediaControllerConnectionState
import com.antiglitch.yetanothernotifier.utils.StreamType
import com.antiglitch.yetanothernotifier.utils.StreamTypeDetector
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.media3.session.MediaController as Media3MediaController

interface MediaControllerCallback {
    fun onInitialized(success: Boolean)
    fun onLoadRequest()
    fun onError(error: String)
    fun onMediaLoaded(mediaItem: MediaItem)
}

interface MediaControllerInterface {
    fun initialize()
    fun loadUrl(url: String)
    fun release()
}

class HybridMediaController(
    private var context: Context,
    private var callback: MediaControllerCallback
) : MediaControllerInterface {

    internal var mediaController: Media3MediaController? = null
    private val stateRepository = MediaControllerStateRepository.getInstance()
    private val positionUpdateHandler = Handler(Looper.getMainLooper())
    private var positionUpdateRunnable: Runnable? = null
    private var isPositionTimerActive = false

    companion object {
        var sessionToken: SessionToken? = null
        const val LOG_TAG: String = "MediaController"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L // Update every second
    }

    init {
        internalInit()
        // YoutubeDL initialization moved to StreamTypeDetector
    }

    private fun internalInit() {
        Log.d(LOG_TAG, "Initializing MediaController...")
        sessionToken = SessionToken(context, ComponentName(context, MediaService::class.java))
        val controllerFuture: ListenableFuture<Media3MediaController> =
            Media3MediaController.Builder(context, sessionToken!!).buildAsync()

        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                mediaController?.let { controller ->
                    setupControllerListener(controller)
                    stateRepository.updateConnectionState(
                        MediaControllerConnectionState(isConnected = true, isInitialized = true)
                    )
                    callback.onInitialized(true)
                } ?: run {
                    Log.e(LOG_TAG, "MediaController future completed but controller is null.")
                    stateRepository.updateConnectionState(
                        MediaControllerConnectionState(isConnected = false, isInitialized = false)
                    )
                    callback.onInitialized(false)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error initializing MediaController", e)
                stateRepository.updateConnectionState(
                    MediaControllerConnectionState(isConnected = false, isInitialized = false)
                )
                callback.onInitialized(false)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener(controller: Media3MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateTransportState()
                handlePositionTimer()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateTransportState()
                handlePositionTimer()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateTransportState()
                handlePositionTimer()
            }

//            override fun onPlaybackSpeedChanged(playbackSpeed: Float) {
//                updateTransportState()
//            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateTransportState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateTransportState()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateTransportState()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateContentState()
                updateTransportState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateContentState()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateContentState()
            }

            override fun onPlaylistMetadataChanged(playlistMetadata: MediaMetadata) {
                updateContentState()
            }

            override fun onPlayerError(error: PlaybackException) {
                stateRepository.updateErrorState(
                    MediaErrorState(
                        error = error,
                        errorMessage = error.message,
                        errorCode = error.errorCode,
                        errorCodeString = MediaErrorState.getErrorCodeString(error.errorCode)
                    )
                )
            }
        })
    }

    private fun handlePositionTimer() {
        mediaController?.let { controller ->
            val shouldRunTimer = controller.isPlaying && 
                                controller.playbackState == Player.STATE_READY &&
                                controller.duration > 0
            
            if (shouldRunTimer && !isPositionTimerActive) {
                startPositionTimer()
            } else if (!shouldRunTimer && isPositionTimerActive) {
                stopPositionTimer()
            }
        }
    }

    private fun startPositionTimer() {
        if (isPositionTimerActive) return
        
        isPositionTimerActive = true
        positionUpdateRunnable = object : Runnable {
            override fun run() {
                if (isPositionTimerActive) {
                    updateTransportState()
                    positionUpdateHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
                }
            }
        }
        positionUpdateHandler.post(positionUpdateRunnable!!)
        Log.d(LOG_TAG, "Position timer started")
    }

    private fun stopPositionTimer() {
        if (!isPositionTimerActive) return
        
        isPositionTimerActive = false
        positionUpdateRunnable?.let { runnable ->
            positionUpdateHandler.removeCallbacks(runnable)
        }
        positionUpdateRunnable = null
        Log.d(LOG_TAG, "Position timer stopped")
    }

    private fun updateTransportState() {
        mediaController?.let { controller ->
            stateRepository.updateTransportState(
                MediaTransportState(
                    playbackState = controller.playbackState,
                    playbackStateString = MediaTransportState.getPlaybackStateString(controller.playbackState),
                    playWhenReady = controller.playWhenReady,
                    isPlaying = controller.isPlaying,
                    currentPosition = controller.currentPosition,
                    duration = controller.duration,
                    bufferedPosition = controller.bufferedPosition,
                    playbackSpeed = controller.playbackParameters.speed,
                    repeatMode = controller.repeatMode,
                    repeatModeString = MediaTransportState.getRepeatModeString(controller.repeatMode),
                    shuffleModeEnabled = controller.shuffleModeEnabled
                )
            )
        }
    }

    private fun updateContentState() {
        mediaController?.let { controller ->
            val playlist = mutableListOf<MediaItem>()
            for (i in 0 until controller.mediaItemCount) {
                controller.getMediaItemAt(i)?.let { playlist.add(it) }
            }
            
            stateRepository.updateContentState(
                MediaContentState(
                    currentMediaItem = controller.currentMediaItem,
                    currentMetadata = controller.mediaMetadata,
                    currentIndex = controller.currentMediaItemIndex,
                    mediaItemCount = controller.mediaItemCount,
                    playlist = playlist,
                    hasNextMediaItem = controller.hasNextMediaItem(),
                    hasPreviousMediaItem = controller.hasPreviousMediaItem(),
                    nextMediaItemIndex = if (controller.hasNextMediaItem()) controller.nextMediaItemIndex else -1,
                    previousMediaItemIndex = if (controller.hasPreviousMediaItem()) controller.previousMediaItemIndex else -1
                )
            )
        }
    }

    override fun initialize() {
        TODO("Not yet implemented")
    }

    @OptIn(UnstableApi::class)
    fun enqueueUrl(url: String) {
        callback.onLoadRequest()
        CoroutineScope(Dispatchers.Main).launch {
            val mediaItem = createMediaItemFromUrl(url)
            mediaItem?.let { item ->
                mediaController?.apply {
                    addMediaItem(item)
                    callback.onMediaLoaded(item)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun loadUrl(url: String) {
        callback.onLoadRequest()
        CoroutineScope(Dispatchers.Main).launch {
            val mediaItem = createMediaItemFromUrl(url)
            mediaItem?.let { item ->
                mediaController?.apply {
                    clearMediaItems()
                    setMediaItem(item)
                    playWhenReady = true
                    prepare()
                    callback.onMediaLoaded(item)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun createMediaItemFromUrl(url: String): MediaItem? {
        val streamInfo = StreamTypeDetector.detectStreamInfo(url, context)
        val streamType = streamInfo.streamType
        Log.d(LOG_TAG, "Detected stream type: $streamType for URL: $url")

        val extras = Bundle().apply {
            putString("content_type", streamType.name)
        }

        return when (streamType) {
            StreamType.VIDEO -> {
                val title = streamInfo.title ?: ""
                val thumbnail = streamInfo.thumbnail ?: ""
                val uploader = streamInfo.uploader ?: ""

                MediaItem.Builder()
                    .setUri(streamInfo.resolvedUrl)
                    .setMediaId(streamInfo.resolvedUrl!!)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(uploader)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE)
                            .setArtworkUri(thumbnail.toUri())
                            .setExtras(extras)
                            .build()
                    )
                    .setTag(StreamType.VIDEO)
                    .build()
            }

            StreamType.RTSP -> {
                val mediaItemRTSP = MediaItem.fromUri(streamInfo.resolvedUrl!!)
                mediaItemRTSP.buildUpon()
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setExtras(extras)
                            .setTitle(streamInfo.title ?: "")
                            .build()
                    )
                    .build()
            }

            StreamType.MJPEG, StreamType.UNKNOWN, StreamType.WEBPAGE -> {
                MediaItem.Builder()
                    .setUri(streamInfo.resolvedUrl)
                    .setMediaId(streamInfo.resolvedUrl!!)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setExtras(extras)
                            .setTitle(streamInfo.title ?: "")
                            .build()
                    )
                    .setTag(streamType)
                    .build()
            }
        }
    }

    override fun release() {
        stopPositionTimer()
        stateRepository.updateConnectionState(
            MediaControllerConnectionState(isConnected = false, isInitialized = false)
        )
        mediaController?.release()
        mediaController = null
    }
}
