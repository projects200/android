package com.project200.feature.matching.utils

import com.project200.domain.model.AgeGroup
import com.project200.domain.model.DayOfWeek
import com.project200.domain.model.ExerciseScore
import com.project200.domain.model.Gender
import com.project200.domain.model.SkillLevel
import com.project200.feature.matching.map.MatchingMapViewModel
import com.project200.presentation.utils.labelResId

object FilterUiMapper {
    fun mapToUiModels(
        type: MatchingFilterType,
        currentState: FilterState,
    ): List<FilterOptionUiModel> {
        return when (type) {
            MatchingFilterType.GENDER ->
                Gender.entries.map { gender ->
                    FilterOptionUiModel(
                        labelResId = gender.labelResId,
                        isSelected = currentState.gender == gender,
                        originalData = gender,
                    )
                }
            MatchingFilterType.AGE ->
                AgeGroup.entries.map { age ->
                    FilterOptionUiModel(
                        labelResId = age.labelResId,
                        isSelected = currentState.ageGroup == age,
                        originalData = age,
                    )
                }
            MatchingFilterType.DAY -> {
                val list = mutableListOf<FilterOptionUiModel>()

                // 전체 옵션
                list.add(
                    FilterOptionUiModel(
                        labelResId = com.project200.undabang.presentation.R.string.filter_all,
                        isSelected = currentState.days.isEmpty(),
                        originalData = null
                    )
                )

                // 요일 옵션
                list.addAll(DayOfWeek.entries.map { day ->
                    FilterOptionUiModel(
                        labelResId = day.labelResId,
                        isSelected = currentState.days.contains(day),
                        originalData = day
                    )
                })

                list
            }
            MatchingFilterType.SKILL ->
                SkillLevel.entries.map { skill ->
                    FilterOptionUiModel(
                        labelResId = skill.labelResId,
                        isSelected = currentState.skillLevel == skill,
                        originalData = skill,
                    )
                }
            MatchingFilterType.SCORE ->
                ExerciseScore.entries.map { score ->
                    FilterOptionUiModel(
                        labelResId = score.labelResId,
                        isSelected = currentState.exerciseScore == score,
                        originalData = score,
                    )
                }
        }
    }
}
