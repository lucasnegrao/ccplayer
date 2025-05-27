package com.antiglitch.yetanothernotifier.ui.properties

import android.content.Context
import com.antiglitch.yetanothernotifier.data.datastore.PreferencesDataStoreImpl
import com.antiglitch.yetanothernotifier.data.datastore.preferencesDataStore
import com.antiglitch.yetanothernotifier.data.repository.BasePropertiesRepository

class MqttPropertiesRepository private constructor(
    context: Context
) : BasePropertiesRepository<MqttProperties>(
    preferencesDataStore = PreferencesDataStoreImpl(context.preferencesDataStore),
    keyPrefix = "mqtt",
    defaultProperties = MqttProperties()
) {

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
        val validPort = MqttProperties.validatePort(port)
        updateProperty(MqttProperties::serverPort, validPort) {
            copy(serverPort = it)
        }
    }

    fun updateClientId(clientId: String) {
        val validClientId = MqttProperties.validateClientId(clientId)
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
        updateProperty(MqttProperties::password, password) {
            copy(password = it)
        }
    }

    fun updateSubscribeTopic(topic: String) {
        val validTopic = MqttProperties.validateTopic(topic)
        updateProperty(MqttProperties::subscribeTopic, validTopic) {
            copy(subscribeTopic = it)
        }
    }

    fun updatePublishTopic(topic: String) {
        val validTopic = MqttProperties.validateTopic(topic)
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
        val validKeepAlive = MqttProperties.validateKeepAlive(keepAlive)
        updateProperty(MqttProperties::keepAlive, validKeepAlive) {
            copy(keepAlive = it)
        }
    }

    fun updateConnectionTimeout(timeout: Int) {
        val validTimeout = MqttProperties.validateConnectionTimeout(timeout)
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

    // Batch update method
    fun updateMultipleProperties(updates: MqttProperties.() -> MqttProperties) {
        val currentProperties = properties.value
        val newProperties = currentProperties.updates()
        updateProperties(newProperties)
    }

    // Convenience method to generate a new client ID
    fun generateNewClientId() {
        val newClientId = "YANClient-${System.currentTimeMillis().toString().takeLast(8)}"
        updateClientId(newClientId)
    }

    companion object {
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
}
