package com.project200.feature.timer.utils

import kotlin.math.ceil

object TimerFormatter {
    fun Int.toFormattedTime(): String {
        val minutes = this / 60
        val seconds = this % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
    fun Long.toFormattedTimeAsLong(): String {
        val totalSeconds = ceil(this / 1000.0).toLong()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}