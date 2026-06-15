package com.project200.feature.exercise.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.project200.common.utils.CommonDateTimeFormatters.HH_MM_KR
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_MM_DD_KR
import com.project200.feature.exercise.utils.ScoreGuidanceState
import com.project200.feature.exercise.utils.TimeSelectionState
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.input.TextFieldVariant
import com.project200.presentation.compose.components.input.UndabangTextField
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorGray100
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite100
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.subtext14
import com.project200.undabang.feature.exercise.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun ExerciseFormScreen(
    isEditMode: Boolean,
    title: String,
    selectedType: String,
    directTypeInput: String,
    showDirectTypeInput: Boolean,
    startTime: LocalDateTime?,
    endTime: LocalDateTime?,
    timeSelectionState: TimeSelectionState,
    selectedLocation: String,
    detail: String,
    scoreGuidanceState: ScoreGuidanceState,
    imageItems: List<ExerciseImageListItem>,
    isLoading: Boolean,
    onTitleChange: (String) -> Unit,
    onTypeSelectClick: () -> Unit,
    onDirectTypeInputChange: (String) -> Unit,
    onLocationSelectClick: () -> Unit,
    onTimeButtonClick: (TimeSelectionState) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeConfirmed: (hour: Int, minute: Int) -> Unit,
    onDetailChange: (String) -> Unit,
    onAddImageClick: () -> Unit,
    onDeleteImageClick: (ExerciseImageListItem) -> Unit,
    onCompleteClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        topBar = {
            UndabangTopBar(
                title =
                    stringResource(
                        if (isEditMode) R.string.edit_exercise else R.string.record_exercise,
                    ),
                onNavigationClick = onBackClick,
                actions = {
                    PrimaryButton(
                        text = stringResource(PresentationR.string.complete),
                        onClick = onCompleteClick,
                        isCompact = true,
                        enabled = !isLoading,
                        modifier = Modifier.padding(end = 20.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
            ) {
                // 운동 제목
                SectionLabel(text = stringResource(R.string.exercise_record_title))
                UndabangTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    hint = stringResource(R.string.exercise_record_title_hint),
                    variant = TextFieldVariant.FilledSmall,
                    maxLength = 255,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp),
                )

                // 운동 종류 (BottomSheet 호출)
                SectionLabel(
                    text = stringResource(R.string.exercise_record_type),
                    topPadding = 24.dp,
                )
                SelectorRow(
                    label = selectedType.ifEmpty { stringResource(R.string.exercise_record_type_select_hint) },
                    hintColor = selectedType.isEmpty(),
                    onClick = onTypeSelectClick,
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp),
                )
                // 직접 입력 선택 시 EditText 노출
                if (showDirectTypeInput) {
                    UndabangTextField(
                        value = directTypeInput,
                        onValueChange = onDirectTypeInputChange,
                        hint = stringResource(R.string.exercise_record_type_hint),
                        variant = TextFieldVariant.FilledSmall,
                        maxLength = 50,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 8.dp),
                    )
                }

                // 시작/종료 시간 + 점수 안내
                SectionLabel(
                    text = stringResource(R.string.exercise_record_time),
                    topPadding = 24.dp,
                )
                // 점수 안내는 시간 라벨 바로 아래에 배치 (원본 디자인 일치)
                ScoreGuidanceText(state = scoreGuidanceState)
                TimeSelectionRow(
                    startTime = startTime,
                    endTime = endTime,
                    selection = timeSelectionState,
                    onClick = onTimeButtonClick,
                )

                // 선택 상태에 따라 인라인 캘린더/시간 입력기 노출
                when (timeSelectionState) {
                    TimeSelectionState.START_DATE,
                    TimeSelectionState.END_DATE,
                    -> {
                        InlineDatePicker(
                            initialDate =
                                if (timeSelectionState == TimeSelectionState.START_DATE) {
                                    startTime?.toLocalDate() ?: LocalDate.now()
                                } else {
                                    endTime?.toLocalDate() ?: LocalDate.now()
                                },
                            onDateSelected = onDateSelected,
                        )
                    }
                    TimeSelectionState.START_TIME,
                    TimeSelectionState.END_TIME,
                    -> {
                        InlineTimePicker(
                            initialTime =
                                if (timeSelectionState == TimeSelectionState.START_TIME) startTime else endTime,
                            onConfirm = onTimeConfirmed,
                        )
                    }
                    TimeSelectionState.NONE -> Unit
                }

                // 운동 장소
                SectionLabel(
                    text = stringResource(R.string.exercise_record_location),
                    topPadding = 24.dp,
                )
                SelectorRow(
                    label = selectedLocation.ifEmpty { stringResource(R.string.exercise_record_location_hint) },
                    hintColor = selectedLocation.isEmpty(),
                    onClick = onLocationSelectClick,
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp),
                )

                // 운동 내용
                SectionLabel(
                    text = stringResource(R.string.exercise_record_desc),
                    topPadding = 24.dp,
                )
                UndabangTextField(
                    value = detail,
                    onValueChange = onDetailChange,
                    hint = "",
                    variant = TextFieldVariant.FilledLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp)
                            .height(160.dp),
                )

                // 이미지 그리드
                SectionLabel(
                    text = stringResource(R.string.exercise_record_add_image),
                    topPadding = 24.dp,
                )
                ImageGrid(
                    items = imageItems,
                    onAddClick = onAddImageClick,
                    onDeleteClick = onDeleteImageClick,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
                )
            }

            if (isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.contentBold,
        color = ColorBlack,
        modifier = modifier.padding(start = 20.dp, top = topPadding),
    )
}

