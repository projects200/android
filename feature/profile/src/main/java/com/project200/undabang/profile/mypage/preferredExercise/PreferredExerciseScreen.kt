package com.project200.undabang.profile.mypage.preferredExercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.project200.common.utils.PreferredExerciseDayFormatter
import com.project200.domain.model.PreferredExercise
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorGrayTrans
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite100
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.header
import com.project200.presentation.compose.theme.subtext12
import com.project200.presentation.compose.theme.subtext14
import com.project200.presentation.utils.SkillLevel
import com.project200.undabang.feature.profile.R
import com.project200.undabang.profile.utils.PreferredExerciseUiModel
import com.project200.undabang.presentation.R as PresentationR

enum class PreferredExerciseStep {
    TYPE,
    DETAIL,
}

@Composable
fun PreferredExerciseScreen(
    step: PreferredExerciseStep,
    nickname: String,
    uiModels: List<PreferredExerciseUiModel>,
    selectedUiModels: List<PreferredExerciseUiModel>,
    isLoading: Boolean,
    formatter: PreferredExerciseDayFormatter,
    onBackClick: () -> Unit,
    onNextOrCompleteClick: () -> Unit,
    onTypeClick: (PreferredExercise) -> Unit,
    onTypeLimitReached: () -> Unit,
    onDayClick: (exerciseTypeId: Long, dayIndex: Int) -> Unit,
    onSkillClick: (exerciseTypeId: Long, skill: SkillLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(if (step == PreferredExerciseStep.DETAIL) ColorWhite100 else ColorWhite300),
        ) {
            UndabangTopBar(
                title = stringResource(R.string.preferred_exercise),
                onNavigationClick = onBackClick,
                actions = {
                    PrimaryButton(
                        text =
                            stringResource(
                                if (step == PreferredExerciseStep.TYPE) {
                                    PresentationR.string.next
                                } else {
                                    PresentationR.string.complete
                                },
                            ),
                        onClick = onNextOrCompleteClick,
                        enabled = !isLoading,
                        isCompact = true,
                        modifier = Modifier.padding(end = 20.dp),
                    )
                },
            )

            when (step) {
                PreferredExerciseStep.TYPE ->
                    PreferredExerciseTypeContent(
                        nickname = nickname,
                        uiModels = uiModels,
                        onTypeClick = onTypeClick,
                        onLimitReached = onTypeLimitReached,
                    )
                PreferredExerciseStep.DETAIL ->
                    PreferredExerciseDetailContent(
                        items = selectedUiModels,
                        formatter = formatter,
                        onDayClick = onDayClick,
                        onSkillClick = onSkillClick,
                    )
            }
        }

        if (isLoading) {
            LoadingOverlay()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferredExerciseTypeContent(
    nickname: String,
    uiModels: List<PreferredExerciseUiModel>,
    onTypeClick: (PreferredExercise) -> Unit,
    onLimitReached: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCount = uiModels.count { it.isSelected }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(44.dp))
        Text(
            text = stringResource(R.string.preferred_exercise_type_title, nickname),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
        )
        Text(
            text = stringResource(R.string.preferred_exercise_type_max_cnt),
            style = MaterialTheme.typography.subtext14,
            color = ColorGray200,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            uiModels.forEach { uiModel ->
                PreferredExerciseTypeChip(
                    uiModel = uiModel,
                    onClick = {
                        if (!uiModel.isSelected && selectedCount >= MAX_SELECTION) {
                            onLimitReached()
                        } else {
                            onTypeClick(uiModel.exercise)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PreferredExerciseTypeChip(
    uiModel: PreferredExerciseUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (uiModel.isSelected) ColorMain else ColorGray300
    val textColor = if (uiModel.isSelected) ColorWhite300 else ColorBlack

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50.dp))
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiModel.isSelected) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_preferred_exercise_type_check),
                contentDescription = null,
                modifier = Modifier.size(width = 11.dp, height = 9.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = uiModel.exercise.name,
            color = textColor,
            fontSize = 14.sp,
            style = MaterialTheme.typography.contentBold,
        )
    }
}

@Composable
private fun PreferredExerciseDetailContent(
    items: List<PreferredExerciseUiModel>,
    formatter: PreferredExerciseDayFormatter,
    onDayClick: (exerciseTypeId: Long, dayIndex: Int) -> Unit,
    onSkillClick: (exerciseTypeId: Long, skill: SkillLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorWhite100),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = items, key = { it.exercise.exerciseTypeId }) { uiModel ->
            PreferredExerciseDetailCard(
                uiModel = uiModel,
                formatter = formatter,
                onDayClick = { dayIndex -> onDayClick(uiModel.exercise.exerciseTypeId, dayIndex) },
                onSkillClick = { skill -> onSkillClick(uiModel.exercise.exerciseTypeId, skill) },
            )
        }
    }
}

