package com.antiglitch.yetanothernotifier

import android.content.Context
import android.os.Build
import android.provider.Settings

object PermissionUtil {
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            // On older versions, this permission is granted at install time
            true
        }
    }
}