@Composable
private fun SelectorRow(
    label: String,
    hintColor: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 원본은 focusable=false EditText + bg_solid_corner. 드롭다운 아이콘 없음
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ColorWhite100)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.subtext14,
            color = if (hintColor) ColorGray200 else ColorBlack,
        )
    }
}

@Composable
private fun TimeSelectionRow(
    startTime: LocalDateTime?,
    endTime: LocalDateTime?,
    selection: TimeSelectionState,
    onClick: (TimeSelectionState) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 원본: 좌측(시작 날짜 위, 시작 시간 아래) + 가운데 화살표 + 우측(종료 날짜 위, 종료 시간 아래)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimeButton(
                text = startTime?.format(YYYY_MM_DD_KR) ?: "-",
                selected = selection == TimeSelectionState.START_DATE,
                onClick = { onClick(TimeSelectionState.START_DATE) },
            )
            TimeButton(
                text = startTime?.format(HH_MM_KR) ?: "-",
                selected = selection == TimeSelectionState.START_TIME,
                onClick = { onClick(TimeSelectionState.START_TIME) },
            )
        }
        Icon(
            painter = painterResource(PresentationR.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ColorGray200,
            modifier = Modifier.padding(horizontal = 20.dp).size(30.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimeButton(
                text = endTime?.format(YYYY_MM_DD_KR) ?: "-",
                selected = selection == TimeSelectionState.END_DATE,
                onClick = { onClick(TimeSelectionState.END_DATE) },
            )
            TimeButton(
                text = endTime?.format(HH_MM_KR) ?: "-",
                selected = selection == TimeSelectionState.END_TIME,
                onClick = { onClick(TimeSelectionState.END_TIME) },
            )
        }
    }
}