@Composable
private fun PreferredExerciseDetailCard(
    uiModel: PreferredExerciseUiModel,
    formatter: PreferredExerciseDayFormatter,
    onDayClick: (Int) -> Unit,
    onSkillClick: (SkillLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(15.dp), clip = false)
                .clip(RoundedCornerShape(15.dp))
                .background(ColorWhite300)
                .border(width = 1.dp, color = ColorGray300, shape = RoundedCornerShape(15.dp))
                .padding(vertical = 15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier =
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape),
            ) {
                AsyncImage(
                    model = uiModel.exercise.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(
                    text = uiModel.exercise.name,
                    style = MaterialTheme.typography.header,
                    color = ColorBlack,
                )
                Text(
                    text = uiModel.getExerciseInfo(context, formatter),
                    style = MaterialTheme.typography.subtext12,
                    color = ColorGray200,
                )
            }
        }

        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = ColorGray300, thickness = 1.dp)

        Text(
            text = stringResource(R.string.preferred_exercise_detail_days_title),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
            modifier = Modifier.padding(12.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            DAY_LABEL_RES_IDS.forEachIndexed { index, resId ->
                DayOrSkillButton(
                    text = stringResource(resId),
                    selected = uiModel.selectedDays[index],
                    onClick = { onDayClick(index) },
                    modifier = Modifier.weight(1f).padding(5.dp).height(40.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.preferred_exercise_detail_days_skill_level),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
            modifier = Modifier.padding(12.dp),
        )

        Column(modifier = Modifier.padding(horizontal = 7.dp)) {
            SkillLevel.entries.chunked(3).forEach { rowSkills ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    rowSkills.forEach { skill ->
                        DayOrSkillButton(
                            text = stringResource(skill.resId),
                            selected = uiModel.skillLevel == skill,
                            onClick = { onSkillClick(skill) },
                            modifier = Modifier.weight(1f).padding(5.dp).height(36.dp),
                        )
                    }
                    // 마지막 row가 3개 미만이면 빈 칸 채우기
                    repeat(3 - rowSkills.size) {
                        Spacer(modifier = Modifier.weight(1f).padding(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayOrSkillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) ColorMain else ColorGray300
    val textColor = if (selected) ColorWhite300 else ColorBlack

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            style = MaterialTheme.typography.contentBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorGrayTrans)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = ColorMain)
    }
}

private const val MAX_SELECTION = 5

private val DAY_LABEL_RES_IDS =
    intArrayOf(
        PresentationR.string.mon,
        PresentationR.string.tue,
        PresentationR.string.wed,
        PresentationR.string.thu,
        PresentationR.string.fri,
        PresentationR.string.sat,
        PresentationR.string.sun,
    )

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PreferredExerciseScreenTypePreview() {
    AppTheme {
        PreferredExerciseScreen(
            step = PreferredExerciseStep.TYPE,
            nickname = "운다방",
            uiModels =
                listOf(
                    sampleUiModel(1L, "축구", true),
                    sampleUiModel(2L, "농구", false),
                    sampleUiModel(3L, "테니스", true),
                    sampleUiModel(4L, "수영", false),
                    sampleUiModel(5L, "러닝", false),
                ),
            selectedUiModels = emptyList(),
            isLoading = false,
            formatter = PreferredExerciseDayFormatter(androidx.compose.ui.platform.LocalContext.current),
            onBackClick = {},
            onNextOrCompleteClick = {},
            onTypeClick = {},
            onTypeLimitReached = {},
            onDayClick = { _, _ -> },
            onSkillClick = { _, _ -> },
        )
    }
}

private fun sampleUiModel(
    id: Long,
    name: String,
    isSelected: Boolean,
): PreferredExerciseUiModel =
    PreferredExerciseUiModel(
        exercise =
            PreferredExercise(
                preferredExerciseId = id,
                exerciseTypeId = id,
                name = name,
                skillLevel = "",
                daysOfWeek = MutableList(7) { false },
                imageUrl = null,
            ),
        isSelected = isSelected,
    )
