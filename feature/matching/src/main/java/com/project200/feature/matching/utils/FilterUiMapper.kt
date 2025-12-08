package com.project200.feature.matching.utils

import com.project200.domain.model.*
import com.project200.feature.matching.utils.*
import com.project200.presentation.utils.labelResId // 기존에 만드신 확장 프로퍼티 import

object FilterUiMapper {

    fun mapToUiModels(
        type: MatchingFilterType,
        currentState: FilterState
    ): List<FilterOptionUiModel> {
        return when (type) {
            MatchingFilterType.GENDER -> Gender.entries.map { gender ->
                FilterOptionUiModel(
                    labelResId = gender.labelResId,
                    isSelected = currentState.gender == gender,
                    originalData = gender
                )
            }
            MatchingFilterType.AGE -> AgeGroup.entries.map { age ->
                FilterOptionUiModel(
                    labelResId = age.labelResId,
                    isSelected = currentState.ageGroup == age,
                    originalData = age
                )
            }
            MatchingFilterType.DAY -> DayOfWeek.entries.map { day ->
                FilterOptionUiModel(
                    labelResId = day.labelResId,
                    isSelected = currentState.days == day,
                    originalData = day
                )
            }
            MatchingFilterType.SKILL -> SkillLevel.entries.map { skill ->
                FilterOptionUiModel(
                    labelResId = skill.labelResId,
                    isSelected = currentState.skillLevel == skill,
                    originalData = skill
                )
            }
            MatchingFilterType.SCORE -> ExerciseScore.entries.map { score ->
                FilterOptionUiModel(
                    labelResId = score.labelResId,
                    isSelected = currentState.exerciseScore == score,
                    originalData = score
                )
            }
        }
    }
}