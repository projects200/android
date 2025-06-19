package com.project200.common.utils

import java.time.LocalDate
import java.time.YearMonth

interface ClockProvider {
    fun now(): LocalDate
    fun yearMonthNow(): YearMonth
}