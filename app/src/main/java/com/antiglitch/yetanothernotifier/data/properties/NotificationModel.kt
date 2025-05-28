package com.antiglitch.yetanothernotifier.data.properties

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A generic class to hold the definition and state of a dynamic property.
 *
 * @param T The type of the property's value.
 * @param key The unique string identifier for this property.
 * @param displayName The human-readable name for UI purposes.
 * @param defaultValue The initial value of the property.
 * @param state The reactive MutableState holding the current value for Compose.
 * @param range Optional: The permissible range for numerical or Dp values (e.g., ClosedRange<Long>, ClosedFloatingPointRange<Float>).
 * @param step Optional: The step increment for numerical or Dp values.
 * @param enumClass Optional: The class of the enum, if this property is an enum type.
 * @param enumValues Optional: An array of all possible enum values.
 */
data class Property<T>(
    val key: String,
    val displayName: String,
    val defaultValue: T,
    val state: MutableState<T> = mutableStateOf(defaultValue),
    // Metadata specific to types
    val range: Any? = null,
    val step: Any? = null,
    val hidden: Boolean = false,
    val enumClass: Class<out Enum<*>>? = null,
    val enumValues: Array<out Enum<*>>? = null
) {
    // Convenience accessor for the value using property syntax
    var value: T
        get() = state.value
        set(newValue) {
            state.value = newValue
        }

    /** Helper to safely cast and retrieve the range with its specific type. */
    @Suppress("UNCHECKED_CAST")
    fun <R> getRangeTyped(): R? = range as? R

    /** Helper to safely cast and retrieve the step with its specific type. */
    @Suppress("UNCHECKED_CAST")
    fun <S> getStepTyped(): S? = step as? S
}

/**
 * The model holding all notification-related dynamic properties.
 * Keys for properties are defined as string literals during their instantiation here.
 */
class NotificationModel constructor() { // Make constructor public (or remove private)
    // Use LinkedHashMap to preserve the insertion order of properties for consistent UI rendering.
    private val _properties = linkedMapOf<String, Property<*>>()
    val properties: Map<String, Property<*>> get() = _properties

    init {
        // Duration (Long)
        addProperty(
            Property(
                key = "duration",
                displayName = "Duration (ms)",
                defaultValue = 3000L,      // Default from NVP
                range = 1000L..10000L, // Updated from PropertyRanges
                step = 500L            // Updated from PropertyRanges
            )
        )

        // Scale (Float)
        addProperty(
            Property(
                key = "scale",
                displayName = "Scale",
                defaultValue = 0.5f,       // Default from NVP
                range = 0.1f..1.0f,  // Updated from PropertyRanges
                step = 0.05f           // Updated from PropertyRanges
            )
        )

        // Aspect Ratio (Enum)
        addProperty(
            Property(
                key = "aspect",
                displayName = "Aspect Ratio",
                defaultValue = AspectRatio.WIDE,
                enumClass = AspectRatio::class.java,
                enumValues = AspectRatio.values()
            )
        )

        // Corner Radius (Dp)
        addProperty(
            Property(
                key = "cornerRadius",
                displayName = "Corner Radius",
                defaultValue = 12.dp,      // Default from NVP
                range = 0.dp..30.dp,   // Updated from PropertyRanges
                step = 2.dp            // Updated from PropertyRanges
            )
        )

        // Gravity (Enum)
        addProperty(
            Property(
                key = "gravity",
                displayName = "Gravity",
                defaultValue = Gravity.TOP_CENTER,
                enumClass = Gravity::class.java,
                enumValues = Gravity.values()
            )
        )

        // Rounded Corners (Boolean)
        addProperty(
            Property(
                key = "roundedCorners",
                displayName = "Rounded Corners",
                defaultValue = true
                // No range/step/enum for Boolean typically
            )
        )

        // Margin (Dp)
        addProperty(
            Property(
                key = "margin",
                displayName = "Margin",
                defaultValue = 16.dp,      // Default from NVP
                range = 0.dp..64.dp,   // Matches PropertyRanges
                step = 1.dp            // Kept model's specific step
            )
        )

        // Transparency (Float)
        addProperty(
            Property(
                key = "transparency",
                displayName = "Transparency (Alpha)",
                defaultValue = 1.0f,       // Default from NVP
                range = 0.0f..1.0f,  // Matches PropertyRanges
                step = 0.05f           // Updated from PropertyRanges
            )
        )
        addProperty(
            Property(
                key = "screenHeightDp",
                displayName = "Screen Height (dp)",
                defaultValue = 640f,       // Default from NVP
                range = 0.0f..2160f,  // Matches PropertyRanges
                step = 1.0f,           // Updated from PropertyRanges
                hidden = true
            )
        )
        addProperty(
            Property(
                key = "screenWidthDp",
                displayName = "Screen Width (dp)",
                defaultValue = 360f,       // Default from NVP
                range = 0.0f..2160f,  // Matches PropertyRanges
                step = 1.0f,           // Updated from PropertyRanges
                hidden = true
            )
        )
    }

    private fun <T> addProperty(property: Property<T>) {
        if (_properties.containsKey(property.key)) {
            throw IllegalArgumentException("Property with key '${property.key}' already exists.")
        }
        _properties[property.key] = property
    }

    /** Retrieves a property by its key. */
    fun getProperty(key: String): Property<*>? {
        return _properties[key]
    }

    /** Retrieves the MutableState of a property by its key, with a type cast. */
    @Suppress("UNCHECKED_CAST")
    fun <T> getPropertyState(key: String): MutableState<T>? {
        return _properties[key]?.state as? MutableState<T>
    }

    /** Creates a deep copy of this NotificationModel with all current property values preserved. */
    fun copy(): NotificationModel {
        val newModel = NotificationModel()
        
        // Copy all current property values to the new model
        _properties.forEach { (key, property) ->
            val newProperty = newModel.getProperty(key)
            if (newProperty != null) {
                // Copy the current value to the new model's property
                when (property.value) {
                    is Long -> (newProperty as? Property<Long>)?.value = property.value as Long
                    is Float -> (newProperty as? Property<Float>)?.value = property.value as Float
                    is Dp -> (newProperty as? Property<Dp>)?.value = property.value as Dp
                    is Boolean -> (newProperty as? Property<Boolean>)?.value = property.value as Boolean
                    is AspectRatio -> (newProperty as? Property<AspectRatio>)?.value = property.value as AspectRatio
                    is Gravity -> (newProperty as? Property<Gravity>)?.value = property.value as Gravity
                    else -> {
                        // Use reflection for safe assignment
                        try {
                            newProperty.state.value = property.value as Nothing
                        } catch (e: Exception) {
                            // Log warning but continue
                            println("Warning: Could not copy property '$key' with value ${property.value}")
                        }
                    }
                }
            }
        }
        
        return newModel
    }

}