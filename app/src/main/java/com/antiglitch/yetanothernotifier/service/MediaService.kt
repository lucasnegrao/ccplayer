package com.antiglitch.yetanothernotifier.service

import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken

class MediaService : MediaSessionService() {

    companion object {
        var sessionCompatToken: MediaSessionCompat.Token? = null
        var mediaSession: MediaSession? = null // Main Media3 session
        var compatSession: MediaSessionCompat? = null // A separate MediaSessionCompat
        var player: ExoPlayer? = null
        var media3SessionToken: SessionToken? = null
        var customLegacyToMedia3Connector: CustomMediaSessionConnector? = null
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .build()
            
        mediaSession = MediaSession.Builder(this, player!!)
            .setId("media_service_session")
            .build()
            
        compatSession = MediaSessionCompat(this, "MediaServiceCompatSession")
        compatSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS and MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS.inv())
        compatSession?.isActive = false
        sessionCompatToken = compatSession?.sessionToken
        customLegacyToMedia3Connector = CustomMediaSessionConnector(compatSession, mediaSession)

        Log.d("MediaService", "Service created. CompatSession Active: ${compatSession?.isActive}, Token: $sessionCompatToken")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        compatSession?.release()
        
        mediaSession = null
        player = null
        compatSession = null
        sessionCompatToken = null
        media3SessionToken = null
        customLegacyToMedia3Connector = null
        
        super.onDestroy()
    }
}
