package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.util.Log
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handler for system-level commands
 */
class SystemCommandHandler(private val context: Context) : CommandHandler {
    companion object {
        private const val TAG = "SystemCommandHandler"
        
        // Action patterns
        private const val GET_SYSTEM_INFO = "get_system_info"
        private const val CLEAR_CACHE = "clear_cache"
        private const val LOG_EVENT = "log_event"
    }
    
    override val actionPattern = "system_.*"
    
    override fun canHandle(command: Command): Boolean {
        return command.action == GET_SYSTEM_INFO ||
               command.action == CLEAR_CACHE ||
               command.action == LOG_EVENT
    }
    
    override suspend fun handle(command: Command): CommandResult {
        return when (command.action) {
            GET_SYSTEM_INFO -> handleGetSystemInfo()
            CLEAR_CACHE -> handleClearCache()
            LOG_EVENT -> handleLogEvent(command.payload)
            else -> CommandResult.Error("Unknown action: ${command.action}")
        }
    }
    
    private suspend fun handleGetSystemInfo(): CommandResult {
        return try {
            val info = mapOf(
                "appVersion" to context.packageManager.getPackageInfo(context.packageName, 0).versionName,
                "deviceModel" to android.os.Build.MODEL,
                "androidVersion" to android.os.Build.VERSION.RELEASE,
                "freeSpace" to withContext(Dispatchers.IO) {
                    context.cacheDir.freeSpace / (1024 * 1024) // MB
                }
            )
            
            Log.d(TAG, "Retrieved system info: $info")
            CommandResult.Success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system info", e)
            CommandResult.Error("Failed to get system info: ${e.message}", e)
        }
    }
    
    private suspend fun handleClearCache(): CommandResult {
        return try {
            val cacheCleared = withContext(Dispatchers.IO) {
                val cacheDir = context.cacheDir
                if (cacheDir.exists() && cacheDir.isDirectory) {
                    deleteRecursively(cacheDir)
                    true
                } else {
                    false
                }
            }
            
            Log.d(TAG, "Cache cleared: $cacheCleared")
            CommandResult.Success(mapOf("cacheCleared" to cacheCleared))
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
            CommandResult.Error("Failed to clear cache: ${e.message}", e)
        }
    }
    
    private fun deleteRecursively(fileOrDirectory: File): Boolean {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        
        return fileOrDirectory.delete()
    }
    
    private suspend fun handleLogEvent(payload: Map<String, Any?>): CommandResult {
        val level = (payload["level"] as? String)?.uppercase() ?: "INFO"
        val message = payload["message"] as? String ?: return CommandResult.Error("No message provided")
        
        when (level) {
            "DEBUG" -> Log.d(TAG, "Remote log: $message")
            "INFO" -> Log.i(TAG, "Remote log: $message")
            "WARN" -> Log.w(TAG, "Remote log: $message")
            "ERROR" -> Log.e(TAG, "Remote log: $message")
            else -> Log.i(TAG, "Remote log ($level): $message")
        }
        
        return CommandResult.Success()
    }
}
