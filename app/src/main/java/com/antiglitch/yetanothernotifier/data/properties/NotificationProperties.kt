package com.antiglitch.yetanothernotifier.data.properties

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer // For String.serializer()


// PropertyRanges object is removed

@Serializable(with = NotificationVisualPropertiesSerializer::class) // Updated to use custom serializer
data class NotificationVisualProperties(
    val model: NotificationModel = NotificationModel()
) {
    // Delegating properties to the model
    val duration: Long get() = model.getProperty("duration")!!.value as Long
    val scale: Float get() = model.getProperty("scale")!!.value as Float
    val aspect: AspectRatio get() = model.getProperty("aspect")!!.value as AspectRatio
    val cornerRadius: Dp get() = model.getProperty("cornerRadius")!!.value as Dp
    val gravity: Gravity get() = model.getProperty("gravity")!!.value as Gravity
    val roundedCorners: Boolean get() = model.getProperty("roundedCorners")!!.value as Boolean
    val margin: Dp get() = model.getProperty("margin")!!.value as Dp
    val transparency: Float get() = model.getProperty("transparency")!!.value as Float
     val screenWidthDp: Float get() = model.getProperty("screenWidthDp")!!.value as Float
     val screenHeightDp: Float get() = model.getProperty("screenHeightDp")!!.value as Float

    // Convenience map to access properties by key
    val propertiesMap: Map<String, Property<*>> get() = model.properties

    // Computed properties using stored screen dimensions and model values (via delegated properties)
    val width: Dp
        get() = getSize(screenWidthDp, screenHeightDp).first

    val height: Dp
        get() = getSize(screenWidthDp, screenHeightDp).second

    fun getSize(screenWidthDp: Float, screenHeightDp: Float): Pair<Dp, Dp> {
        // Debug logging
        // Uses this.scale and this.aspect, which now delegate to the model
        println("DEBUG: getSize called with screenWidth=$screenWidthDp, screenHeight=$screenHeightDp, scale=$scale, aspect=${aspect.ratio}")

        // Determine if this is a portrait aspect ratio (height > width)
        val isPortraitRatio = aspect.ratio < 1.0f

        val finalWidth: Float
        val finalHeight: Float

        if (isPortraitRatio) {
            // For portrait ratios, scale applies to height
            val targetHeight = screenHeightDp * scale // Remove 0.9f multiplier
            val calculatedWidth = targetHeight * aspect.ratio

            // Check if width fits (leave small margin for safety)
            val maxWidth = screenWidthDp * 0.95f
            if (calculatedWidth > maxWidth) {
                finalWidth = maxWidth
                finalHeight = finalWidth / aspect.ratio
            } else {
                finalWidth = calculatedWidth
                finalHeight = targetHeight
            }
        } else {
            // For landscape ratios, scale applies to width
            val targetWidth = screenWidthDp * scale // Remove 0.9f multiplier
            val calculatedHeight = targetWidth / aspect.ratio

            // Check if height fits (leave small margin for safety)
            val maxHeight = screenHeightDp * 0.95f
            if (calculatedHeight > maxHeight) {
                finalHeight = maxHeight
                finalWidth = finalHeight * aspect.ratio
            } else {
                finalWidth = targetWidth
                finalHeight = calculatedHeight
            }
        }

        println("DEBUG: Final dimensions: width=$finalWidth, height=$finalHeight")
        return finalWidth.dp to finalHeight.dp
    }

    companion object {
        private const val DEFAULT_SCREEN_WIDTH_DP = 360f // Typical Android screen width
        private const val DEFAULT_SCREEN_HEIGHT_DP = 640f // Typical Android screen height

        // Validation methods are removed, as ranges are defined in NotificationModel
    }
}

@Serializable
enum class AspectRatio(val ratio: Float, val displayName: String) {
    ULTRA_WIDE(6.0f, "Ultra Wide"),
    WIDE(4.0f, "Wide"),
    CINEMA(2.35f, "Cinema"),
    STANDARD(1.77f, "16:9"),
    GOLDEN(1.618f, "Golden"),
    CLASSIC(1.33f, "4:3"),
    SQUARE(1.0f, "Square"),
    PORTRAIT(0.75f, "3:4"),
    TALL(0.5f, "Tall"),
    ULTRA_TALL(0.25f, "Ultra Tall")
}

