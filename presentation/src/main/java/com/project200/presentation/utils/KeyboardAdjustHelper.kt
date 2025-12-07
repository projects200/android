package com.project200.presentation.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

object KeyboardAdjustHelper {
    /**
     * 키보드가 올라올 때 키보드 높이와 시스템 바 높이 중 더 큰 값으로 하단 패딩을 적용합니다.
     * @param targetView 패딩을 적용할 뷰
     */
    fun View.applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
            val systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            // 키보드(IME) 높이와 시스템 바 높이 중 더 큰 값을 하단 패딩으로 사용
            val bottomPadding = max(systemBarInsets.bottom, imeInsets.bottom)

            view.updatePadding(
                left = systemBarInsets.left,
                top = systemBarInsets.top,
                right = systemBarInsets.right,
                bottom = bottomPadding,
            )

            WindowInsetsCompat.CONSUMED
        }
    }
}
