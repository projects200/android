package com.project200.undabang.profile.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getString
import com.project200.common.utils.PreferredExerciseDayFormatter
import com.project200.domain.model.PreferredExercise
import com.project200.undabang.feature.profile.R

data class PreferredExerciseUiModel(
    val exercise: PreferredExercise,
    var isSelected: Boolean,
    var selectedDays: MutableList<Boolean> = MutableList(7) { false }, // 월~일 선택 상태
    var skillLevel: SkillLevel? = null // 숙련도
) {
    fun getExerciseInfo(context: Context, formatter: PreferredExerciseDayFormatter): String {
        val formattedDays = formatter.formatDaysOfWeek(selectedDays)

        val daysText = if (formattedDays.isNotEmpty()) {
            formattedDays
        } else {
            getString(context, R.string.preferred_exercise_day_not_selected)
        }

        val skillText = getString(context, skillLevel?.resId ?: R.string.preferred_exercise_skill_not_selected)

        return "$daysText ・ $skillText"
    }
}

enum class SkillLevel(@StringRes val resId: Int) {
    NOVICE(R.string.skill_novice),
    BEGINNER(R.string.skill_beginner),
    INTERMEDIATE(R.string.skill_intermediate),
    ADVANCED(R.string.skill_advanced),
    EXPERT(R.string.skill_expert),
    PROFESSIONAL(R.string.skill_professional)
}