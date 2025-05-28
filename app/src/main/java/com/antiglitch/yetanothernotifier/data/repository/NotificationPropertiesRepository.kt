package com.antiglitch.yetanothernotifier.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.ui.unit.Dp
import com.antiglitch.yetanothernotifier.data.properties.AspectRatio
import com.antiglitch.yetanothernotifier.data.properties.Gravity
import com.antiglitch.yetanothernotifier.data.properties.NotificationVisualProperties
import com.antiglitch.yetanothernotifier.data.properties.Property
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class NotificationVisualPropertiesRepository private constructor(
    private val context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val serializer: KSerializer<NotificationVisualProperties> = NotificationVisualProperties.serializer()
    private val defaultProperties: NotificationVisualProperties = NotificationVisualProperties()

    private val _properties = MutableStateFlow(defaultProperties)
    val properties: StateFlow<NotificationVisualProperties> = _properties.asStateFlow()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // Changed scope for IO

    init {
        repositoryScope.launch {
            loadPropertiesFromPreferences()
        }
    }

    private suspend fun loadPropertiesFromPreferences() {
        withContext(Dispatchers.IO) {
            val jsonString = sharedPreferences.getString(KEY_NOTIFICATION_PROPERTIES, null)
            val loadedProps = if (jsonString != null) {
                try {
                    json.decodeFromString(serializer, jsonString)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing NotificationVisualProperties. Using defaults.", e)
                    defaultProperties
                }
            } else {
                Log.d(TAG, "No saved NotificationVisualProperties found. Using defaults.")
                defaultProperties
            }
            // After loading, screenWidthDp and screenHeightDp will be the defaults from
            // NotificationVisualProperties() because they are transient in serialization.
            // If they were persisted separately, they would be loaded here.
            _properties.value = loadedProps
            Log.d(TAG, "Loaded properties: $loadedProps")
        }
    }

    private suspend fun savePropertiesToPreferences(propertiesToSave: NotificationVisualProperties) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = json.encodeToString(serializer, propertiesToSave)
                sharedPreferences.edit().putString(KEY_NOTIFICATION_PROPERTIES, jsonString).apply()
                Log.d(TAG, "Saved properties: $jsonString")
            } catch (e: Exception) {
                Log.e(TAG, "Error serializing or saving NotificationVisualProperties.", e)
            }
        }
    }
    
    // This internal function will be called by public update methods
    private fun updateAndPersistProperties(newProperties: NotificationVisualProperties) {
        _properties.value = newProperties
        repositoryScope.launch {
            savePropertiesToPreferences(newProperties)
        }
    }

    // Add listener interface and storage
    interface PropertyChangeListener {
        fun onPropertyChanged(key: String, oldValue: Any?, newValue: Any?)
    }

    private val propertyChangeListeners = mutableSetOf<PropertyChangeListener>()

    // Add listener management methods
    fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeListeners.add(listener)
    }
    
    fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeListeners.remove(listener)
    }
    
    private fun notifyPropertyChanged(key: String, oldValue: Any?, newValue: Any?) {
        propertyChangeListeners.forEach { listener ->
            try {
                listener.onPropertyChanged(key, oldValue, newValue)
            } catch (e: Exception) {
                Log.e(TAG, "Error in property change listener", e)
            }
        }
    }

    val dynamicPropertiesMap: StateFlow<Map<String, Property<*>>> =
        properties.map { notificationVisualProperties ->
            notificationVisualProperties.model.properties
        }.stateIn(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default), // Keep separate scope for UI-related flow
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = defaultProperties.model.properties
        )

    fun <V : Any> updatePropertyByKey(key: String, newValue: V) {
        val currentNVP = _properties.value // Use the latest value from StateFlow
        val property = currentNVP.model.getProperty(key) as? Property<V> ?: run {
            Log.e(
                TAG,
                "Property not found or type mismatch for key '$key' with value type ${newValue::class.java.simpleName}"
            )
            return
        }

        // Capture old value for listener notification
        val oldValue = property.value

        val validatedValue: V = property.range?.let { rng ->
            try {
                when (property.defaultValue) {
                    is Dp -> if (newValue is Dp && rng is ClosedRange<*>) (newValue as Dp).coerceIn(rng as ClosedRange<Dp>) as V else newValue
                    is Long -> if (newValue is Long && rng is ClosedRange<*>) (newValue as Long).coerceIn(rng as ClosedRange<Long>) as V else newValue
                    is Float -> if (newValue is Float && rng is ClosedFloatingPointRange<*>) (newValue as Float).coerceIn(rng as ClosedFloatingPointRange<Float>) as V else newValue
                    else -> newValue
                }
            } catch (e: ClassCastException) {
                Log.e(
                    TAG,
                    "Type mismatch during validation for key '$key'. Expected range type compatible with ${property.defaultValue!!::class.java.simpleName}. Got value ${newValue::class.java.simpleName}. Range: $rng",
                    e
                )
                newValue
            }
        } ?: newValue

        if (property.value != validatedValue) {
            // Create a completely new NotificationVisualProperties instance to trigger StateFlow
            val newModel = currentNVP.model.copy() // Assuming your model has a copy method
            newModel.getProperty(key)?.let { prop ->
                (prop as? Property<V>)?.value = validatedValue
            }
            
            val newNVP = currentNVP.copy(model = newModel)
            updateAndPersistProperties(newNVP)
            
            // Trigger state change listeners
            notifyPropertyChanged(key, oldValue, validatedValue)
        }
    }

    fun updateScreenDimensions(screenWidthDp: Float, screenHeightDp: Float) {
     updatePropertyByKey("screenWidthDp", screenWidthDp)
        updatePropertyByKey("screenHeightDp", screenHeightDp)
    }
    
    fun resetToDefaults() {
        Log.d(TAG, "Resetting properties to defaults.")
        // Create a completely new default instance to ensure all states are fresh
        val newDefaultProperties = NotificationVisualProperties()
        
        // Capture old properties for listener notifications
        val oldProperties = _properties.value
        updateAndPersistProperties(newDefaultProperties)
        
        // Notify listeners about all property changes
        oldProperties.model.properties.forEach { (key, oldProperty) ->
            val newProperty = newDefaultProperties.model.getProperty(key)
            if (newProperty != null && oldProperty.value != newProperty.value) {
                notifyPropertyChanged(key, oldProperty.value, newProperty.value)
            }
        }
    }



    companion object {
        private const val TAG = "NotificationVisualRepo" // Shortened Tag
        private const val PREFS_NAME = "notification_visual_properties_prefs"
        private const val KEY_NOTIFICATION_PROPERTIES = "notification_visual_properties_json"


        @Volatile
        private var INSTANCE: NotificationVisualPropertiesRepository? = null

        fun getInstance(context: Context): NotificationVisualPropertiesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE
                    ?: NotificationVisualPropertiesRepository(context.applicationContext).also {
                        INSTANCE = it
                    }
            }
        }
    }
}