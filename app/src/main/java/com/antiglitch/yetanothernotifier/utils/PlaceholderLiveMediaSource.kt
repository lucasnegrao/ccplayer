package com.antiglitch.yetanothernotifier.utils

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.common.Timeline
import androidx.media3.common.util.NullableType
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.BaseMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.upstream.Allocator



@UnstableApi
class PlaceholderLiveMediaSource(private val mediaItem: MediaItem) : BaseMediaSource() {

    private val timeline = createPlaceholderTimeline()

    override fun prepareSourceInternal(transferListener: TransferListener?) {
        // Immediately refresh the source info with a timeline that describes the live item
        refreshSourceInfo(timeline)
    }

    override fun maybeThrowSourceInfoRefreshError() {
        // No network operations, so nothing to throw here
    }

    override fun createPeriod(id: MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        // Return a MediaPeriod that provides no playable content (placeholder behavior)
        return PlaceholderMediaPeriod()
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        // Cleanup if needed
    }

    override fun releaseSourceInternal() {
        // Cleanup if needed
    }

    override fun getMediaItem(): MediaItem {
        return mediaItem
    }

    private fun createPlaceholderTimeline(): Timeline {
        return object : Timeline() {
            override fun getWindowCount(): Int = 1
            override fun getPeriodCount(): Int = 1

            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                return window.set(
                    /* uid = */ mediaItem.mediaId,
                    /* mediaItem = */ mediaItem,
                    /* manifest = */ null,
                    /* presentationStartTimeMs = */ C.TIME_UNSET,
                    /* windowStartTimeMs = */0,
                    /* elapsedRealtimeEpochOffsetMs = */ System.currentTimeMillis()  ,
                    /* isSeekable = */ false, // Live streams are typically not seekable
                    /* isDynamic = */ true,  // Key for live streams
                    /* liveConfiguration = */ mediaItem.liveConfiguration,
                    /* defaultPositionUs = */ 0L,
                    /* durationUs = */ C.TIME_UNSET, // Undefined duration for live
                    /* firstPeriodIndex = */ 0,
                    /* lastPeriodIndex = */ 0,
                    /* positionInFirstPeriodUs = */ 0L
                )
            }

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                val uid = mediaItem.mediaId
                return period.set(
                    /* id = */ if (setIds) uid else null,
                    /* uid = */ uid,
                    /* windowIndex = */ 0,
                    /* durationUs = */ C.TIME_UNSET, // Indefinite duration for live
                    /* positionInWindowUs = */ 0
                )
            }

            override fun getIndexOfPeriod(uid: Any): Int {
                val expectedUid = mediaItem.mediaId
                return if (uid == expectedUid) 0 else C.INDEX_UNSET
            }

            override fun getUidOfPeriod(periodIndex: Int): Any {
                return mediaItem.mediaId
            }

            override fun hashCode(): Int {
                return mediaItem.mediaId?.hashCode() ?: mediaItem.hashCode()
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Timeline) return false

                if (windowCount != other.windowCount || periodCount != other.periodCount) return false

                val thisWindow = Window()
                val otherWindow = Window()
                getWindow(0, thisWindow)
                other.getWindow(0, otherWindow)

                return thisWindow.mediaItem.mediaId == otherWindow.mediaItem.mediaId
            }
        }
    }

    /**
     * A MediaPeriod implementation that provides no tracks and signals immediate end of stream.
     * This is used for placeholder content that shouldn't actually play anything.
     */
    private inner class PlaceholderMediaPeriod : MediaPeriod {

        override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
            // Call back immediately, indicating we are prepared (with no tracks)
            callback.onPrepared(this)
        }

        override fun maybeThrowPrepareError() {
            // No preparation errors for placeholder content
        }

        override fun getTrackGroups(): TrackGroupArray = TrackGroupArray.EMPTY

        override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
            // No buffer to discard
        }

        override fun readDiscontinuity(): Long = C.TIME_UNSET

        override fun seekToUs(positionUs: Long): Long = positionUs

        override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long = positionUs

        override fun getBufferedPositionUs(): Long = C.TIME_END_OF_SOURCE

        override fun getNextLoadPositionUs(): Long = C.TIME_END_OF_SOURCE
        override fun continueLoading(loadingInfo: LoadingInfo): Boolean {
            return false
        }


        override fun isLoading(): Boolean = false

        override fun reevaluateBuffer(positionUs: Long) {
            // No buffer to reevaluate
        }

        override fun getStreamKeys(trackSelections: List<ExoTrackSelection>):List<StreamKey> = emptyList()

        override fun selectTracks(
            selections: Array<out @NullableType ExoTrackSelection?>,
            mayRetainStreamFlags: BooleanArray,
            streams: Array<SampleStream?>,
            streamResetFlags: BooleanArray,
            positionUs: Long
        ): Long {
            // Since we have no tracks (TrackGroupArray.EMPTY), we need to:
            // 1. Clear any existing streams that might be passed in
            // 2. Mark streams as reset where we're clearing them

            for (i in streams.indices) {
                if (streams[i] != null) {
                    // We're removing an existing stream, so mark it as reset
                    streamResetFlags[i] = true
                } else {
                    // No change to this stream position
                    streamResetFlags[i] = false
                }
                // Set all streams to null since we have no tracks to provide
                streams[i] = null
            }

            // Return the same position since we're not actually selecting anything
            return positionUs
        }
    }
}

