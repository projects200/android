package com.project200.data.utils

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LocalDateAdapter {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    @ToJson
    fun toJson(value: LocalDate?): String? {
        return value?.format(formatter)
    }

    @FromJson
    fun fromJson(value: String?): LocalDate? {
        return value?.let {
            try {
                LocalDate.parse(it, formatter)
            } catch (e: java.time.format.DateTimeParseException) {
                null
            }
        }
    }
}

class LocalDateTimeAdapter {
    // 서버와 주고받는 날짜시간 문자열 형식을 지정합니다. (예: "2023-10-27T10:15:30")
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @ToJson
    fun toJson(value: LocalDateTime?): String? {
        return value?.format(formatter)
    }

    @FromJson
    fun fromJson(value: String?): LocalDateTime? {
        return value?.let {
            try {
                LocalDateTime.parse(it, formatter)
            } catch (e: java.time.format.DateTimeParseException) {
                null
            }
        }
    }
}