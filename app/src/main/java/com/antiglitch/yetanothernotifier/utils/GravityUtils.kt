package com.antiglitch.yetanothernotifier.utils

import com.antiglitch.yetanothernotifier.data.properties.Gravity
import android.view.Gravity as AndroidGravity

/**
 * Converts the app's custom Gravity type to Android's system Gravity int constants
 */
fun Gravity.toAndroidGravity(): Int {
    return when (this) {
        Gravity.TOP_START -> AndroidGravity.TOP or AndroidGravity.START
        Gravity.TOP_CENTER -> AndroidGravity.TOP or AndroidGravity.CENTER_HORIZONTAL
        Gravity.TOP_END -> AndroidGravity.TOP or AndroidGravity.END
        Gravity.CENTER_START -> AndroidGravity.CENTER_VERTICAL or AndroidGravity.START
        Gravity.CENTER -> AndroidGravity.CENTER
        Gravity.CENTER_END -> AndroidGravity.CENTER_VERTICAL or AndroidGravity.END
        Gravity.BOTTOM_START -> AndroidGravity.BOTTOM or AndroidGravity.START
        Gravity.BOTTOM_CENTER -> AndroidGravity.BOTTOM or AndroidGravity.CENTER_HORIZONTAL
        Gravity.BOTTOM_END -> AndroidGravity.BOTTOM or AndroidGravity.END
    }
}