@Composable
private fun TimeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 원본 bg_time_indicator (선택 시) — 선택 안 됐을 땐 배경 없음, 선택되면 배경/테두리 강조
    val borderColor = if (selected) ColorMain else Color.Transparent
    val bgColor = if (selected) ColorWhite100 else Color.Transparent
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.subtext14,
            color = ColorBlack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineDatePicker(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialMillis = remember(initialDate) { initialDate.toUtcMillis() }
    val nowMillis = remember { LocalDate.now().toUtcMillis() }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates =
                object : SelectableDates {
                    // 미래 날짜는 선택 불가 (운동 기록은 과거~현재만 허용)
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= nowMillis
                },
        )
    LaunchedEffect(state.selectedDateMillis) {
        state.selectedDateMillis?.let { millis ->
            val date =
                Instant.ofEpochMilli(millis)
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDate()
            if (date != initialDate) {
                onDateSelected(date)
            }
        }
    }
    DatePicker(
        state = state,
        title = null,
        headline = null,
        showModeToggle = false,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun InlineTimePicker(
    initialTime: LocalDateTime?,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 원본 time_selector_ll: height 140dp, bg_time_selector, 시계 35dp + 큰 40sp 시/분 입력 + 확인 버튼
    var hour by remember(initialTime) {
        mutableStateOf(initialTime?.hour?.toString()?.padStart(2, '0') ?: "")
    }
    var minute by remember(initialTime) {
        mutableStateOf(initialTime?.minute?.toString()?.padStart(2, '0') ?: "")
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ColorWhite100)
                .height(140.dp)
                .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(35.dp),
        )
        Spacer(Modifier.width(20.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = hour,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(2)
                val parsed = digits.toIntOrNull()
                if (digits.isEmpty() || (parsed != null && parsed in 0..23)) {
                    hour = digits
                }
            },
            textStyle =
                androidx.compose.ui.text.TextStyle(
                    fontSize = 40.sp,
                    color = ColorBlack,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            singleLine = true,
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ":",
            fontSize = 40.sp,
            color = ColorBlack,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        androidx.compose.foundation.text.BasicTextField(
            value = minute,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(2)
                val parsed = digits.toIntOrNull()
                if (digits.isEmpty() || (parsed != null && parsed in 0..59)) {
                    minute = digits
                }
            },
            textStyle =
                androidx.compose.ui.text.TextStyle(
                    fontSize = 40.sp,
                    color = ColorBlack,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            singleLine = true,
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(20.dp))
        PrimaryButton(
            text = stringResource(PresentationR.string.confirm),
            onClick = {
                val h = hour.toIntOrNull() ?: return@PrimaryButton
                val m = minute.toIntOrNull() ?: return@PrimaryButton
                onConfirm(h, m)
            },
            isCompact = true,
        )
    }
}

@Composable
private fun ScoreGuidanceText(
    state: ScoreGuidanceState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ScoreGuidanceState.Hidden -> Unit
        is ScoreGuidanceState.Warning -> {
            Text(
                text = stringResource(state.messageId),
                style = MaterialTheme.typography.subtext14,
                color = ColorErrorRed,
                modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        is ScoreGuidanceState.PointsAvailable -> {
            Text(
                text = "${state.points}점을 획득할 수 있어요",
                style = MaterialTheme.typography.subtext14,
                color = ColorMain,
                modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ImageGrid(
    items: List<ExerciseImageListItem>,
    onAddClick: () -> Unit,
    onDeleteClick: (ExerciseImageListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // verticalScroll Column 안에 LazyVerticalGrid를 못 두므로 chunked Row로 직접 그림
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        ImageCell(
                            item = item,
                            onAddClick = onAddClick,
                            onDeleteClick = { onDeleteClick(item) },
                        )
                    }
                }
                // 빈 칸 채우기
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImageCell(
    item: ExerciseImageListItem,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is ExerciseImageListItem.AddButtonItem -> {
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorWhite100)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAddClick,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    tint = ColorGray200,
                )
            }
        }
        is ExerciseImageListItem.NewImageItem, is ExerciseImageListItem.ExistingImageItem -> {
            Box(modifier = modifier.fillMaxSize()) {
                val model =
                    when (item) {
                        is ExerciseImageListItem.NewImageItem -> item.uri
                        is ExerciseImageListItem.ExistingImageItem -> item.url
                        else -> null
                    }
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ColorGray100),
                )
                Icon(
                    painter = painterResource(PresentationR.drawable.ic_close),
                    contentDescription = null,
                    tint = ColorWhite300,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(2.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDeleteClick,
                            ),
                )
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun ExerciseFormScreenPreview() {
    AppTheme {
        ExerciseFormScreen(
            isEditMode = false,
            title = "오늘 운동",
            selectedType = "헬스",
            directTypeInput = "",
            showDirectTypeInput = false,
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now(),
            timeSelectionState = TimeSelectionState.NONE,
            selectedLocation = "헬스장",
            detail = "스쿼트 5세트",
            scoreGuidanceState = ScoreGuidanceState.Hidden,
            imageItems = listOf(ExerciseImageListItem.AddButtonItem),
            isLoading = false,
            onTitleChange = {},
            onTypeSelectClick = {},
            onDirectTypeInputChange = {},
            onLocationSelectClick = {},
            onTimeButtonClick = {},
            onDateSelected = {},
            onTimeConfirmed = { _, _ -> },
            onDetailChange = {},
            onAddImageClick = {},
            onDeleteImageClick = {},
            onCompleteClick = {},
            onBackClick = {},
        )
    }
}
