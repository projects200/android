package com.project200.feature.matching.map.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.project200.feature.matching.utils.FilterState
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite100
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.subtext14
import com.project200.presentation.utils.labelResId
import com.project200.undabang.feature.matching.R

private val ChipShape = RoundedCornerShape(40.dp)

/**
 * 매칭 지도 상단 필터 칩 줄.
 *
 * 맨 앞에 초기화 칩, 그 뒤로 [MatchingFilterType] 별 필터 칩을 [LazyRow] 로 나열한다.
 * 칩 클릭/초기화는 콜백으로 위임하고, 선택 여부 표시는 [filterState] 로 그린다.
 * (기존 RecyclerView 필터 어댑터를 Compose 로 옮긴 것)
 */
@Composable
fun MatchingFilterRow(
    filterState: FilterState,
    onFilterClick: (MatchingFilterType) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding =
            PaddingValues(
                start = dimensionResource(com.project200.undabang.presentation.R.dimen.base_horizontal_margin),
                end = 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ClearChip(onClick = onClearClick) }
        items(MatchingFilterType.entries) { type ->
            FilterChip(
                text = filterChipLabel(type, filterState),
                selected = isFilterSelected(type, filterState),
                onClick = { onFilterClick(type) },
            )
        }
    }
}

/** 초기화 칩: 항상 흰 배경 + 회색 테두리. */
@Composable
private fun ClearChip(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .clip(ChipShape)
                .background(ColorWhite100, ChipShape)
                .border(1.dp, ColorGray200, ChipShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.filter_clear),
            style = MaterialTheme.typography.subtext14,
            color = ColorBlack,
            modifier = Modifier.padding(end = 4.dp),
        )
        Image(
            painter = painterResource(R.drawable.ic_filter_clear),
            contentDescription = null,
        )
    }
}

/** 필터 칩: 선택 시 main 배경 + 흰 텍스트, 미선택 시 흰 배경 + 회색 테두리 + 검정 텍스트. */
@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) ColorWhite300 else ColorBlack
    Row(
        modifier =
            Modifier
                .clip(ChipShape)
                .background(if (selected) ColorMain else ColorWhite100, ChipShape)
                .then(if (selected) Modifier else Modifier.border(1.dp, ColorGray200, ChipShape))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.subtext14,
            color = contentColor,
            modifier = Modifier.padding(end = 4.dp),
        )
        Icon(
            painter = painterResource(com.project200.undabang.presentation.R.drawable.ic_arrow_down),
            contentDescription = null,
            tint = contentColor,
        )
    }
}

/**
 * 칩에 표시할 라벨.
 * - 종목: 선택된 종목명(문자열)
 * - 다중선택(요일): 항상 기본 라벨
 * - 그 외: 선택된 옵션 라벨(있으면), 없으면 기본 라벨
 */
@Composable
private fun filterChipLabel(
    type: MatchingFilterType,
    state: FilterState,
): String {
    return when {
        type == MatchingFilterType.EXERCISE_TYPE && state.selectedExerciseType != null -> {
            state.selectedExerciseType.name
        }
        type.isMultiSelect -> stringResource(type.labelResId)
        else -> stringResource(selectedOptionLabelResId(type, state) ?: type.labelResId)
    }
}

/** 단일선택 필터에서 현재 선택된 옵션의 라벨 resId (없으면 null). */
private fun selectedOptionLabelResId(
    type: MatchingFilterType,
    state: FilterState,
): Int? {
    return when (type) {
        MatchingFilterType.GENDER -> state.gender?.labelResId
        MatchingFilterType.AGE -> state.ageGroup?.labelResId
        MatchingFilterType.SKILL -> state.skillLevel?.labelResId
        MatchingFilterType.SCORE -> state.exerciseScore?.labelResId
        else -> null
    }
}

/** 필터가 선택된 상태인지. */
private fun isFilterSelected(
    type: MatchingFilterType,
    state: FilterState,
): Boolean {
    return when (type) {
        MatchingFilterType.GENDER -> state.gender != null
        MatchingFilterType.AGE -> state.ageGroup != null
        MatchingFilterType.EXERCISE_TYPE -> state.selectedExerciseType != null
        MatchingFilterType.DAY -> state.days.isNotEmpty()
        MatchingFilterType.SKILL -> state.skillLevel != null
        MatchingFilterType.SCORE -> state.exerciseScore != null
    }
}
