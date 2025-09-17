package com.project200.presentation.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowMetrics

object UiUtils {
    fun dpToPx(
        context: Context,
        dp: Float,
    ): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun getScreenWidthPx(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val insets =
                windowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            val bounds = windowMetrics.bounds
            bounds.width() - insets.left - insets.right
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }
}
