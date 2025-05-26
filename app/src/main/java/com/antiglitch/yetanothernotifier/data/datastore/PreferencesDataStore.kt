package com.antiglitch.yetanothernotifier.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

interface PreferencesDataStore {
    suspend fun <T> save(key: String, value: T)
    fun <T> get(key: String, defaultValue: T): Flow<T>
    suspend fun clear()
}

class PreferencesDataStoreImpl(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PreferencesDataStore {

    override suspend fun <T> save(key: String, value: T) {
        dataStore.edit { preferences ->
            when (value) {
                is String -> preferences[stringPreferencesKey(key)] = value
                is Int -> preferences[intPreferencesKey(key)] = value
                is Long -> preferences[longPreferencesKey(key)] = value
                is Float -> preferences[floatPreferencesKey(key)] = value
                is Boolean -> preferences[booleanPreferencesKey(key)] = value
                else -> {
                    // For complex objects, serialize to JSON
                    val serializer = serializer(value!!::class.java)
                    val jsonString = json.encodeToString(serializer, value)
                    preferences[stringPreferencesKey(key)] = jsonString
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String, defaultValue: T): Flow<T> {
        return dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                when (defaultValue) {
                    is String -> preferences[stringPreferencesKey(key)] ?: defaultValue
                    is Int -> preferences[intPreferencesKey(key)] ?: defaultValue
                    is Long -> preferences[longPreferencesKey(key)] ?: defaultValue
                    is Float -> preferences[floatPreferencesKey(key)] ?: defaultValue
                    is Boolean -> preferences[booleanPreferencesKey(key)] ?: defaultValue
                    else -> {
                        val jsonString = preferences[stringPreferencesKey(key)]
                        if (jsonString != null) {
                            try {
                                val serializer = serializer(defaultValue!!::class.java)
                                json.decodeFromString(serializer, jsonString)
                            } catch (e: Exception) {
                                defaultValue
                            }
                        } else {
                            defaultValue
                        }
                    }
                } as T
            }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
