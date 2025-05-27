package com.antiglitch.yetanothernotifier.utils

import android.content.Context
import android.util.TypedValue
import androidx.compose.ui.unit.Dp

/**
 * Converts a Dp value to pixels using the device's display metrics
 */
fun Dp.toPx(context: Context): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.value,
        context.resources.displayMetrics
    ).toInt()
}
