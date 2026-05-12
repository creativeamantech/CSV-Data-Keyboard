package com.mahavtaar.csvkeyboard.ui.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences

object FloatingBallManager {
    fun start(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            return
        }

        try {
            context.startForegroundService(
                Intent(context, FloatingBallService::class.java)
            )
        } catch (e: Exception) {
            context.startService(
                Intent(context, FloatingBallService::class.java)
            )
        }
        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, true)
    }

    fun stop(context: Context) {
        val intent = Intent(context, FloatingBallService::class.java)
        context.stopService(intent)
        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, false)
    }

    fun isRunning(context: Context): Boolean {
        // Quick check via preferences. A true check would use ActivityManager,
        // but this works for our current UX flow toggle
        return AppPreferences.getBoolean(context, AppPreferences.KEY_BALL_ENABLED)
    }
}
