package com.project200.feature.timer.custom

interface OnStepItemClickListener {
    fun onDeleteClick(id: Long)
    fun onTimeClick(id: Long, time: Int)
    fun onStepNameChanged(id: Long, name: String)
    fun onNewStepNameChanged(name: String)
    fun onNewStepTimeClick(time: Int)
    fun onAddStepClick()
}