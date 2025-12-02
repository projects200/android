package com.project200.common.constants

import androidx.annotation.StringRes
import com.project200.undabang.common.R

enum class DayOfWeek(val index: Int, @StringRes val resId: Int) {
    MON(0, R.string.day_mon),
    TUE(1, R.string.day_tue),
    WED(2, R.string.day_wed),
    THU(3, R.string.day_thu),
    FRI(4, R.string.day_fri),
    SAT(5, R.string.day_sat),
    SUN(6, R.string.day_sun);

    companion object {
        fun getByIndex(index: Int): DayOfWeek? = entries.find { it.index == index }
    }
}