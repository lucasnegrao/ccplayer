package com.antiglitch.yetanothernotifier.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.media3.session.MediaController as Media3MediaController
import com.antiglitch.yetanothernotifier.service.StreamType

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

class MediaController(
    private var context: Context, 
    private var callback: MediaControllerCallback
) : MediaControllerInterface {

    internal var mediaController: Media3MediaController? = null

    companion object {
        var sessionToken: SessionToken? = null
        const val LOG_TAG: String = "MediaController"
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
                    callback.onInitialized(true)
                } ?: run {
                    Log.e(LOG_TAG, "MediaController future completed but controller is null.")
                    callback.onInitialized(false)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error initializing MediaController", e)
                callback.onInitialized(false)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun initialize() {
        TODO("Not yet implemented")
    }

    @OptIn(UnstableApi::class)
    override fun loadUrl(url: String) {
        var mediaItem: MediaItem? = null
        callback.onLoadRequest()
        CoroutineScope(Dispatchers.Main).launch {
            val streamInfo = StreamTypeDetector.detectStreamInfo(url, context)
            val streamType = streamInfo.streamType
            Log.d(LOG_TAG, "Detected stream type: $streamType for URL: $url")

            val extras = Bundle().apply {
                putString("content_type", streamType.name)
            }

            when (streamType) {
                StreamType.VIDEO -> {
                    val title = streamInfo.title ?: ""
                    val thumbnail = streamInfo.thumbnail ?: ""
                    val uploader = streamInfo.uploader ?: ""

                    mediaItem = MediaItem.Builder()
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
                    mediaItem = mediaItemRTSP.buildUpon()
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setExtras(extras)
                                .setTitle(streamInfo.title ?: "")
                                .build()
                        )
                        .build()
                }
                StreamType.MJPEG, StreamType.UNKNOWN, StreamType.WEBPAGE -> {
                    mediaItem = MediaItem.Builder()
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

    override fun release() {
        mediaController?.release()
        mediaController = null
    }
}
