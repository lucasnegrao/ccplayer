package com.antiglitch.yetanothernotifier.messaging.handlers

import android.content.Context
import android.util.Log
import com.antiglitch.yetanothernotifier.messaging.Command
import com.antiglitch.yetanothernotifier.messaging.CommandHandler
import com.antiglitch.yetanothernotifier.messaging.CommandResult

/**
 * Handler for show_notification commands
 */
class NotificationCommandHandler(private val context: Context) : CommandHandler {
    companion object {
        private const val TAG = "NotificationCmdHandler"
    }
    
    override val actionPattern = "show_notification"
    
    override fun canHandle(command: Command): Boolean {
        return command.action == actionPattern
    }
    
    override suspend fun handle(command: Command): CommandResult {
        val title = command.payload["title"] as? String ?: "Notification"
        val message = command.payload["message"] as? String ?: return CommandResult.Error("Missing message")
        
        Log.d(TAG, "Showing notification: $title - $message")
        
        return try {
            // Implementation of showing notification
            // NotificationHelper.showNotification(context, title, message)
            CommandResult.Success()
        } catch (e: Exception) {
            CommandResult.Error("Failed to show notification: ${e.message}", e)
        }
    }
}
