package com.project200.undabang.profile.utils

import com.project200.domain.model.PreferredExercise

data class PreferredExerciseUiModel(
    val exercise: PreferredExercise,
    val isSelected: Boolean
)