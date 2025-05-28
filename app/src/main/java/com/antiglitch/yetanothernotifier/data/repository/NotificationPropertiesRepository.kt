package com.antiglitch.yetanothernotifier.data.repository

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.Dp
import com.antiglitch.yetanothernotifier.data.datastore.PreferencesDataStoreImpl
import com.antiglitch.yetanothernotifier.data.datastore.preferencesDataStore
import com.antiglitch.yetanothernotifier.data.properties.AspectRatio
import com.antiglitch.yetanothernotifier.data.properties.Gravity
import com.antiglitch.yetanothernotifier.data.properties.NotificationVisualProperties
import com.antiglitch.yetanothernotifier.data.properties.Property
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NotificationVisualPropertiesRepository private constructor(
    context: Context
) : BasePropertiesRepository<NotificationVisualProperties>(
    preferencesDataStore = PreferencesDataStoreImpl(context.preferencesDataStore),
    keyPrefix = "notification_visual",
    defaultProperties = NotificationVisualProperties()
) {

    // Scope for flow transformations within the repository.
    // For a true singleton, this scope should be managed according to the application lifecycle.
    // For simplicity, using SupervisorJob + Dispatchers.Default.
    // Consider cancelling this scope if the repository can be destroyed.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Provides a StateFlow of the dynamic properties map from the NotificationModel.
     * This map is suitable for dynamically building UI components.
     * The Map structure and Property metadata are read-only.
     * Property values are observable via their MutableState.
     */
    val dynamicPropertiesMap: StateFlow<Map<String, Property<*>>> =
        properties.map { notificationVisualProperties ->
            notificationVisualProperties.model.properties // or .propertiesMap
        }.stateIn(
            scope = repositoryScope,
            started = SharingStarted.WhileSubscribed(5000L), // Or Eagerly, depending on needs
            initialValue = defaultProperties.model.properties // Initial value from default
        )

    /**
     * Updates a property within the NotificationModel by its key.
     * Validates the new value against the property's defined range.
     * Persists the changes to NotificationVisualProperties.
     *
     * @param V The type of the property value.
     * @param key The string key of the property in NotificationModel.
     * @param newValue The new value for the property.
     */
    fun <V : Any> updatePropertyByKey(key: String, newValue: V) {
        val currentNVP = properties.value
        val property = currentNVP.model.getProperty(key) as? Property<V> ?: run {
            Log.e(
                "NotificationVisualPropertiesRepository",
                "Property not found or type mismatch for key '$key' with value type ${newValue::class.java.simpleName}"
            )
            return
        }

        // Validate the new value against the property's range
        val validatedValue: V = property.range?.let { rng ->
            try {
                when (property.defaultValue) { // Infer type T for range from defaultValue
                    is Dp -> if (newValue is Dp && rng is ClosedRange<*>) (newValue as Dp).coerceIn(rng as ClosedRange<Dp>) as V else newValue
                    is Long -> if (newValue is Long && rng is ClosedRange<*>) (newValue as Long).coerceIn(rng as ClosedRange<Long>) as V else newValue
                    is Float -> if (newValue is Float && rng is ClosedFloatingPointRange<*>) (newValue as Float).coerceIn(rng as ClosedFloatingPointRange<Float>) as V else newValue
                    else -> newValue // Non-numeric types or types without standard coerceIn
                }
            } catch (e: ClassCastException) {
                Log.e(
                    "NotificationVisualPropertiesRepository",
                    "Type mismatch during validation for key '$key'. Expected range type compatible with ${property.defaultValue!!::class.java.simpleName}. Got value ${newValue::class.java.simpleName}. Range: $rng",
                    e
                )
                newValue // Fallback to unvalidated value if cast fails
            }
        } ?: newValue // If range is null, use newValue as is

        if (property.value != validatedValue) {
            property.value = validatedValue // Update the MutableState in the model
            // Create a shallow copy of NVP. This ensures that the Flow emits a new instance,
            // even though the 'model' instance within NVP is the same but mutated.
            // Serialization must handle the updated state of the 'model'.
            updateProperties(currentNVP.copy())
        }
    }

    // Refactored property updaters
    fun updateDuration(duration: Long) {
        updatePropertyByKey("duration", duration)
    }

    fun updateMargin(margin: Dp) {
        updatePropertyByKey("margin", margin)
    }

    fun updateScale(scale: Float) {
        updatePropertyByKey("scale", scale)
    }

    fun updateAspect(aspect: AspectRatio) {
        updatePropertyByKey("aspect", aspect)
    }

    fun updateGravity(gravity: Gravity) {
        updatePropertyByKey("gravity", gravity)
    }

    fun updateRoundedCorners(enabled: Boolean) {
        updatePropertyByKey("roundedCorners", enabled)
    }

    fun updateCornerRadius(radius: Dp) {
        updatePropertyByKey("cornerRadius", radius)
    }

    fun updateTransparency(transparency: Float) {
        updatePropertyByKey("transparency", transparency)
    }

    fun updateScreenDimensions(screenWidthDp: Float, screenHeightDp: Float) {
        val currentProperties = properties.value
        Log.d(
            "NotificationVisualPropertiesRepository",
            "Updating screen dimensions from ${currentProperties.screenWidthDp}x${currentProperties.screenHeightDp} to ${screenWidthDp}x${screenHeightDp}"
        )

        val newProperties = currentProperties.copy(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp
        )
        updateProperties(newProperties)
    }

    // Batch update method
    fun updateMultipleProperties(updates: NotificationVisualProperties.() -> NotificationVisualProperties) {
        val currentProperties = properties.value
        val newProperties = currentProperties.updates()
        updateProperties(newProperties)
    }

    companion object {
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