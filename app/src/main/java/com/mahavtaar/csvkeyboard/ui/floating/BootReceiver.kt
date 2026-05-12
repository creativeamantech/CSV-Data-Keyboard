package com.mahavtaar.csvkeyboard.ui.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AppPreferences.getBoolean(context, AppPreferences.KEY_BALL_ENABLED, false)) {
                FloatingBallManager.start(context)
            }
        }
    }
}
