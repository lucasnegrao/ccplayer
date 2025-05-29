package com.antiglitch.yetanothernotifier.repository

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlin.reflect.KProperty

data class MediaTransportState(
    val playbackState: Int = Player.STATE_IDLE,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false, // Add isLoading property
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleModeEnabled: Boolean = false,
    // Update playbackStateString to use the instance's playbackState, isPlaying, and isLoading
    val playbackStateString: String = getPlaybackStateString(playbackState, isPlaying, isLoading),
    val repeatModeString: String = getRepeatModeString(Player.REPEAT_MODE_OFF)
) {
    companion object {
        // Modify getPlaybackStateString to accept isLoading parameter
        fun getPlaybackStateString(state: Int, isPlaying: Boolean, isLoading: Boolean): String = when {
            isLoading -> "LOADING" // Check isLoading first
            state == Player.STATE_IDLE -> "IDLE"
            state == Player.STATE_BUFFERING -> "BUFFERING"
            state == Player.STATE_READY -> if (isPlaying) "PLAYING" else "PAUSED"
            state == Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN"
        }
        
        fun getRepeatModeString(mode: Int): String = when (mode) {
            Player.REPEAT_MODE_OFF -> "OFF"
            Player.REPEAT_MODE_ONE -> "ONE"
            Player.REPEAT_MODE_ALL -> "ALL"
            else -> "UNKNOWN"
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): MediaTransportState {
        return this
    }
}

data class MediaContentState(
    val currentMediaItem: MediaItem? = null,
    val currentMetadata: MediaMetadata? = null,
    val currentIndex: Int = -1,
    val mediaItemCount: Int = 0,
    val playlist: List<MediaItem> = emptyList(),
    val hasNextMediaItem: Boolean = false,
    val hasPreviousMediaItem: Boolean = false,
    val nextMediaItemIndex: Int = -1,
    val previousMediaItemIndex: Int = -1
)

data class MediaErrorState(
    val error: PlaybackException? = null,
    val errorMessage: String? = null,
    val errorCode: Int? = null,
    val errorCodeString: String? = null
) {
    companion object {
        fun getErrorCodeString(errorCode: Int?): String? = when (errorCode) {
            PlaybackException.ERROR_CODE_UNSPECIFIED -> "UNSPECIFIED"
            PlaybackException.ERROR_CODE_REMOTE_ERROR -> "REMOTE_ERROR"
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "BEHIND_LIVE_WINDOW"
            PlaybackException.ERROR_CODE_TIMEOUT -> "TIMEOUT"
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "FAILED_RUNTIME_CHECK"
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO_UNSPECIFIED"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "IO_INVALID_HTTP_CONTENT_TYPE"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "IO_NO_PERMISSION"
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "IO_READ_POSITION_OUT_OF_RANGE"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "PARSING_CONTAINER_MALFORMED"
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "PARSING_MANIFEST_MALFORMED"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "PARSING_CONTAINER_UNSUPPORTED"
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "PARSING_MANIFEST_UNSUPPORTED"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "DECODER_QUERY_FAILED"
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "DECODING_FORMAT_EXCEEDS_CAPABILITIES"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "DECODING_FORMAT_UNSUPPORTED"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_TRACK_INIT_FAILED"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "AUDIO_TRACK_WRITE_FAILED"
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM_UNSPECIFIED"
            else -> errorCode?.toString()
        }
    }
}

data class MediaControllerConnectionState(
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false
)
