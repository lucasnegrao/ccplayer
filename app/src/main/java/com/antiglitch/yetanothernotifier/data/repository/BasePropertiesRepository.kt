package com.antiglitch.yetanothernotifier.data.repository

import com.antiglitch.yetanothernotifier.data.datastore.PreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty1

abstract class BasePropertiesRepository<T : Any>(
    private val preferencesDataStore: PreferencesDataStore,
    private val keyPrefix: String,
    private val defaultProperties: T
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    protected val _properties = MutableStateFlow(defaultProperties)
    val properties: StateFlow<T> = _properties.asStateFlow()

    init {
        android.util.Log.d(
            "BasePropertiesRepository",
            "Initializing repository for $keyPrefix with defaults: $defaultProperties"
        )
        loadProperties()
    }

    protected fun updateProperties(newProperties: T) {
        android.util.Log.d(
            "BasePropertiesRepository",
            "Updating properties for $keyPrefix from ${_properties.value} to $newProperties"
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
        android.util.Log.d("BasePropertiesRepository", "Starting to load properties for $keyPrefix")
        preferencesDataStore.get("${keyPrefix}_properties", defaultProperties)
            .onEach { loadedProperties ->
                android.util.Log.d(
                    "BasePropertiesRepository",
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

    private fun saveProperties(properties: T) {
        repositoryScope.launch {
            try {
                android.util.Log.d(
                    "BasePropertiesRepository",
                    "Saving properties for $keyPrefix: $properties"
                )
                preferencesDataStore.save("${keyPrefix}_properties", properties)
                android.util.Log.d(
                    "BasePropertiesRepository",
                    "Successfully saved properties for $keyPrefix"
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "BasePropertiesRepository",
                    "Error saving properties for $keyPrefix",
                    e
                )
            }
        }
    }

    fun resetToDefaults() {
        updateProperties(defaultProperties)
    }

    suspend fun clearAllData() {
        preferencesDataStore.clear()
        _properties.value = defaultProperties
    }
}
