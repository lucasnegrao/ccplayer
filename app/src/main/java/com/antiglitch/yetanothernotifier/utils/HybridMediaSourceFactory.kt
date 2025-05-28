package com.antiglitch.yetanothernotifier.utils

import android.content.Context
import android.os.Bundle

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

@UnstableApi
class HybridMediaSourceFactory(
    context: Context
) : MediaSource.Factory {

    private val defaultMediaSourceFactory = DefaultMediaSourceFactory(context)
    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray {
        defaultMediaSourceFactory.supportedTypes
        TODO("Not yet implemented")
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val extras: Bundle? = mediaItem.mediaMetadata.extras // Access extras here!
        val contentType = extras?.getString("content_type")

        if (contentType !== StreamType.VIDEO.name && contentType !== StreamType.RTSP.name) {
            // For HTML media items, return a SilenceMediaSource.
            // This ensures ExoPlayer won't try to play the HTML as audio/video.
            val livePlaceholderMediaItem = mediaItem.buildUpon()
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(0) // Important for some live UI
                        .build()
                )
                // Ensure mediaId and metadata are preserved (buildUpon does this, but good to be aware)
                .build()
            return PlaceholderLiveMediaSource(mediaItem)

        } else {
            // For all other media items (e.g., video, audio), delegate to the standard factory.
            return defaultMediaSourceFactory.createMediaSource(mediaItem)
        }
    }



}

