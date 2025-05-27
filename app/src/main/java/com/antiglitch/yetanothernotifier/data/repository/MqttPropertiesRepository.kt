package com.antiglitch.yetanothernotifier.data.repository

import android.content.Context
import android.util.Log
import com.antiglitch.yetanothernotifier.data.datastore.PreferencesDataStoreImpl
import com.antiglitch.yetanothernotifier.data.datastore.preferencesDataStore
import com.antiglitch.yetanothernotifier.data.properties.EncryptionType
import com.antiglitch.yetanothernotifier.data.properties.MqttProperties
import com.antiglitch.yetanothernotifier.data.properties.QosLevel
import com.antiglitch.yetanothernotifier.utils.SecurityUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MqttPropertiesRepository private constructor(
    private val context: Context
) : BasePropertiesRepository<MqttProperties>(
    preferencesDataStore = PreferencesDataStoreImpl(context.preferencesDataStore),
    keyPrefix = "mqtt",
    defaultProperties = MqttProperties()
) {
    companion object {
        private const val TAG = "MqttPropertiesRepository"
        
        @Volatile
        private var INSTANCE: MqttPropertiesRepository? = null

        fun getInstance(context: Context): MqttPropertiesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MqttPropertiesRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    // Create a coroutine scope for the repository
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Create a new properties flow that decrypts passwords on the fly
    private val _decryptedProperties: StateFlow<Any> = super.properties.map { props ->
        props.copy(password = getDecryptedPassword(props.password))
    }.stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = defaultProperties
    )

    // Expose the decrypted properties
    val mqttProperties: StateFlow<MqttProperties> = _decryptedProperties as StateFlow<MqttProperties>

    // Add connection state tracking
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    
    private val _lastMessage = MutableStateFlow<Pair<String, String>?>(null)
    val lastMessage: StateFlow<Pair<String, String>?> = _lastMessage.asStateFlow()
    
    fun updateConnectionState(isConnected: Boolean) {
        _connectionState.value = isConnected
    }
    
    fun updateLastMessage(topic: String, message: String) {
        _lastMessage.value = Pair(topic, message)
    }

    /**
     * Get decrypted password for internal use
     */
    private fun getDecryptedPassword(encryptedPassword: String): String {
        if (encryptedPassword.isEmpty()) return ""
        
        return if (SecurityUtils.isPasswordEncrypted(encryptedPassword)) {
            SecurityUtils.decryptPassword(context, encryptedPassword) ?: ""
        } else {
            // Handle legacy plain text passwords - migrate them
            migratePlainTextPassword(encryptedPassword)
            encryptedPassword
        }
    }
    
    /**
     * Migrate plain text password to encrypted format
     */
    private fun migratePlainTextPassword(plainPassword: String) {
        if (plainPassword.isNotEmpty() && !SecurityUtils.isPasswordEncrypted(plainPassword)) {
            Log.d(TAG, "Migrating plain text password to encrypted format")
            val encryptedPassword = SecurityUtils.encryptPassword(context, plainPassword)
            if (encryptedPassword != null) {
                // Update stored password with encrypted version
                updateProperty(MqttProperties::password, encryptedPassword) {
                    copy(password = it)
                }
            }
        }
    }

    // Individual property updaters with validation
    fun updateEnabled(enabled: Boolean) {
        updateProperty(MqttProperties::enabled, enabled) {
            copy(enabled = it)
        }
    }

    fun updateServerHost(host: String) {
        val validHost = host.trim().takeIf { it.isNotBlank() } ?: "broker.hivemq.com"
        updateProperty(MqttProperties::serverHost, validHost) {
            copy(serverHost = it)
        }
    }

    fun updateServerPort(port: Int) {
        val validPort = MqttProperties.Companion.validatePort(port)
        updateProperty(MqttProperties::serverPort, validPort) {
            copy(serverPort = it)
        }
    }

    fun updateClientId(clientId: String) {
        val validClientId = MqttProperties.Companion.validateClientId(clientId)
        updateProperty(MqttProperties::clientId, validClientId) {
            copy(clientId = it)
        }
    }

    fun updateUsername(username: String) {
        updateProperty(MqttProperties::username, username.trim()) {
            copy(username = it)
        }
    }

    fun updatePassword(password: String) {
        val encryptedPassword = if (password.isEmpty()) {
            ""
        } else {
            SecurityUtils.encryptPassword(context, password) ?: password
        }
        
        updateProperty(MqttProperties::password, encryptedPassword) {
            copy(password = it)
        }
    }

    fun updateSubscribeTopic(topic: String) {
        val validTopic = MqttProperties.Companion.validateTopic(topic)
        updateProperty(MqttProperties::subscribeTopic, validTopic) {
            copy(subscribeTopic = it)
        }
    }

    fun updatePublishTopic(topic: String) {
        val validTopic = MqttProperties.Companion.validateTopic(topic)
        updateProperty(MqttProperties::publishTopic, validTopic) {
            copy(publishTopic = it)
        }
    }

    fun updateAutoReconnect(autoReconnect: Boolean) {
        updateProperty(MqttProperties::autoReconnect, autoReconnect) {
            copy(autoReconnect = it)
        }
    }

    fun updateCleanSession(cleanSession: Boolean) {
        updateProperty(MqttProperties::cleanSession, cleanSession) {
            copy(cleanSession = it)
        }
    }

    fun updateKeepAlive(keepAlive: Int) {
        val validKeepAlive = MqttProperties.Companion.validateKeepAlive(keepAlive)
        updateProperty(MqttProperties::keepAlive, validKeepAlive) {
            copy(keepAlive = it)
        }
    }

    fun updateConnectionTimeout(timeout: Int) {
        val validTimeout = MqttProperties.Companion.validateConnectionTimeout(timeout)
        updateProperty(MqttProperties::connectionTimeout, validTimeout) {
            copy(connectionTimeout = it)
        }
    }

    fun updateQos(qos: QosLevel) {
        updateProperty(MqttProperties::qos, qos) {
            copy(qos = it)
        }
    }

    fun updateEncryption(encryption: EncryptionType) {
        updateProperty(MqttProperties::encryption, encryption) {
            // Auto-update port when encryption type changes
            copy(encryption = it, serverPort = it.defaultPort)
        }
    }

    fun updateAvailabilityTopic(topic: String) {
        val validTopic = MqttProperties.Companion.validateAvailabilityTopic(topic)
        updateProperty(MqttProperties::availabilityTopic, validTopic) {
            copy(availabilityTopic = it)
        }
    }

    fun updateOnlinePayload(payload: String) {
        val validPayload = MqttProperties.Companion.validatePayload(payload)
        updateProperty(MqttProperties::onlinePayload, validPayload) {
            copy(onlinePayload = it)
        }
    }

    fun updateOfflinePayload(payload: String) {
        val validPayload = MqttProperties.Companion.validatePayload(payload)
        updateProperty(MqttProperties::offlinePayload, validPayload) {
            copy(offlinePayload = it)
        }
    }

    fun updateBaseReconnectInterval(interval: Long) {
        val validInterval = interval.coerceIn(1L..3600L) // 1 second to 1 hour
        updateProperty(MqttProperties::baseReconnectInterval, validInterval) {
            copy(baseReconnectInterval = it)
        }
    }

    fun updateMaxReconnectInterval(interval: Long) {
        val validInterval = interval.coerceIn(60L..7200L) // 1 minute to 2 hours
        updateProperty(MqttProperties::maxReconnectInterval, validInterval) {
            copy(maxReconnectInterval = it)
        }
    }

    fun updateReconnectMultiplier(multiplier: Double) {
        val validMultiplier = multiplier.coerceIn(1.0..3.0)
        updateProperty(MqttProperties::reconnectMultiplier, validMultiplier) {
            copy(reconnectMultiplier = it)
        }
    }

    // Batch update method
    fun updateMultipleProperties(updates: MqttProperties.() -> MqttProperties) {
        val currentProperties = mqttProperties.value
        val newProperties = currentProperties.updates()
        updateProperties(newProperties)
    }

    // Convenience method to generate a new client ID
    fun generateNewClientId() {
        val newClientId = "YANClient-${System.currentTimeMillis().toString().takeLast(8)}"
        updateClientId(newClientId)
    }
}