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
import kotlin.reflect.full.memberProperties

abstract class BasePropertiesRepository<T : Any>(
    private val preferencesDataStore: PreferencesDataStore,
    private val keyPrefix: String,
    private val defaultProperties: T
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    protected val _properties = MutableStateFlow(defaultProperties)
    val properties: StateFlow<T> = _properties.asStateFlow()

    init {
        loadProperties()
    }

    protected fun updateProperties(newProperties: T) {
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
        preferencesDataStore.get("${keyPrefix}_properties", defaultProperties)
            .onEach { loadedProperties ->
                _properties.value = loadedProperties
            }
            .launchIn(repositoryScope)
    }

    private fun saveProperties(properties: T) {
        repositoryScope.launch {
            preferencesDataStore.save("${keyPrefix}_properties", properties)
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
