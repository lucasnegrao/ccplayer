package com.antiglitch.yetanothernotifier.ui.properties

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VisualPropertiesRepository {
    private val _notificationProperties = MutableStateFlow(NotificationVisualProperties())
    val notificationProperties: StateFlow<NotificationVisualProperties> = _notificationProperties.asStateFlow()

    fun updateNotificationProperties(properties: NotificationVisualProperties) {
        _notificationProperties.value = properties
    }

    fun updateNotificationDuration(duration: Long) {
        _notificationProperties.value = _notificationProperties.value.copy(duration = duration)
    }

    fun updateNotificationMargin(margin: androidx.compose.ui.unit.Dp) {
        _notificationProperties.value = _notificationProperties.value.copy(margin = margin)
    }


    fun updateNotificationScale(scale: Float) {
        _notificationProperties.value = _notificationProperties.value.copy(scale = scale)
    }

    fun updateNotificationAspect(aspect: AspectRatio) {
        _notificationProperties.value = _notificationProperties.value.copy(aspect = aspect)
    }


    fun updateNotificationGravity(gravity: Gravity) {
        _notificationProperties.value = _notificationProperties.value.copy(gravity = gravity)
    }

    fun updateRoundedCorners(enabled: Boolean) {
        _notificationProperties.value = _notificationProperties.value.copy(roundedCorners = enabled)
    }

    fun updateCornerRadius(radius: androidx.compose.ui.unit.Dp) {
        _notificationProperties.value = _notificationProperties.value.copy(cornerRadius = radius)
    }

    companion object {
        @Volatile
        private var INSTANCE: VisualPropertiesRepository? = null

        fun getInstance(): VisualPropertiesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VisualPropertiesRepository().also { INSTANCE = it }
            }
        }
    }
}
