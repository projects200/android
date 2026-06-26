package com.project200.feature.timer.custom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.domain.model.Step
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBackground
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.header
import com.project200.undabang.feature.timer.R

@Composable
fun CustomTimerScreen(
    title: String,
    steps: List<Step>,
    currentStepIndex: Int,
    remainingTime: Long,
    totalStepTime: Long,
    isRunning: Boolean,
    isRepeatEnabled: Boolean,
    isTimerFinished: Boolean,
    onPlayPauseClick: () -> Unit,
    onEndClick: () -> Unit,
    onRepeatToggle: () -> Unit,
    onStepClick: (Int) -> Unit,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        topBar = {
            UndabangTopBar(
                title = title,
                onNavigationClick = onBackClick,
                actions = {
                    IconButton(onClick = onMenuClick) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = null,
                            tint = Color.Unspecified,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // 상단: 원형 progress + 시간 + 종료/반복/시작 버튼 row
            TimerHeader(
                remainingTime = remainingTime,
                totalStepTime = totalStepTime,
                isRunning = isRunning,
                isRepeatEnabled = isRepeatEnabled,
                isTimerFinished = isTimerFinished,
                onPlayPauseClick = onPlayPauseClick,
                onEndClick = onEndClick,
                onRepeatToggle = onRepeatToggle,
            )

            // 하단: 스텝 목록
            StepList(
                steps = steps,
                currentStepIndex = currentStepIndex,
                onStepClick = onStepClick,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(ColorBackground),
            )
        }
    }
}

@Composable
private fun TimerHeader(
    remainingTime: Long,
    totalStepTime: Long,
    isRunning: Boolean,
    isRepeatEnabled: Boolean,
    isTimerFinished: Boolean,
    onPlayPauseClick: () -> Unit,
    onEndClick: () -> Unit,
    onRepeatToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (totalStepTime > 0) {
            (remainingTime.toFloat() / totalStepTime.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ColorWhite300)
                .padding(top = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(230.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgress(progress = progress)
            Text(
                text = remainingTime.toFormattedTimeAsLong(),
                style = MaterialTheme.typography.header,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = ColorBlack,
            )
        }

        Spacer(Modifier.height(30.dp))

        // 종료 / 반복 / 시작 버튼 row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 45.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 종료 (회색 배경 + 흰 텍스트, 타이머 종료 시 비활성)
            TimerActionButton(
                text = stringResource(R.string.timer_end),
                bgColor = ColorGray200,
                enabled = !isTimerFinished,
                onClick = onEndClick,
                modifier = Modifier.weight(1f),
            )

            // 반복 토글
            Image(
                painter =
                    painterResource(
                        if (isRepeatEnabled) R.drawable.ic_repeat else R.drawable.ic_repeat_off,
                    ),
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRepeatToggle,
                        ),
            )

            // 시작/정지 (실행 중이면 error_red + 정지 텍스트, 아니면 main + 시작)
            TimerActionButton(
                text =
                    stringResource(
                        if (isRunning) R.string.timer_stop else R.string.timer_start,
                    ),
                bgColor = if (isRunning) ColorErrorRed else ColorMain,
                enabled = true,
                onClick = onPlayPauseClick,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TimerActionButton(
    text: String,
    bgColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(45.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(bgColor)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.contentBold,
            color = ColorWhite300,
        )
    }
}

@Composable
private fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // 50ms tick마다 progress가 갱신되므로 별도 tween 애니메이션 없이 즉시 반영 (애니메이션 누적·프레임 드랍 방지)
    // 스텝 전환·종료 시 1f로 돌아갈 때도 부드러운 차오름 없이 바로 표시됨
    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = 20.dp.toPx()
        val half = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(half, half)

        drawArc(
            color = ColorBackground,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = ColorMain,
            startAngle = 270f,
            sweepAngle = -progress * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun StepList(
    steps: List<Step>,
    currentStepIndex: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 현재 스텝으로 자동 스크롤
    LaunchedEffect(currentStepIndex) {
        if (steps.isNotEmpty()) {
            listState.animateScrollToItem(currentStepIndex.coerceIn(0, steps.size - 1))
        }
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(steps, key = { it.id }) { step ->
            val index = steps.indexOf(step)
            StepCard(
                step = step,
                highlighted = index == currentStepIndex,
                onClick = { onStepClick(index) },
            )
        }
    }
}

@Composable
private fun StepCard(
    step: Step,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (highlighted) ColorMain else ColorWhite300
    val tint = if (highlighted) ColorWhite300 else ColorBlack
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .shadow(2.dp, RoundedCornerShape(15.dp))
                .clip(RoundedCornerShape(15.dp))
                .background(bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .height(60.dp)
                .padding(start = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = step.name,
            style = MaterialTheme.typography.contentBold,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = step.time.toFormattedTime(),
            style = MaterialTheme.typography.header,
            color = tint,
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CustomTimerScreenPreview() {
    AppTheme {
        CustomTimerScreen(
            title = "운동 루틴",
            steps =
                listOf(
                    Step(id = 1L, order = 0, time = 30, name = "준비"),
                    Step(id = 2L, order = 1, time = 60, name = "스쿼트"),
                    Step(id = 3L, order = 2, time = 90, name = "휴식"),
                ),
            currentStepIndex = 1,
            remainingTime = 45_000L,
            totalStepTime = 60_000L,
            isRunning = true,
            isRepeatEnabled = false,
            isTimerFinished = false,
            onPlayPauseClick = {},
            onEndClick = {},
            onRepeatToggle = {},
            onStepClick = {},
            onMenuClick = {},
            onBackClick = {},
        )
    }
}
