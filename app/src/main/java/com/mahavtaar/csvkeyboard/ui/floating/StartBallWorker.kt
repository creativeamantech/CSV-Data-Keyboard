package com.mahavtaar.csvkeyboard.ui.floating

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class StartBallWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        FloatingBallManager.start(applicationContext)
        return Result.success()
    }
}
