package com.project200.common.utils

import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class SystemClockProvider @Inject constructor() : ClockProvider {
    override fun now(): LocalDate = LocalDate.now()
    override fun yearMonthNow(): YearMonth = YearMonth.now()
}