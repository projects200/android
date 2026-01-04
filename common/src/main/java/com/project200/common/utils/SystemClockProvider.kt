package com.project200.common.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

class SystemClockProvider
    @Inject
    constructor() : ClockProvider {
        override fun now(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

        override fun yearMonthNow(): YearMonth = YearMonth.now(ZoneId.of("Asia/Seoul"))

        override fun nowTime(): LocalTime = LocalDateTime.now(ZoneId.of("Asia/Seoul")).toLocalTime()
    }
