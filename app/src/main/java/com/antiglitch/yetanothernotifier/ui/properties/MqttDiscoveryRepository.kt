package com.antiglitch.yetanothernotifier.ui.properties

import android.content.Context
import com.antiglitch.yetanothernotifier.network.MdnsDiscoveryService
import com.antiglitch.yetanothernotifier.network.MdnsService
import com.antiglitch.yetanothernotifier.network.DiscoveryState
import kotlinx.coroutines.flow.StateFlow

class MqttDiscoveryRepository private constructor(
    context: Context
) {
    private val mdnsService = MdnsDiscoveryService.getInstance(context)
    
    // Expose discovery state
    val discoveryState: StateFlow<DiscoveryState> = mdnsService.discoveryState
    
    // Discovery methods
    fun startMqttDiscovery(timeoutMs: Long = 15000L) {
        mdnsService.startMqttDiscovery(timeoutMs)
    }
    
    fun stopDiscovery() {
        mdnsService.stopDiscovery()
    }
    
    fun clearResults() {
        mdnsService.clearResults()
    }
    
    // Helper methods for MQTT-specific logic
    fun getMqttServices(): List<MdnsService> {
        return when (val state = discoveryState.value) {
            is DiscoveryState.Found -> state.services.filter { it.type.contains("mqtt", ignoreCase = true) }
            else -> emptyList()
        }
    }
    
    fun getRecommendedMqttService(): MdnsService? {
        val services = getMqttServices()
        
        // Prioritize services with common MQTT names
        val prioritizedNames = listOf("mosquitto", "mqtt", "broker", "hivemq", "emqx")
        
        return services.find { service ->
            prioritizedNames.any { name -> 
                service.displayName.contains(name, ignoreCase = true) 
            }
        } ?: services.firstOrNull()
    }
    
    companion object {
        @Volatile
        private var INSTANCE: MqttDiscoveryRepository? = null
        
        fun getInstance(context: Context): MqttDiscoveryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MqttDiscoveryRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
