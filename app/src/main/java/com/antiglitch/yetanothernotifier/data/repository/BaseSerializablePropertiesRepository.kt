package com.antiglitch.yetanothernotifier.data.repository

import android.util.Log
import com.antiglitch.yetanothernotifier.data.datastore.SimpleStringDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.reflect.KProperty1

abstract class BaseSerializablePropertiesRepository<T : Any>(
    private val simpleStringDataStore: SimpleStringDataStore,
    private val json: Json,
    private val serializer: KSerializer<T>,
    private val keyPrefix: String,
    internal val defaultProperties: T
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    protected val _properties = MutableStateFlow(defaultProperties)
    val properties: StateFlow<T> = _properties.asStateFlow()

    private val serializedDefaultProperties: String by lazy {
        try {
            json.encodeToString(serializer, defaultProperties)
        } catch (e: Exception) {
            Log.e(
                "BaseSerializableRepo",
                "Error serializing default properties for $keyPrefix. Using empty JSON object as fallback.",
                e
            )
            "{}" // Fallback to empty JSON object string if default serialization fails
        }
    }

    init {
        Log.d(
            "BaseSerializableRepo",
            "Initializing repository for $keyPrefix with defaults: $defaultProperties"
        )
        loadProperties()
    }

    fun updateProperties(newProperties: T) {
        Log.d(
            "BaseSerializableRepo",
            "Updating properties for $keyPrefix. FROM (current _properties.value): ${_properties.value} TO (newProperties argument): $newProperties"
        )
        _properties.value = newProperties
        saveProperties(newProperties)
    }

    protected inline fun <reified V> updateProperty(
        property: KProperty1<T, V>,
        value: V,
        crossinline copyFn: T.(V) -> T
    ) {
        val newProperties = _properties.value.copyFn(value)
        updateProperties(newProperties)
    }

    private fun loadProperties() {
        Log.d("BaseSerializableRepo", "Starting to load properties for $keyPrefix")
        // Assuming PreferencesDataStore has getStringFlow(key: String, defaultValue: String): Flow<String>
        simpleStringDataStore.getStringFlow("${keyPrefix}_properties", serializedDefaultProperties)
            .map { serializedString ->
                try {
                    json.decodeFromString(serializer, serializedString)
                } catch (e: Exception) {
                    Log.e(
                        "BaseSerializableRepo",
                        "Error deserializing properties for $keyPrefix from string: $serializedString. Using default.",
                        e
                    )
                    defaultProperties // Use default if deserialization fails
                }
            }
            .catch { e ->
                Log.e(
                    "BaseSerializableRepo",
                    "Error loading properties string for $keyPrefix. Using default.",
                    e
                )
                emit(defaultProperties) // Emit default on upstream error
            }
            .onEach { loadedProperties ->
                Log.d(
                    "BaseSerializableRepo",
                    "Loaded properties for $keyPrefix: $loadedProperties"
                )
                _properties.value = loadedProperties
            }
            .launchIn(repositoryScope)
    }

    // Public method to force reload properties from DataStore
    fun reloadProperties() {
        loadProperties()
    }

    private fun saveProperties(propertiesToSave: T) {
        repositoryScope.launch {
            try {
                Log.d(
                    "BaseSerializableRepo",
                    "Preparing to save properties for $keyPrefix. Object to serialize (propertiesToSave.toString()): $propertiesToSave"
                )
                val serializedString = json.encodeToString(serializer, propertiesToSave)
                Log.d(
                    "BaseSerializableRepo",
                    "Saving properties for $keyPrefix (serializedString): $serializedString"
                )
                // Assuming PreferencesDataStore has saveString(key: String, value: String)
                simpleStringDataStore.saveString("${keyPrefix}_properties", serializedString)
                Log.d(
                    "BaseSerializableRepo",
                    "Successfully saved properties for $keyPrefix"
                )
            } catch (e: Exception) {
                Log.e(
                    "BaseSerializableRepo",
                    "Error serializing or saving properties for $keyPrefix",
                    e
                )
            }
        }
    }

    fun resetToDefaults() {
        updateProperties(defaultProperties)
    }

    suspend fun clearAllData() {
        // This clears the entire DataStore. If you want to clear only this key:
        // preferencesDataStore.saveString("${keyPrefix}_properties", serializedDefaultProperties)
        // Or if your DataStore supports removing a key:
        // preferencesDataStore.remove("${keyPrefix}_properties")
        simpleStringDataStore.clear() // Keeps original behavior
        _properties.value = defaultProperties
    }
}
