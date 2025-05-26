package com.antiglitch.yetanothernotifier.ui.properties

import android.content.Context
import androidx.compose.ui.unit.Dp
import com.antiglitch.yetanothernotifier.data.datastore.PreferencesDataStoreImpl
import com.antiglitch.yetanothernotifier.data.datastore.preferencesDataStore
import com.antiglitch.yetanothernotifier.data.repository.BasePropertiesRepository

class NotificationVisualPropertiesRepository private constructor(
    context: Context
) : BasePropertiesRepository<NotificationVisualProperties>(
    preferencesDataStore = PreferencesDataStoreImpl(context.preferencesDataStore),
    keyPrefix = "notification_visual",
    defaultProperties = NotificationVisualProperties()
) {
    
    // Dynamic property updaters using reflection
    fun updateDuration(duration: Long) {
        val validDuration = NotificationVisualProperties.validateDuration(duration)
        updateProperty(NotificationVisualProperties::duration, validDuration) { 
            copy(duration = it) 
        }
    }

    fun updateMargin(margin: Dp) {
        val validMargin = NotificationVisualProperties.validateMargin(margin)
        updateProperty(NotificationVisualProperties::margin, validMargin) { 
            copy(margin = it) 
        }
    }

    fun updateScale(scale: Float) {
        val validScale = NotificationVisualProperties.validateScale(scale)
        updateProperty(NotificationVisualProperties::scale, validScale) { 
            copy(scale = it) 
        }
    }

    fun updateAspect(aspect: AspectRatio) {
        updateProperty(NotificationVisualProperties::aspect, aspect) { 
            copy(aspect = it) 
        }
    }

    fun updateGravity(gravity: Gravity) {
        updateProperty(NotificationVisualProperties::gravity, gravity) { 
            copy(gravity = it) 
        }
    }

    fun updateRoundedCorners(enabled: Boolean) {
        updateProperty(NotificationVisualProperties::roundedCorners, enabled) { 
            copy(roundedCorners = it) 
        }
    }

    fun updateCornerRadius(radius: Dp) {
        val validRadius = NotificationVisualProperties.validateCornerRadius(radius)
        updateProperty(NotificationVisualProperties::cornerRadius, validRadius) { 
            copy(cornerRadius = it) 
        }
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
                INSTANCE ?: NotificationVisualPropertiesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
