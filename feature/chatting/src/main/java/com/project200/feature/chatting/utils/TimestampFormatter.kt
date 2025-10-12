package com.project200.feature.chatting.utils

import com.project200.common.utils.CommonDateTimeFormatters.YYYY_MM_DD_SLASH_KR
import com.project200.common.utils.CommonDateTimeFormatters.a_h_mm_KR
import java.time.LocalDate
import java.time.LocalDateTime

object TimestampFormatter {
    // 시간 포맷팅을 처리하는 함수
    fun formatTimestamp(dateTime: LocalDateTime): String {
        val today = LocalDate.now()
        val messageDate = dateTime.toLocalDate()

        return if (messageDate.isEqual(today)) {
            dateTime.format(a_h_mm_KR)
        } else {
            dateTime.format(YYYY_MM_DD_SLASH_KR)
        }
    }
}