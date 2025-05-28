package com.antiglitch.yetanothernotifier.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore // Ensure this is the correct import
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// This extension property provides a DataStore<Preferences> instance.
// Changed to use "app_preferences" for consistency if this is the main preference file.
// Renamed for general use.
// Note: If this extension is defined elsewhere (e.g., in PropertiesDataStore.kt)
// with the name "app_preferences", ensure only one definition is used or they are identical.
// It's best practice to define such an extension once in a central location.
val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")


interface SimpleStringDataStore {
    suspend fun saveString(key: String, value: String)
    fun getStringFlow(key: String, defaultValue: String): Flow<String>
    suspend fun clear()
    // Optional: if you need to remove a specific key
    suspend fun remove(key: String)
}

class SimpleStringDataStoreImpl(
    private val dataStore: DataStore<Preferences>
) : SimpleStringDataStore {

    override suspend fun saveString(key: String, value: String) {
        try {
            Log.d("SimpleStringDataStore", "Saving string for key: $key, value: $value")
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
            Log.d("SimpleStringDataStore", "Successfully saved string for key: $key")
        } catch (e: Exception) {
            Log.e("SimpleStringDataStore", "Error saving string for key $key", e)
            throw e // Re-throw to allow caller to handle
        }
    }

    override fun getStringFlow(key: String, defaultValue: String): Flow<String> {
        return dataStore.data
            .catch { exception ->
                Log.e(
                    "SimpleStringDataStore",
                    "Error reading string preferences for key $key. Emitting default.",
                    exception
                )
                emit(emptyPreferences()) // Emit empty preferences on error to recover
            }
            .map { preferences ->
                preferences[stringPreferencesKey(key)] ?: defaultValue
            }
    }

    override suspend fun clear() {
        Log.d("SimpleStringDataStore", "Clearing all preferences.")
        dataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d("SimpleStringDataStore", "Successfully cleared all preferences.")
    }

    override suspend fun remove(key: String) {
        try {
            Log.d("SimpleStringDataStore", "Removing key: $key")
            dataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey(key))
            }
            Log.d("SimpleStringDataStore", "Successfully removed key: $key")
        } catch (e: Exception) {
            Log.e("SimpleStringDataStore", "Error removing key $key", e)
            throw e
        }
    }
}
