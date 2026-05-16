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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(activity)) {
            return // Already granted or not needed
        }

        // Try 1: Standard ACTION_MANAGE_OVERLAY_PERMISSION (works on stock Android)
        val standardIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )

        // Try 2: Some OEMs need ACTION_APPLICATION_DETAILS_SETTINGS as fallback
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${activity.packageName}")
        )

        // Try 3: Last resort — open general Settings
        val generalSettingsIntent = Intent(Settings.ACTION_SETTINGS)

        try {
            activity.startActivityForResult(standardIntent, REQUEST_CODE_OVERLAY)
        } catch (e1: android.content.ActivityNotFoundException) {
            try {
                activity.startActivityForResult(appDetailsIntent, REQUEST_CODE_OVERLAY)
                // Show a toast explaining what to do manually
                android.widget.Toast.makeText(
                    activity,
                    "Go to Permissions → Display over other apps → Allow",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e2: android.content.ActivityNotFoundException) {
                try {
                    activity.startActivityForResult(generalSettingsIntent, REQUEST_CODE_OVERLAY)
                    android.widget.Toast.makeText(
                        activity,
                        "Please find and enable 'Display over other apps' for this app in Settings",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (e3: android.content.ActivityNotFoundException) {
                    android.widget.Toast.makeText(
                        activity,
                        "Please manually enable 'Draw over other apps' permission for CSV Keyboard in your device Settings",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
