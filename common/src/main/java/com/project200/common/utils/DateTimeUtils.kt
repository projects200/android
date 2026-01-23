package com.project200.common.utils

import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

fun String?.toLocalDate(formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE): LocalDate? {
    if (this.isNullOrBlank()) {
        return null
    }
    return try {
        LocalDate.parse(this, formatter)
    } catch (e: DateTimeParseException) {
        Timber.e(e, "Failed to parse date string '$this' with formatter '$formatter'")
        null
    }
}

fun LocalDate?.toFormattedString(formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE): String? {
    return this?.format(formatter)
}

fun String?.toLocalDateTime(formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME): LocalDateTime? {
    if (this.isNullOrBlank()) {
        return null
    }
    return try {
        LocalDateTime.parse(this, formatter)
    } catch (e: DateTimeParseException) {
        Timber.e(e, "Failed to parse date-time string '$this' with formatter '$formatter'")
        null
    }
}

fun LocalDateTime?.toFormattedString(formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME): String? {
    return this?.format(formatter)
}

object CommonDateTimeFormatters {
    val YY_MM_DD_HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("yy.MM.dd HH:mm", Locale.KOREAN)
    val YYYY_M_KR = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA)

    val YYYY_MM_DD_KR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREAN)
    val HH_MM_KR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

    val a_h_mm_KR = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
    val YYYY_MM_DD_SLASH_KR = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.KOREAN)
}
