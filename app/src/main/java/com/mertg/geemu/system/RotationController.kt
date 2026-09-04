package com.mertg.geemu.system

import android.content.Context
import android.provider.Settings
import android.view.Surface

object RotationController {
    fun canControlSystemRotation(context: Context): Boolean = Settings.System.canWrite(context)

    fun enterGameMode(context: Context) {
        if (!canControlSystemRotation(context)) return
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.USER_ROTATION,
            Surface.ROTATION_90
        )
    }

    fun exitGameMode(context: Context) {
        if (!canControlSystemRotation(context)) return
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.USER_ROTATION,
            Surface.ROTATION_0
        )
    }
}
