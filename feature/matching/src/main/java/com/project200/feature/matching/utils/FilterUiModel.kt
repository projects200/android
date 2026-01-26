package com.project200.feature.matching.utils

import com.project200.domain.model.AgeGroup
import com.project200.domain.model.DayOfWeek
import com.project200.domain.model.ExerciseScore
import com.project200.domain.model.Gender
import com.project200.domain.model.SkillLevel
import com.project200.undabang.feature.matching.R

sealed class FilterListItem {
    data object ClearButton : FilterListItem() // 초기화 버튼

    data class FilterItem(val type: MatchingFilterType) : FilterListItem() // 필터 버튼
}

enum class MatchingFilterType(
    val labelResId: Int,
    val isMultiSelect: Boolean = false,
) {
    GENDER(R.string.filter_gender), // 성별
    AGE(R.string.filter_age), // 나이
    DAY(R.string.filter_day, true), // 요일
    SKILL(R.string.filter_skill), // 숙련도
    SCORE(R.string.filter_score), // 점수
}

data class FilterState(
    val gender: Gender? = null,
    val ageGroup: AgeGroup? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val skillLevel: SkillLevel? = null,
    val exerciseScore: ExerciseScore? = null,
)

data class FilterOptionUiModel(
    val labelResId: Int,
    val isSelected: Boolean,
    val originalData: Any?, // 선택된 Enum 객체 (Gender, AgeGroup 등)
)
