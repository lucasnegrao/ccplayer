package com.antiglitch.yetanothernotifier.data.repository

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.Dp
import com.antiglitch.yetanothernotifier.data.datastore.PreferencesDataStoreImpl
import com.antiglitch.yetanothernotifier.data.datastore.preferencesDataStore
import com.antiglitch.yetanothernotifier.data.properties.AspectRatio
import com.antiglitch.yetanothernotifier.data.properties.Gravity
import com.antiglitch.yetanothernotifier.data.properties.NotificationVisualProperties

class NotificationVisualPropertiesRepository private constructor(
    context: Context
) : BasePropertiesRepository<NotificationVisualProperties>(
    preferencesDataStore = PreferencesDataStoreImpl(context.preferencesDataStore),
    keyPrefix = "notification_visual",
    defaultProperties = NotificationVisualProperties()
) {

    // Dynamic property updaters using reflection
    fun updateDuration(duration: Long) {
        val validDuration = NotificationVisualProperties.Companion.validateDuration(duration)
        updateProperty(NotificationVisualProperties::duration, validDuration) {
            copy(duration = it)
        }
    }

    fun updateMargin(margin: Dp) {
        val validMargin = NotificationVisualProperties.Companion.validateMargin(margin)
        updateProperty(NotificationVisualProperties::margin, validMargin) {
            copy(margin = it)
        }
    }

    fun updateScale(scale: Float) {
        val validScale = NotificationVisualProperties.Companion.validateScale(scale)
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
        val validRadius = NotificationVisualProperties.Companion.validateCornerRadius(radius)
        updateProperty(NotificationVisualProperties::cornerRadius, validRadius) {
            copy(cornerRadius = it)
        }
    }

    fun updateTransparency(transparency: Float) {
        val validTransparency = NotificationVisualProperties.Companion.validateTransparency(transparency)
        updateProperty(NotificationVisualProperties::transparency, validTransparency) {
            copy(transparency = it)
        }
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