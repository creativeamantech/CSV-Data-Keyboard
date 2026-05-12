package com.mahavtaar.csvkeyboard.ui.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AppPreferences.getBoolean(context, AppPreferences.KEY_BALL_ENABLED, false)) {
                val work = OneTimeWorkRequestBuilder<StartBallWorker>()
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueue(work)
            }
        }
    }
}
