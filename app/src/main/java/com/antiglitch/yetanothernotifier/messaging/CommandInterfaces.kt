package com.antiglitch.yetanothernotifier.messaging

/**
 * Represents a command from any source
 */
interface Command {
    val source: CommandSource
    val action: String
    val payload: Map<String, Any?>
}

/**
 * Sources that can generate commands
 */
enum class CommandSource {
    MQTT,
    INTENT,
    REST,
    INTERNAL
}

/**
 * Interface for command handlers
 */
interface CommandHandler {
    val actionPattern: String // Could be regex pattern or exact match
    fun canHandle(command: Command): Boolean
    suspend fun handle(command: Command): CommandResult
}

/**
 * Result of command processing
 */
sealed class CommandResult {
    data class Success(val data: Any? = null) : CommandResult()
    data class Error(val message: String, val exception: Exception? = null) : CommandResult()
}
