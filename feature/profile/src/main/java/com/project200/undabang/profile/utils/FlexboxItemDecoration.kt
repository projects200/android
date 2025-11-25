package com.project200.undabang.profile.utils

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class FlexboxItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.right = space
        outRect.bottom = space
    }
}