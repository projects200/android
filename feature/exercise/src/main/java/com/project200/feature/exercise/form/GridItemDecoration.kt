package com.project200.feature.exercise.form

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingDp: Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view) // 아이템 위치
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val column = position % spanCount // 아이템 열

        outRect.left = column * spacingDp / spanCount
        outRect.right = spacingDp - (column + 1) * spacingDp / spanCount
        if (position >= spanCount) { // 첫 번째 행 이후부터 상단 간격
            outRect.top = spacingDp
        }
    }
}