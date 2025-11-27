package com.project200.undabang.profile.utils

import com.project200.domain.model.PreferredExercise

data class PreferredExerciseUiModel(
    val exercise: PreferredExercise,
    var isSelected: Boolean,
    var selectedDays: MutableList<Boolean> = MutableList(7) { false }, // 월~일 선택 상태
    var skillLevel: SkillLevel? = null // 숙련도
) {
    fun getExerciseInfo(): String {
        val formattedDays = PreferredExerciseDayFormatter.formatDaysOfWeek(selectedDays)

        val daysText = if (formattedDays.isNotEmpty()) {
            formattedDays
        } else {
            "요일 미선택"
        }

        val skillText = skillLevel?.displayName ?: "숙련도 미선택"

        return "$daysText ・ $skillText"
    }
}

enum class SkillLevel(val displayName: String) {
    NOVICE("입문"),
    BEGINNER("초급"),
    INTERMEDIATE("중급"),
    ADVANCED("고급"),
    EXPERT("숙련"),
    PROFESSIONAL("선출")
}