package com.project200.feature.chatting.utils

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView

class KeyboardVisibilityHelper(
    private val rootView: View,
    private val recyclerView: RecyclerView
) {
    private var isKeyboardVisible = false

    private val listener = ViewTreeObserver.OnGlobalLayoutListener {
        val r = Rect()
        rootView.getWindowVisibleDisplayFrame(r)
        val screenHeight = rootView.rootView.height
        val keypadHeight = screenHeight - r.bottom

        if (keypadHeight > screenHeight * 0.15) {
            if (!isKeyboardVisible) {
                recyclerView.smoothScrollBy(0, keypadHeight)
            }
            isKeyboardVisible = true
        } else {
            isKeyboardVisible = false
        }
    }

    fun start() {
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    fun stop() {
        rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }
}