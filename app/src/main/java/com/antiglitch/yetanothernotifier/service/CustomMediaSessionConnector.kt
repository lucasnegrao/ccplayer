package com.antiglitch.yetanothernotifier.service

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

class CustomMediaSessionConnector(
    private val legacyMediaSession: MediaSessionCompat?,
    private val media3Session: MediaSession?
) {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var playerListener: Player.Listener? = null

    init {
        setupSessionListeners()
    }

    private fun setupSessionListeners() {
        // Register player listener to update legacy session
        playerListener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateLegacyMediaSessionMetadata()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateLegacyMediaSessionPlaybackState()
                updateLegacyMediaSessionMetadata()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateLegacyMediaSessionPlaybackState()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateLegacyMediaSessionPlaybackState()
            }
        }

        playerListener?.let { media3Session?.player?.addListener(it) }
    }

    private fun updateLegacyMediaSessionPlaybackState() {
        val player: Player? = media3Session?.player
        if (player == null) return

        val stateBuilder: PlaybackStateCompat.Builder = PlaybackStateCompat.Builder()

        // Map Media3 player state to legacy PlaybackState
        val state: Int = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            Player.STATE_READY ->
                if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

            Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            Player.STATE_IDLE -> PlaybackStateCompat.STATE_NONE
            else -> PlaybackStateCompat.STATE_NONE
        }

        stateBuilder.setState(
            state,
            player.currentPosition,
            player.playbackParameters.speed
        )

        // Add supported actions
        var actions: Long = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO

        if (player.hasNextMediaItem()) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }

        if (player.hasPreviousMediaItem()) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        stateBuilder.setActions(actions)

        legacyMediaSession?.setPlaybackState(stateBuilder.build())
    }

    @OptIn(UnstableApi::class)
    private fun updateLegacyMediaSessionMetadata() {
        val player: Player? = media3Session?.player
        if (player == null) return

        val media3MediaItem = player.currentMediaItem
        val media3Metadata: MediaMetadata = player.mediaMetadata

        val metadataBuilder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()

        // Map Media3 metadata to legacy MediaMetadataCompat
        media3Metadata.title?.let {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.toString())
        }

        media3Metadata.artist?.let {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.toString())
        }

        media3Metadata.albumTitle?.let {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it.toString())
        }

        // Set media ID
        media3MediaItem?.mediaId?.let {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, it)
        }

        // Set duration
        if (player.duration != C.TIME_UNSET) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.duration)
        }

        // Handle artwork if available
        media3Metadata.artworkData?.let { artworkData ->
            val artwork = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
        }

        legacyMediaSession?.setMetadata(metadataBuilder.build())
    }

    fun release() {
        playerListener?.let { media3Session?.player?.removeListener(it) }
        playerListener = null
    }
}
