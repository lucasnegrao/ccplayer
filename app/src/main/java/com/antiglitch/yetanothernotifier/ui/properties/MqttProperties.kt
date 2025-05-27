package com.antiglitch.yetanothernotifier.ui.properties

import kotlinx.serialization.Serializable

// Ranges and defaults for MQTT properties
object MqttPropertyRanges {
    val PORT_RANGE = 1..65535
    val KEEP_ALIVE_RANGE = 10..300 // seconds
    val CONNECTION_TIMEOUT_RANGE = 5..60 // seconds
    val QOS_RANGE = 0..2
    
    // Default values
    const val DEFAULT_PORT = 1883
    const val DEFAULT_KEEP_ALIVE = 60
    const val DEFAULT_CONNECTION_TIMEOUT = 30
    const val DEFAULT_QOS = 0
}

@Serializable
data class MqttProperties(
    val enabled: Boolean = false,
    val serverHost: String = "broker.hivemq.com",
    val serverPort: Int = MqttPropertyRanges.DEFAULT_PORT,
    val clientId: String = "YANClient-${System.currentTimeMillis().toString().takeLast(8)}",
    val username: String = "",
    val password: String = "",
    val subscribeTopic: String = "yan/control/#",
    val publishTopic: String = "yan/status",
    val autoReconnect: Boolean = true,
    val cleanSession: Boolean = true,
    val keepAlive: Int = MqttPropertyRanges.DEFAULT_KEEP_ALIVE, // seconds
    val connectionTimeout: Int = MqttPropertyRanges.DEFAULT_CONNECTION_TIMEOUT, // seconds
    val qos: QosLevel = QosLevel.AT_MOST_ONCE,
    val encryption: EncryptionType = EncryptionType.NONE
) {
    // Computed property for full server URI
    val serverUri: String
        get() = when (encryption) {
            EncryptionType.NONE -> "tcp://$serverHost:$serverPort"
            EncryptionType.TLS -> "ssl://$serverHost:$serverPort"
            EncryptionType.WSS -> "wss://$serverHost:$serverPort"
        }
    
    companion object {
        // Validation methods
        fun validatePort(value: Int): Int = 
            value.coerceIn(MqttPropertyRanges.PORT_RANGE)
        
        fun validateKeepAlive(value: Int): Int =
            value.coerceIn(MqttPropertyRanges.KEEP_ALIVE_RANGE)
        
        fun validateConnectionTimeout(value: Int): Int =
            value.coerceIn(MqttPropertyRanges.CONNECTION_TIMEOUT_RANGE)
        
        fun validateQos(value: Int): Int =
            value.coerceIn(MqttPropertyRanges.QOS_RANGE)
        
        fun validateClientId(value: String): String =
            if (value.isBlank()) "YANClient-${System.currentTimeMillis().toString().takeLast(8)}" else value
        
        fun validateTopic(value: String): String =
            value.trim().takeIf { it.isNotBlank() } ?: "yan/default"
    }
}

@Serializable
enum class QosLevel(val value: Int, val displayName: String, val description: String) {
    AT_MOST_ONCE(0, "QoS 0", "At most once (fire and forget)"),
    AT_LEAST_ONCE(1, "QoS 1", "At least once (acknowledged delivery)"),
    EXACTLY_ONCE(2, "QoS 2", "Exactly once (assured delivery)")
}

@Serializable
enum class EncryptionType(val displayName: String, val defaultPort: Int) {
    NONE("None (TCP)", 1883),
    TLS("TLS/SSL", 8883),
    WSS("WebSocket Secure", 8084)
}
