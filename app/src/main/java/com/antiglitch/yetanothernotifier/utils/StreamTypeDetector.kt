package com.antiglitch.yetanothernotifier.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

enum class StreamType {
    VIDEO,
    MJPEG,
    WEBPAGE,
    UNKNOWN,
    RTSP,
}

object StreamTypeDetector {
    private const val TAG = "StreamTypeDetector"

    // YoutubeDL initialization state
    private var isYoutubeDLInitialized = false
    private var youtubeDLInitializationFailed = false

    // Cache for yt-dlp extractors
    private var cachedExtractors: Set<String>? = null
    private var extractorsCacheTime: Long = 0
    private const val EXTRACTORS_CACHE_DURATION = 24 * 60 * 60 * 1000L // 24 hours

    // Common video file extensions
    private val videoExtensions = setOf(
        ".mp4",
        ".webm",
        ".mkv",
        ".mov",
        ".avi",
        ".flv",
        ".wmv",
        ".m4v",
        ".3gp",
        ".ts",
        ".m3u8",
        ".mpd"
    )

    data class StreamInfo(
        val streamType: StreamType,
        val resolvedUrl: String? = null,
        val title: String? = null,
        val thumbnail: String? = null,
        val uploader: String? = null
    )

    // Initialize YoutubeDL if not already done
    suspend fun initializeYoutubeDL(context: Context) {
        if (isYoutubeDLInitialized || youtubeDLInitializationFailed) return

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing YoutubeDL...")
                YoutubeDL.getInstance().init(context)
                isYoutubeDLInitialized = true
                Log.d(TAG, "YoutubeDL initialized successfully")

                // Pre-cache extractors after initialization
                cacheYtDlpExtractors()
            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Failed to initialize YoutubeDL", e)
                youtubeDLInitializationFailed = true
            }
        }
    }

    private suspend fun cacheYtDlpExtractors() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Pre-caching yt-dlp extractors in background...")
                getYtDlpExtractors()
                Log.d(TAG, "yt-dlp extractors cached successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pre-cache yt-dlp extractors: ${e.message}")
            }
        }
    }

    // Get cached extractors or fetch them
    suspend fun getYtDlpExtractors(): Set<String> {
        return withContext(Dispatchers.IO) {
            if (!isYoutubeDLInitialized) {
                Log.w(TAG, "YoutubeDL not initialized, returning empty extractors")
                return@withContext emptySet()
            }

            val currentTime = System.currentTimeMillis()

            // Return cached extractors if still valid
            cachedExtractors?.let { extractors ->
                if (currentTime - extractorsCacheTime < EXTRACTORS_CACHE_DURATION) {
                    Log.d(TAG, "Using cached extractors (${extractors.size} extractors)")
                    return@withContext extractors
                }
            }

            // Fetch new extractors
            try {
                Log.d(TAG, "Fetching yt-dlp extractors...")
                val request = YoutubeDLRequest(emptyList())
                request.addOption("--list-extractors")
                val response = YoutubeDL.getInstance().execute(request)

                val extractors = response.out.split("\n")
                    .filter { it.isNotBlank() && !it.startsWith(" ") }
                    .map { it.trim().lowercase() }
                    .toSet()

                cachedExtractors = extractors
                extractorsCacheTime = currentTime
                Log.d(TAG, "Cached ${extractors.size} yt-dlp extractors")

                return@withContext extractors
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch yt-dlp extractors", e)
                return@withContext cachedExtractors ?: emptySet()
            }
        }
    }

    // Check if URL matches any yt-dlp extractor
    suspend fun isYtDlpSupported(url: String): Boolean {
        val extractors = getYtDlpExtractors()
        val host = url.toUri().host?.lowercase() ?: return false

        // Extract the main domain (e.g., "example.com" from "www.example.com")
        val mainDomain = host.split('.')[2].toString()

//            .takeLast(2).joinToString(".")

        // Match against extractors
        return extractors.any { extractor ->
            val extractorParts = extractor.lowercase().split(":")
            val extractorDomain = extractorParts[0] // Extract the main part of the extractor
            mainDomain.contains(extractorDomain) || extractorDomain.contains(mainDomain)
        }
    }

    // Extract title from webpage HTML
    private suspend fun extractWebpageMetadata(url: String): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36"
                )

                val html = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                // Extract title
                val titlePattern =
                    Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE)
                val titleMatcher = titlePattern.matcher(html)
                val title = if (titleMatcher.find()) {
                    titleMatcher.group(1)?.trim()?.let {
                        // Decode HTML entities
                        it.replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                    }
                } else null

                // Extract description
                val descPattern = Pattern.compile(
                    "<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE
                )
                val descMatcher = descPattern.matcher(html)
                val description = if (descMatcher.find()) descMatcher.group(1)?.trim() else null

                Log.d(TAG, "Extracted webpage metadata - Title: $title")
                return@withContext Pair(title, description)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to extract webpage metadata: ${e.message}")
                return@withContext Pair(null, null)
            }
        }
    }

    // Unified detection: returns both type and metadata, only calls yt-dlp if needed
    suspend fun detectStreamInfo(url: String, context: Context? = null): StreamInfo {
        // Initialize YoutubeDL if context is provided and not already initialized
        context?.let { initializeYoutubeDL(it) }

        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(url)
                val path = uri.path?.lowercase() ?: ""
                Log.d(TAG, "Detecting stream info for: $url")

                // Step 1: Check for direct video files first (fastest check)
                if (videoExtensions.any { path.endsWith(it) }) {
                    Log.d(TAG, "Detected as VIDEO (direct video file extension)")
                    return@withContext StreamInfo(
                        streamType = StreamType.VIDEO,
                        resolvedUrl = url
                    )
                }

                // Step 1.5: Check for RTSP scheme
                if (uri.scheme.equals("rtsp", ignoreCase = true)) {
                    Log.d(TAG, "Detected as VIDEO (RTSP stream)")
                    return@withContext StreamInfo(
                        streamType = StreamType.RTSP,
                        resolvedUrl = url
                    )
                }

                // Step 2: Check if URL is supported by yt-dlp (before HEAD request)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val isYtDlpSupported = isYtDlpSupported(url)

                    if (isYtDlpSupported) {
                        Log.d(TAG, "URL matches yt-dlp extractor, trying yt-dlp resolution")
                        try {
                            val request = YoutubeDLRequest(url)
                            request.addOption("-f", "best")
                            request.addOption("--no-check-certificate")
                            request.addOption("--socket-timeout", "8")
                            val streamInfo = YoutubeDL.getInstance().getInfo(request)
                            val resolvedUrl = streamInfo.url
                            if (resolvedUrl != null && resolvedUrl.isNotEmpty()) {
                                Log.d(TAG, "yt-dlp successfully resolved - detected as VIDEO")
                                return@withContext StreamInfo(
                                    streamType = StreamType.VIDEO,
                                    resolvedUrl = resolvedUrl,
                                    title = streamInfo.title,
                                    thumbnail = streamInfo.thumbnail,
                                    uploader = streamInfo.uploader
                                )
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "yt-dlp failed for supported site: ${e.message}")
                        }
                    }

                    // Step 3: Quick HEAD request for content type detection
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.requestMethod = "HEAD"
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        connection.connect()

                        val contentType = connection.contentType?.lowercase() ?: ""
                        connection.disconnect()

                        Log.d(TAG, "HEAD request Content-Type: $contentType")

                        when {
                            contentType.contains("video/") -> {
                                Log.d(TAG, "Detected as VIDEO (Content-Type: video/*)")
                                return@withContext StreamInfo(
                                    streamType = StreamType.VIDEO,
                                    resolvedUrl = url
                                )
                            }

                            contentType.contains("application/vnd.apple.mpegurl") ||
                                    contentType.contains("application/x-mpegurl") -> {
                                Log.d(TAG, "Detected as VIDEO (HLS stream)")
                                return@withContext StreamInfo(
                                    streamType = StreamType.VIDEO,
                                    resolvedUrl = url
                                )
                            }

                            contentType.contains("application/dash+xml") -> {
                                Log.d(TAG, "Detected as VIDEO (DASH stream)")
                                return@withContext StreamInfo(
                                    streamType = StreamType.VIDEO,
                                    resolvedUrl = url
                                )
                            }

                            contentType.contains("multipart/x-mixed-replace") -> {
                                Log.d(
                                    TAG,
                                    "Detected as MJPEG (Content-Type: multipart/x-mixed-replace)"
                                )
                                return@withContext StreamInfo(
                                    streamType = StreamType.MJPEG,
                                    resolvedUrl = url
                                )
                            }

                            contentType.contains("image/jpeg") && url.contains(
                                "mjpeg",
                                ignoreCase = true
                            ) -> {
                                Log.d(
                                    TAG,
                                    "Detected as MJPEG (Content-Type: image/jpeg + mjpeg in URL)"
                                )
                                return@withContext StreamInfo(
                                    streamType = StreamType.MJPEG,
                                    resolvedUrl = url
                                )
                            }

                            (contentType.contains("text/plain") || contentType.contains("application/octet-stream")) &&
                                    url.contains("camera_proxy_stream", ignoreCase = true) -> {
                                Log.d(TAG, "Detected as MJPEG (Home Assistant camera proxy stream)")
                                return@withContext StreamInfo(
                                    streamType = StreamType.MJPEG,
                                    resolvedUrl = url
                                )
                            }

                            contentType.contains("text/html") -> {
                                Log.d(TAG, "HTML detected, extracting webpage metadata")
                                // Extract webpage metadata
                                val (title, _) = extractWebpageMetadata(url)
                                return@withContext StreamInfo(
                                    streamType = StreamType.WEBPAGE,
                                    resolvedUrl = url,
                                    title = title
                                )
                            }

                            else -> {
                                Log.d(TAG, "Unknown content type: $contentType")
                                // For ambiguous content types, try MJPEG heuristics
                                if (url.contains("camera", ignoreCase = true) ||
                                    url.contains("mjpeg", ignoreCase = true) ||
                                    url.contains("stream", ignoreCase = true)
                                ) {
                                    Log.d(TAG, "Guessing MJPEG based on URL heuristics")
                                    return@withContext StreamInfo(
                                        streamType = StreamType.MJPEG,
                                        resolvedUrl = url
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "HEAD request failed: ${e.message}")
                    }

                    // Step 4: If not yt-dlp supported and no clear content type, try extracting webpage metadata
                    if (!isYtDlpSupported) {
                        Log.d(TAG, "Not yt-dlp supported, trying webpage metadata extraction")
                        val (title, _) = extractWebpageMetadata(url)
                        return@withContext StreamInfo(
                            streamType = StreamType.WEBPAGE,
                            resolvedUrl = url,
                            title = title
                        )
                    }

                    // Step 5: Fallback to WEBPAGE for HTTP URLs
                    Log.d(TAG, "Defaulting to WEBPAGE")
                    return@withContext StreamInfo(
                        streamType = StreamType.WEBPAGE,
                        resolvedUrl = url
                    )
                } else {
                    // Step 6: For non-HTTP URLs, return UNKNOWN
                    Log.d(TAG, "Non-HTTP URL, detected as UNKNOWN")
                    return@withContext StreamInfo(
                        streamType = StreamType.UNKNOWN,
                        resolvedUrl = url
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting stream info for $url", e)
                return@withContext StreamInfo(
                    streamType = StreamType.UNKNOWN,
                    resolvedUrl = url
                )
            }
        }
    }

    // Lightweight wrapper: returns only the type, uses unified detection
    suspend fun detectStreamType(url: String): StreamType {
        return detectStreamInfo(url).streamType
    }
}