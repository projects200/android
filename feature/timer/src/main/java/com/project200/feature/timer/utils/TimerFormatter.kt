package com.project200.feature.timer.utils

object TimerFormatter {
    fun Int.toFormattedTime(): String {
        val minutes = this / 60
        val seconds = this % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}