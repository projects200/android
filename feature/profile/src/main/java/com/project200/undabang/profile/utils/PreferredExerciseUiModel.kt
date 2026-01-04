package com.project200.undabang.profile.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getString
import com.project200.common.utils.PreferredExerciseDayFormatter
import com.project200.domain.model.PreferredExercise
import com.project200.presentation.utils.SkillLevel
import com.project200.undabang.feature.profile.R

data class PreferredExerciseUiModel(
    val exercise: PreferredExercise,
    var isSelected: Boolean,
    var selectedDays: MutableList<Boolean> = MutableList(7) { false }, // 월~일 선택 상태
    var skillLevel: SkillLevel? = null // 숙련도ㅎ
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

    fun toModel(): PreferredExercise {
        return PreferredExercise(
            preferredExerciseId = this.exercise.preferredExerciseId,
            exerciseTypeId = this.exercise.exerciseTypeId,
            name = this.exercise.name,
            skillLevel = this.skillLevel?.name ?: "",
            daysOfWeek = this.selectedDays,
            imageUrl = this.exercise.imageUrl
        )
    }
}

sealed class CompletionState {
    object Idle : CompletionState()
    object Loading : CompletionState()
    object Success : CompletionState()
    object NoChanges : CompletionState()
    object NoneSelected : CompletionState()
    object IncompleteSelection : CompletionState()
    data class Error(val message: String) : CompletionState()
}