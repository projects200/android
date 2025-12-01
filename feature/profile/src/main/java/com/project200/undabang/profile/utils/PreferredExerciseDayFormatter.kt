package com.project200.undabang.profile.utils

object PreferredExerciseDayFormatter {
    private val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

    /**
     * 월요일부터 일요일까지의 선택 여부를 담은 Boolean 리스트를
     * 지정된 규칙에 따라 문자열로 포맷합니다.
     * @param days 크기가 7인 Boolean 리스트 [월, 화, 수, 목, 금, 토, 일] 순서
     * @return 포맷팅된 요일 문자열
     */
    fun formatDaysOfWeek(days: List<Boolean>): String {
        if (days.size != 7) {
            return ""
        }

        // 모든 요일이 포함될 경우 "매일"로 표기
        if (days.all { it }) {
            return "매일"
        }

        val weekdays = days.subList(0, 5) // 월요일부터 금요일까지
        val weekend = days.subList(5, 7) // 토요일, 일요일

        val isAllWeekdaysSelected = weekdays.all { it }
        val isAllWeekendSelected = weekend.all { it }

        val resultParts = mutableListOf<String>()

        // 평일이 모두 포함될 경우 "평일"로 표기
        if (isAllWeekdaysSelected) {
            resultParts.add("평일")
        } else {
            // 각각 해당하는 평일 날짜를 추가
            weekdays.forEachIndexed { index, isSelected ->
                if (isSelected) {
                    resultParts.add(DAY_NAMES[index])
                }
            }
        }

        // 주말이 모두 포함될 경우 "주말"로 표기
        if (isAllWeekendSelected) {
            resultParts.add("주말")
        } else {
            // 각각 해당하는 주말 날짜를 추가
            weekend.forEachIndexed { index, isSelected ->
                if (isSelected) {
                    resultParts.add(DAY_NAMES[index + 5])
                }
            }
        }

        return resultParts.joinToString(", ")
    }
}
