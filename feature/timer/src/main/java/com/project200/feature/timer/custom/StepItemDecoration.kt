package com.project200.feature.timer.custom

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.project200.presentation.utils.UiUtils.dpToPx

class StepItemDecoration(private val spaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = parent.adapter?.itemCount ?: 0

        // 첫 번째 아이템일 때 상단에 공간 추가
        if (position == 0) {
            outRect.top = dpToPx(view.context, spaceHeight.toFloat())
        }

        // 마지막 아이템일 때 하단에 공간 추가
        if (position == itemCount - 1) {
            outRect.bottom = dpToPx(view.context, spaceHeight.toFloat())
        }
    }
}
