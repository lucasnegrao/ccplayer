package com.antiglitch.yetanothernotifier.data.datastore

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Generic filesystem-based datastore with TTL (Time To Live) support.
 * Can store any serializable data with automatic expiration.
 */
class FilesystemDatastore private constructor(
    private val context: Context,
    private val datastoreName: String
) {
    companion object {
        private const val TAG = "FilesystemDatastore"
        private const val METADATA_SUFFIX = ".meta"
        private val instances = mutableMapOf<String, FilesystemDatastore>()

        fun getInstance(context: Context, datastoreName: String): FilesystemDatastore {
            return instances.getOrPut(datastoreName) {
                FilesystemDatastore(context.applicationContext, datastoreName)
            }
        }
    }

    private val datastoreDir = File(context.filesDir, "datastore/$datastoreName").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class CacheMetadata(
        val createdAt: Long,
        val ttlMs: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > (createdAt + ttlMs)
    }

    /**
     * Store data with a specified TTL
     */
    suspend fun <T> put(
        key: String,
        data: T,
        serializer: KSerializer<T>,
        ttlMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dataFile = File(datastoreDir, key)
            val metaFile = File(datastoreDir, "$key$METADATA_SUFFIX")

            // Write data
            val jsonData = json.encodeToString(serializer, data)
            dataFile.writeText(jsonData)

            // Write metadata
            val metadata = CacheMetadata(
                createdAt = System.currentTimeMillis(),
                ttlMs = ttlMs
            )
            val metaJson = json.encodeToString(metadata)
            metaFile.writeText(metaJson)

            Log.d(TAG, "Stored data for key: $key with TTL: ${ttlMs}ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store data for key: $key", e)
            false
        }
    }

    /**
     * Retrieve data, returns null if expired or not found
     */
    suspend fun <T> get(
        key: String,
        serializer: KSerializer<T>
    ): T? = withContext(Dispatchers.IO) {
        try {
            val dataFile = File(datastoreDir, key)
            val metaFile = File(datastoreDir, "$key$METADATA_SUFFIX")

            if (!dataFile.exists() || !metaFile.exists()) {
                Log.d(TAG, "Data or metadata not found for key: $key")
                return@withContext null
            }

            // Check if expired
            val metaJson = metaFile.readText()
            val metadata = json.decodeFromString<CacheMetadata>(metaJson)

            if (metadata.isExpired()) {
                Log.d(TAG, "Data expired for key: $key, removing")
                remove(key)
                return@withContext null
            }

            // Read and return data
            val jsonData = dataFile.readText()
            val data = json.decodeFromString(serializer, jsonData)
            Log.d(TAG, "Retrieved data for key: $key")
            data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve data for key: $key", e)
            null
        }
    }

    /**
     * Remove data and metadata for a key
     */
    suspend fun remove(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dataFile = File(datastoreDir, key)
            val metaFile = File(datastoreDir, "$key$METADATA_SUFFIX")

            val dataDeleted = if (dataFile.exists()) dataFile.delete() else true
            val metaDeleted = if (metaFile.exists()) metaFile.delete() else true

            Log.d(TAG, "Removed data for key: $key")
            dataDeleted && metaDeleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove data for key: $key", e)
            false
        }
    }

    /**
     * Check if data exists and is not expired
     */
    suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dataFile = File(datastoreDir, key)
            val metaFile = File(datastoreDir, "$key$METADATA_SUFFIX")

            if (!dataFile.exists() || !metaFile.exists()) {
                return@withContext false
            }

            val metaJson = metaFile.readText()
            val metadata = json.decodeFromString<CacheMetadata>(metaJson)

            if (metadata.isExpired()) {
                remove(key)
                return@withContext false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check existence for key: $key", e)
            false
        }
    }

    /**
     * Clean up expired entries
     */
    suspend fun cleanupExpired(): Int = withContext(Dispatchers.IO) {
        try {
            val files = datastoreDir.listFiles() ?: return@withContext 0
            var cleanedCount = 0

            files.filter { it.name.endsWith(METADATA_SUFFIX) }
                .forEach { metaFile ->
                    try {
                        val key = metaFile.name.removeSuffix(METADATA_SUFFIX)
                        val metaJson = metaFile.readText()
                        val metadata = json.decodeFromString<CacheMetadata>(metaJson)

                        if (metadata.isExpired()) {
                            remove(key)
                            cleanedCount++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to process metadata file: ${metaFile.name}", e)
                    }
                }

            Log.d(TAG, "Cleaned up $cleanedCount expired entries")
            cleanedCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup expired entries", e)
            0
        }
    }

    /**
     * Clear all data in this datastore
     */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        try {
            datastoreDir.deleteRecursively()
            datastoreDir.mkdirs()
            Log.d(TAG, "Cleared all data in datastore: $datastoreName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear datastore: $datastoreName", e)
            false
        }
    }
}