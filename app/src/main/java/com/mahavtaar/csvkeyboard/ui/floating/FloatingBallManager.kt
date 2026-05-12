package com.mahavtaar.csvkeyboard.ui.floating

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences
import com.mahavtaar.csvkeyboard.ui.setup.MainActivity
import com.mahavtaar.csvkeyboard.ui.setup.OverlayPermissionHelper

object FloatingBallManager {
    fun start(context: Context) {
        if (!OverlayPermissionHelper.hasOverlayPermission(context)) {
            Log.w("FloatingBall", "Overlay permission not granted — aborting start")
            return
        }

        val intent = Intent(context, FloatingBallService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException is available in API 31+
                // Catching Exception to be safe across versions, or specifically handle it
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    showStartReminderNotification(context)
                } else {
                    e.printStackTrace()
                }
            }
        } else {
            context.startService(intent)
        }

        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, true)
    }

    fun stop(context: Context) {
        val intent = Intent(context, FloatingBallService::class.java)
        context.stopService(intent)
        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, false)
    }

    fun isRunning(context: Context): Boolean {
        return AppPreferences.getBoolean(context, AppPreferences.KEY_BALL_ENABLED)
    }

    private fun showStartReminderNotification(context: Context) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, FloatingBallService.CHANNEL_ID)
            .setContentTitle("CSV Keyboard")
            .setContentText("Tap to start the Floating Ball")
            .setSmallIcon(R.drawable.ic_csv_ball)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(999, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
