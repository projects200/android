package com.project200.feature.timer.custom

import androidx.recyclerview.widget.RecyclerView

interface OnStepItemClickListener {
    fun onDeleteClick(id: Long)

    fun onTimeClick(
        id: Long,
        time: Int,
    )

    fun onStepNameChanged(
        id: Long,
        name: String,
    )

    fun onNewStepNameChanged(name: String)

    fun onNewStepTimeClick(time: Int)

    fun onAddStepClick()

    fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
}