@Serializable
enum class Gravity(val displayName: String) {
    TOP_START("Top Left"),
    TOP_CENTER("Top Center"),
    TOP_END("Top Right"),
    CENTER_START("Middle Left"),
    CENTER("Center"),
    CENTER_END("Middle Right"),
    BOTTOM_START("Bottom Left"),
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_END("Bottom Right");

    companion object {
        val gravityGrid = listOf(
            listOf(TOP_START, TOP_CENTER, TOP_END),
            listOf(CENTER_START, CENTER, CENTER_END),
            listOf(BOTTOM_START, BOTTOM_CENTER, BOTTOM_END)
        )
    }
}

// Custom serializer for Dp - kept in case other parts of the system use it or if serialization is reintroduced
internal object DpSerializer : KSerializer<Dp> { // Changed to internal object
    override val descriptor = PrimitiveSerialDescriptor(
        "Dp",
        PrimitiveKind.FLOAT
    )

    override fun serialize(encoder: Encoder, value: Dp) =
        encoder.encodeFloat(value.value)

    override fun deserialize(decoder: Decoder): Dp =
        decoder.decodeFloat().dp
}

internal object NotificationVisualPropertiesSerializer : KSerializer<NotificationVisualProperties> {
    private val mapSerializer = MapSerializer(
        serializer<String>(),
        serializer<JsonElement>()
    )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NotificationVisualProperties") {
        element("properties", mapSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: NotificationVisualProperties) {
        val json = Json // Default Json instance
        val propertyValuesJson = buildMap<String, JsonElement> {
            value.model.properties.forEach { (key, prop) ->
                try {
                    val propValue = prop.value
                    val jsonElement = when (propValue) {
                        is Dp -> json.encodeToJsonElement(DpSerializer, propValue) // Use DpSerializer object
                        is AspectRatio -> json.encodeToJsonElement(propValue)
                        is Gravity -> json.encodeToJsonElement(propValue)
                        is Long -> JsonPrimitive(propValue)
                        is Float -> JsonPrimitive(propValue)
                        is Boolean -> JsonPrimitive(propValue)
                        null -> throw SerializationException("Property value for key '$key' is null, which is not supported for serialization.")
                        else -> throw SerializationException("Unsupported property type for key '$key': ${propValue::class}")
                    }
                    put(key, jsonElement)
                } catch (e: Exception) {
                    throw SerializationException("Error serializing property '$key' from NotificationVisualProperties: ${e.message}", e)
                }
            }
        }
        encoder.encodeSerializableValue(mapSerializer, propertyValuesJson)
    }

    override fun deserialize(decoder: Decoder): NotificationVisualProperties {
        val json = Json { ignoreUnknownKeys = true }
        val propertyValuesJson = decoder.decodeSerializableValue(mapSerializer)
        
        // Create NVP with a new default model. Screen dimensions will be default.
        val nvp = NotificationVisualProperties() 

        propertyValuesJson.forEach { (key, jsonElement) ->
            val property = nvp.model.getProperty(key)
            if (property != null) {
                try {
                    val deserializedValue: Any? = when (property.defaultValue) {
                        is Dp -> json.decodeFromJsonElement(DpSerializer, jsonElement) // Use DpSerializer object
                        is AspectRatio -> json.decodeFromJsonElement<AspectRatio>(jsonElement)
                        is Gravity -> json.decodeFromJsonElement<Gravity>(jsonElement)
                        is Long -> jsonElement.jsonPrimitive.longOrNull
                        is Float -> jsonElement.jsonPrimitive.floatOrNull
                        is Boolean -> jsonElement.jsonPrimitive.booleanOrNull
                        else -> throw SerializationException("Unsupported default property type for deserialization for key '$key': ${property.defaultValue!!::class}")
                    }

                    if (deserializedValue != null) {
                        @Suppress("UNCHECKED_CAST")
                        (property as Property<Any?>).value = deserializedValue
                    } else {
                        // Log or handle null/unparsable values if necessary
                        System.err.println("Warning: Deserialized value for key '$key' in NVP was null or unparsable from $jsonElement. Using default.")
                    }
                } catch (e: Exception) {
                    System.err.println("Error deserializing property '$key' for NVP from $jsonElement. Using default. Error: ${e.message}")
                    // Property retains its default value
                }
            } else {
                // Log or handle unknown keys if necessary
                 System.err.println("Warning: Property key '$key' from NVP serialized data not found in current NotificationModel definition. Ignoring.")
            }
        }
        return nvp
    }
}

