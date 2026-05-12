package com.mahavtaar.csvkeyboard.ui.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences

object FloatingBallManager {
    fun start(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
            return
        }
        val intent = Intent(context, FloatingBallService::class.java)
        context.startForegroundService(intent)
        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, true)
    }

    fun stop(context: Context) {
        val intent = Intent(context, FloatingBallService::class.java)
        context.stopService(intent)
        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, false)
    }
}
