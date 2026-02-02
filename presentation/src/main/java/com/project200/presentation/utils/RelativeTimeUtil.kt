package com.project200.presentation.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object RelativeTimeUtil {

    fun getRelativeTime(localDateTime: LocalDateTime?): String {
        if (localDateTime == null) return ""

        val now = LocalDateTime.now()
        val seconds = ChronoUnit.SECONDS.between(localDateTime, now)
        val minutes = ChronoUnit.MINUTES.between(localDateTime, now)
        val hours = ChronoUnit.HOURS.between(localDateTime, now)
        val days = ChronoUnit.DAYS.between(localDateTime, now)

        return when {
            seconds < 60 -> "방금 전"
            minutes < 60 -> "${minutes}분 전"
            hours < 24 -> "${hours}시간 전"
            days < 7 -> "${days}일 전"
            days < 30 -> "${days / 7}주 전"
            days < 365 -> "${days / 30}개월 전"
            else -> "${days / 365}년 전"
        }
    }

    fun getRelativeTime(isoString: String?): String {
        if (isoString.isNullOrEmpty()) return ""
        return try {
            val parsed = LocalDateTime.parse(isoString, DateTimeFormatter.ISO_DATE_TIME)
            getRelativeTime(parsed)
        } catch (e: Exception) {
            isoString
        }
    }
}