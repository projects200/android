package com.project200.domain.manager

interface TimerManager {
    fun setOnTickListener(listener: (millisUntilFinished: Long) -> Unit)
    fun setOnFinishListener(listener: () -> Unit)
    fun start(durationInMillis: Long)
    fun pause()
    fun cancel()
}