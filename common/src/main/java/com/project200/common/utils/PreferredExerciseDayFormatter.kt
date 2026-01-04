package com.project200.common.utils

import android.content.Context
import com.project200.common.constants.DayOfWeek
import com.project200.undabang.common.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PreferredExerciseDayFormatter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * 월요일부터 일요일까지의 선택 여부를 담은 Boolean 리스트를
         * 지정된 규칙에 따라 문자열로 포맷합니다.
         * @param days 크기가 7인 Boolean 리스트 [월, 화, 수, 목, 금, 토, 일] 순서
         * @return 포맷팅된 요일 문자열
         */
        fun formatDaysOfWeek(days: List<Boolean>): String {
            if (days.size != 7) return ""

            // 모든 요일이 포함될 경우 "매일"로 표기
            if (days.all { it }) return context.getString(R.string.day_everyday)

            val resultParts = mutableListOf<String>()
            val weekdays = days.subList(0, 5) // 월요일부터 금요일까지
            val weekend = days.subList(5, 7) // 토요일, 일요일

            // 평일이 모두 포함될 경우 "평일"로 표기
            if (weekdays.all { it }) {
                resultParts.add(context.getString(R.string.day_weekdays))
            } else {
                // 각각 해당하는 평일 날짜를 추가
                weekdays.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        addDayName(index, resultParts)
                    }
                }
            }

            // 주말이 모두 포함될 경우 "주말"로 표기
            if (weekend.all { it }) {
                resultParts.add(context.getString(R.string.day_weekend))
            } else {
                // 각각 해당하는 주말 날짜를 추가
                weekend.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        addDayName(index + 5, resultParts)
                    }
                }
            }

            return resultParts.joinToString(", ")
        }

        private fun addDayName(
            index: Int,
            resultList: MutableList<String>,
        ) {
            DayOfWeek.getByIndex(index)?.let { day ->
                resultList.add(context.getString(day.resId))
            }
        }
    }
