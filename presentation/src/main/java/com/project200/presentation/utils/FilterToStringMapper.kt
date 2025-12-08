package com.project200.presentation.utils

import com.project200.domain.model.*
import com.project200.undabang.presentation.R

val Gender.labelResId: Int
    get() = when (this) {
        Gender.MALE -> R.string.gender_male
        Gender.FEMALE -> R.string.gender_female
    }

val AgeGroup.labelResId: Int
    get() = when (this) {
        AgeGroup.TEEN -> R.string.age_10
        AgeGroup.TWENTIES -> R.string.age_20
        AgeGroup.THIRTIES -> R.string.age_30
        AgeGroup.FORTIES -> R.string.age_40
        AgeGroup.FIFTIES -> R.string.age_50
        AgeGroup.SIXTIES_PLUS -> R.string.age_60
    }

val DayOfWeek.labelResId: Int
    get() = when (this) {
        DayOfWeek.MONDAY -> R.string.mon
        DayOfWeek.TUESDAY -> R.string.tue
        DayOfWeek.WEDNESDAY -> R.string.wed
        DayOfWeek.THURSDAY -> R.string.thu
        DayOfWeek.FRIDAY -> R.string.fri
        DayOfWeek.SATURDAY -> R.string.sat
        DayOfWeek.SUNDAY -> R.string.sun
    }

val SkillLevel.labelResId: Int
    get() = when (this) {
        SkillLevel.NOVICE -> R.string.novice
        SkillLevel.BEGINNER -> R.string.beginner
        SkillLevel.INTERMEDIATE -> R.string.intermediate
        SkillLevel.ADVANCED -> R.string.advanced
        SkillLevel.EXPERT -> R.string.expert
        SkillLevel.PROFESSIONAL -> R.string.professional
    }

val ExerciseScore.labelResId: Int
    get() = when (this) {
        ExerciseScore.OVER_20 -> R.string.score_over_20
        ExerciseScore.OVER_40 -> R.string.score_over_40
        ExerciseScore.OVER_60 -> R.string.score_over_60
        ExerciseScore.OVER_80 -> R.string.score_over_80
    }