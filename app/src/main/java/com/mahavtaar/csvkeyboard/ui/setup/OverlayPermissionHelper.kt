package com.mahavtaar.csvkeyboard.ui.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager

object OverlayPermissionHelper {

    const val REQUEST_CODE_OVERLAY = 1001

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(context)) return true

            return try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val testView = View(context)
                val params = WindowManager.LayoutParams(
                    0, 0,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                wm.addView(testView, params)
                wm.removeView(testView)
                true
            } catch (e: WindowManager.BadTokenException) {
                false
            } catch (e: Exception) {
                false
            }
        } else true
    }

    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
    }
}
