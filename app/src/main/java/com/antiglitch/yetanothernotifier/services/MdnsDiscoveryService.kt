package com.antiglitch.yetanothernotifier.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class MdnsService(
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String> = emptyMap()
) {
    val displayName: String get() = name.replace("\\032", " ").trim()
    val hostPort: String get() = "$host:$port"
}

sealed class DiscoveryState {
    object Idle : DiscoveryState()
    object Scanning : DiscoveryState()
    data class Found(val services: List<MdnsService>) : DiscoveryState()
    data class Error(val message: String) : DiscoveryState()
}

class MdnsDiscoveryService private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "MdnsDiscoveryService"

        // Common service types
        const val MQTT_SERVICE_TYPE = "_mqtt._tcp"
        const val HTTP_SERVICE_TYPE = "_http._tcp"
        const val SSH_SERVICE_TYPE = "_ssh._tcp"
        const val FTP_SERVICE_TYPE = "_ftp._tcp"

        @Volatile
        private var INSTANCE: MdnsDiscoveryService? = null

        fun getInstance(context: Context): MdnsDiscoveryService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MdnsDiscoveryService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var nsdManager: NsdManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State management
    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    // Track discovered services
    private val discoveredServices = ConcurrentHashMap<String, MdnsService>()
    private var currentDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var activeResolvers = mutableSetOf<NsdManager.ResolveListener>()

    // Discovery timeout
    private var discoveryJob: Job? = null

    init {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager == null) {
                Log.e(TAG, "Failed to get NsdManager service")
                _discoveryState.value =
                    DiscoveryState.Error("NSD service not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing NsdManager", e)
            _discoveryState.value = DiscoveryState.Error("Failed to initialize NSD: ${e.message}")
        }
    }

    fun startDiscovery(
        serviceType: String,
        timeoutMs: Long = 10000L
    ) {
        serviceScope.launch {
            try {
                // Check if NsdManager is available
                val manager = nsdManager ?: run {
                    _discoveryState.value = DiscoveryState.Error("NSD service not available")
                    Log.e(TAG, "Cannot start discovery - NsdManager is null")
                    return@launch
                }

                _discoveryState.value = DiscoveryState.Scanning
                discoveredServices.clear()

                Log.d(TAG, "Starting discovery for service type: $serviceType")

                val discoveryListener = createDiscoveryListener(serviceType)
                currentDiscoveryListener = discoveryListener

                try {
                    withContext(Dispatchers.Main) {
                        manager.discoverServices(
                            serviceType,
                            NsdManager.PROTOCOL_DNS_SD,
                            discoveryListener
                        )
                    }

                    // Set timeout
                    discoveryJob = launch {
                        delay(timeoutMs)
                        Log.d(TAG, "Discovery timeout reached after ${timeoutMs}ms")
                        stopDiscovery()
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception during discovery", e)
                    _discoveryState.value = DiscoveryState.Error("Security error: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during discovery", e)
                    _discoveryState.value = DiscoveryState.Error("Discovery error: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error starting discovery", e)
                _discoveryState.value =
                    DiscoveryState.Error("Failed to start discovery: ${e.message}")
            }
        }
    }


    // Debugging method to help diagnose network and discovery issues
    fun runDiagnostics() {
        serviceScope.launch {
            Log.d(TAG, "--- MDNS DIAGNOSTICS ---")

            // Check permissions
            val hasInternet = context.checkPermission(
                Manifest.permission.INTERNET,
                Process.myPid(),
                Process.myUid()
            ) == PackageManager.PERMISSION_GRANTED

            val hasLocation = context.checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Process.myPid(),
                Process.myUid()
            ) == PackageManager.PERMISSION_GRANTED

            Log.d(TAG, "Permissions: INTERNET=$hasInternet, LOCATION=$hasLocation")

            // Check if service is available
            Log.d(TAG, "NsdManager available: ${nsdManager != null}")

            // Check network state if we have the permission
            if (context.checkPermission(
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Process.myPid(),
                    Process.myUid()
                ) == PackageManager.PERMISSION_GRANTED
            ) {

                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = connectivityManager?.activeNetwork
                val capabilities =
                    activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

                Log.d(TAG, "Active network: ${activeNetwork != null}")
                Log.d(TAG, "Network capabilities: $capabilities")

                // Check if we're on WiFi
                val isWifi =
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                Log.d(TAG, "On WiFi network: $isWifi")
            }

            Log.d(TAG, "------------------------")
        }
    }

    private fun createDiscoveryListener(serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to start. Error code: $errorCode")
                serviceScope.launch {
                    _discoveryState.value =
                        DiscoveryState.Error("Discovery failed to start (Error: $errorCode)")
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to stop. Error code: $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started for: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped for: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                discoveredServices.remove(serviceInfo.serviceName)

                // Update state with current services
                serviceScope.launch {
                    val services = discoveredServices.values.toList()
                    if (_discoveryState.value is DiscoveryState.Scanning && services.isNotEmpty()) {
                        _discoveryState.value = DiscoveryState.Found(services)
                    }
                }
            }
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}. Error code: $errorCode")
                activeResolvers.remove(this)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service resolved: ${serviceInfo.serviceName}")

                try {
                    val host = serviceInfo.host?.hostAddress ?: "unknown"
                    val port = serviceInfo.port
                    val attributes = serviceInfo.attributes?.mapNotNull { (key, value) ->
                        key?.let { k -> k to (String(value ?: byteArrayOf())) }
                    }?.toMap() ?: emptyMap()

                    val service = MdnsService(
                        name = serviceInfo.serviceName,
                        type = serviceInfo.serviceType,
                        host = host,
                        port = port,
                        attributes = attributes
                    )

                    discoveredServices[serviceInfo.serviceName] = service

                    // Update state with current services
                    serviceScope.launch {
                        val services = discoveredServices.values.toList()
                        _discoveryState.value = DiscoveryState.Found(services)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing resolved service", e)
                }

                activeResolvers.remove(this)
            }
        }

        activeResolvers.add(resolveListener)

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service resolution", e)
            activeResolvers.remove(resolveListener)
        }
    }

    fun clearResults() {
        discoveredServices.clear()
        _discoveryState.value = DiscoveryState.Idle
    }

    // Convenience methods for common service types
    fun startMqttDiscovery(timeoutMs: Long = 10000L) {
        runDiagnostics() // Run diagnostics before attempting discovery
        startDiscovery(MQTT_SERVICE_TYPE, timeoutMs)
    }

    fun startHttpDiscovery(timeoutMs: Long = 10000L) = startDiscovery(HTTP_SERVICE_TYPE, timeoutMs)

    fun stopDiscovery() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Stopping discovery")
                discoveryJob?.cancel()

                // Stop active resolvers
                activeResolvers.forEach { resolver ->
                    try {
                        withContext(Dispatchers.Main) {
                            nsdManager?.stopServiceResolution(resolver)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping resolver", e)
                    }
                }
                activeResolvers.clear()

                // Stop discovery
                currentDiscoveryListener?.let { listener ->
                    try {
                        withContext(Dispatchers.Main) {
                            nsdManager?.stopServiceDiscovery(listener)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping discovery", e)
                    }
                }
                currentDiscoveryListener = null

                // Update state with final results
                val services = discoveredServices.values.toList()
                _discoveryState.value = if (services.isNotEmpty()) {
                    DiscoveryState.Found(services)
                } else {
                    DiscoveryState.Idle
                }

                Log.d(TAG, "Discovery stopped. Found ${services.size} services")

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
                _discoveryState.value =
                    DiscoveryState.Error("Failed to stop discovery: ${e.message}")
            }
        }
    }

    fun onDestroy() {
        stopDiscovery()
        serviceScope.cancel()
    }
}
