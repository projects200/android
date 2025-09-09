package com.project200.feature.timer.utils

import android.content.Context
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * PopupMenu의 스타일을 지정하는 유틸리티 함수들을 모아놓은 object
 */
object MenuStyler {
    /**
     * MenuItem의 텍스트 색상을 변경합니다.
     */
    fun applyTextColor(
        context: Context,
        item: MenuItem,
        @ColorRes colorRes: Int,
    ) {
        val titleString = SpannableString(item.title)
        titleString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, colorRes)),
            0,
            titleString.length,
            0,
        )
        item.title = titleString
    }

    /**
     * PopupMenu의 아이콘을 강제로 표시합니다. (리플렉션 사용)
     * @param popupMenu 아이콘을 표시할 PopupMenu 객체
     */
    fun showIcons(popupMenu: PopupMenu) {
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popupMenu)
            mPopup.javaClass
                .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(mPopup, true)
        } catch (e: Exception) {
            Timber.e(e, "Error showing menu icons.")
        }
    }
}
