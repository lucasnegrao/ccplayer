package com.antiglitch.yetanothernotifier.services

import android.content.Context
import android.util.Log
import com.antiglitch.yetanothernotifier.data.datastore.FilesystemDatastore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Service for handling all yt-dlp operations on background threads with caching
 */
class YtDlpService private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "YtDlpService"
        private const val EXTRACTORS_CACHE_KEY = "yt_dlp_extractors"
        private const val EXTRACTORS_CACHE_TTL = 24 * 60 * 60 * 1000L // 24 hours
        private const val VIDEO_INFO_CACHE_TTL = 60 * 60 * 1000L // 1 hour
        
        @Volatile
        private var instance: YtDlpService? = null
        
        fun getInstance(context: Context): YtDlpService {
            return instance ?: synchronized(this) {
                instance ?: YtDlpService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val datastore = FilesystemDatastore.getInstance(context, "yt_dlp_cache")
    
    // Initialization state
    @Volatile
    internal var isInitialized = false
    @Volatile
    private var initializationFailed = false
    private val initializationCallbacks = mutableListOf<(Boolean) -> Unit>()

    @Serializable
    data class CachedVideoInfo(
        val url: String,
        val title: String?,
        val thumbnail: String?,
        val uploader: String?,
        val duration: Long?
    )


    /**
     * Initialize yt-dlp and cache extractors (called by ServiceOrchestrator)
     */
    suspend fun initialize() {
        serviceScope.launch {
            initializeYtDlp()
        }
    }

    /**
     * Initialize yt-dlp and cache extractors
     */
    private suspend fun initializeYtDlp() {
        if (isInitialized || initializationFailed) return

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing YoutubeDL...")
                YoutubeDL.getInstance().init(context)
                  isInitialized = true

                getExtractors() // Pre-fetch extractors to cache them

                
                Log.d(TAG, "YoutubeDL initialized successfully")
                // Notify callbacks
                synchronized(initializationCallbacks) {
                    initializationCallbacks.forEach { it(true) }
                    initializationCallbacks.clear()
                }
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Failed to initialize YoutubeDL", e)
                initializationFailed = true
                
                // Notify callbacks
                synchronized(initializationCallbacks) {
                    initializationCallbacks.forEach { it(false) }
                    initializationCallbacks.clear()
                }
            }
        }
    }

    /**
     * Add callback for initialization completion
     */
    fun onInitialized(callback: (Boolean) -> Unit) {
        if (isInitialized) {
            callback(true)
        } else if (initializationFailed) {
            callback(false)
        } else {
            synchronized(initializationCallbacks) {
                initializationCallbacks.add(callback)
            }
        }
    }

    /**
     * Get extractors (cached or fetch new ones)
     */
    suspend fun getExtractors(): Set<String> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "YoutubeDL not initialized, returning empty extractors")
            return@withContext emptySet()
        }

        // Try to get from cache first
        datastore.get(EXTRACTORS_CACHE_KEY, SetSerializer(String.serializer()))?.let { cached ->
            Log.d(TAG, "Using cached extractors (${cached.size} extractors)")
            return@withContext cached
        }

        // Fetch and cache new extractors
        cacheExtractors()
    }

    /**
     * Cache extractors in background
     */
    private suspend fun cacheExtractors(): Set<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching yt-dlp extractors...")
            val request = YoutubeDLRequest(emptyList())
            request.addOption("--list-extractors")
            val response = YoutubeDL.getInstance().execute(request)

            val extractors = response.out.split("\n")
                .filter { it.isNotBlank() && !it.startsWith(" ") }
                .map { it.trim().lowercase() }
                .toSet()

            // Cache the extractors
            datastore.put(
                EXTRACTORS_CACHE_KEY,
                extractors,
                SetSerializer(String.serializer()),
                EXTRACTORS_CACHE_TTL
            )

            Log.d(TAG, "Cached ${extractors.size} yt-dlp extractors")
            extractors
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch yt-dlp extractors", e)
            emptySet()
        }
    }

    /**
     * Check if URL is supported by yt-dlp extractors
     */
    suspend fun isUrlSupported(url: String): Boolean = withContext(Dispatchers.IO) {
        val extractors = getExtractors()
        val host = try {
            java.net.URL(url).host?.lowercase()
        } catch (e: Exception) {
            Log.w(TAG, "Invalid URL: $url", e)
            return@withContext false
        } ?: return@withContext false

        // Extract the main domain
        val mainDomain = host.split('.').takeLast(2).joinToString(".")

        // Match against extractors
        extractors.any { extractor ->
            val extractorParts = extractor.lowercase().split(":")
            val extractorDomain = extractorParts[0]
            mainDomain.contains(extractorDomain) || extractorDomain.contains(mainDomain)
        }
    }

    /**
     * Extract video info using yt-dlp (with caching)
     */
    suspend fun extractVideoInfo(url: String): CachedVideoInfo? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "YoutubeDL not initialized")
            return@withContext null
        }

        val cacheKey = "video_info_${url.hashCode()}"
        
        // Try to get from cache first
        datastore.get(cacheKey, CachedVideoInfo.serializer())?.let { cached ->
            Log.d(TAG, "Using cached video info for: $url")
            return@withContext cached
        }

        // Extract new info
        try {
            Log.d(TAG, "Extracting video info for: $url")
            val request = YoutubeDLRequest(url)
            request.addOption("-f", "best")
            request.addOption("--no-check-certificate")
            request.addOption("--socket-timeout", "8")
            
            val videoInfo = YoutubeDL.getInstance().getInfo(request)
            
            val cachedInfo = CachedVideoInfo(
                url = videoInfo.url ?: url,
                title = videoInfo.title,
                thumbnail = videoInfo.thumbnail,
                uploader = videoInfo.uploader,
                duration = videoInfo.duration.toLong()
            )
            
            // Cache the result
            datastore.put(
                cacheKey,
                cachedInfo,
                CachedVideoInfo.serializer(),
                VIDEO_INFO_CACHE_TTL
            )
            
            Log.d(TAG, "Successfully extracted and cached video info for: $url")
            cachedInfo
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video info for: $url", e)
            null
        }
    }

    /**
     * Get direct stream URL from yt-dlp
     */
    suspend fun getStreamUrl(url: String): String? = withContext(Dispatchers.IO) {
        extractVideoInfo(url)?.url
    }

    /**
     * Clear all cached data
     */
    suspend fun clearCache() {
        datastore.clear()
        Log.d(TAG, "Cleared all yt-dlp cache")
    }

    /**
     * Cleanup expired cache entries
     */
    suspend fun cleanupCache(): Int {
        return datastore.cleanupExpired()
    }

    /**
     * Check initialization status
     */
    fun isInitialized(): Boolean = isInitialized
    
    fun initializationFailed(): Boolean = initializationFailed
}
