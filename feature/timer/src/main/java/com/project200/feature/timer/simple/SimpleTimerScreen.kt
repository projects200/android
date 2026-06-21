package com.project200.feature.timer.simple

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBackground
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.undabang.feature.timer.R

@Composable
fun SimpleTimerScreen(
    remainingTime: Long,
    totalTime: Long,
    isRunning: Boolean,
    timers: List<SimpleTimer>,
    onPlayPauseClick: () -> Unit,
    onTimerItemClick: (SimpleTimer) -> Unit,
    onTimerEditClick: (SimpleTimer) -> Unit,
    onTimerDeleteClick: (SimpleTimer) -> Unit,
    onAddClick: () -> Unit,
    onSortClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        topBar = {
            UndabangTopBar(
                title = stringResource(R.string.simple_timer),
                onNavigationClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // 상단: 원형 progress + 시간 텍스트 + 재생/정지 버튼
            TimerHeader(
                remainingTime = remainingTime,
                totalTime = totalTime,
                isRunning = isRunning,
                onPlayPauseClick = onPlayPauseClick,
            )

            // 하단: 정렬 버튼 + 2열 그리드
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(ColorBackground)
                        .padding(top = 20.dp),
            ) {
                TimerGrid(
                    timers = timers,
                    onItemClick = onTimerItemClick,
                    onEditClick = onTimerEditClick,
                    onDeleteClick = onTimerDeleteClick,
                    onAddClick = onAddClick,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp).padding(top = 20.dp),
                )

                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 14.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSortClick,
                            )
                            .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.simple_timer_sort),
                        style = MaterialTheme.typography.contentBold,
                        color = ColorBlack,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_sort),
                        contentDescription = null,
                        tint = Color.Unspecified,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerHeader(
    remainingTime: Long,
    totalTime: Long,
    isRunning: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (totalTime > 0) {
            (remainingTime.toFloat() / totalTime.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ColorWhite300)
                .padding(top = 20.dp, bottom = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(230.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgress(progress = progress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = remainingTime.toFormattedTimeAsLong(),
                    style = MaterialTheme.typography.contentBold,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorBlack,
                )
                Spacer(Modifier.height(6.dp))
                Icon(
                    painter =
                        painterResource(
                            if (isRunning) R.drawable.ic_stop else R.drawable.ic_play,
                        ),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPlayPauseClick,
                            ),
                )
            }
        }
    }
}

@Composable
private fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    // 원본 TimerCircularProgressBar 동작 그대로: stroke 20dp, 12시(270°) 시작 + 시계 반대 방향(-) 진행
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 150),
        label = "progress",
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = 20.dp.toPx()
        val half = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(half, half)

        // 배경 링 (main_background)
        drawArc(
            color = ColorBackground,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        // 진행 링 (main color), 시계 반대 방향(-)
        drawArc(
            color = ColorMain,
            startAngle = 270f,
            sweepAngle = -animated * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun TimerGrid(
    timers: List<SimpleTimer>,
    onItemClick: (SimpleTimer) -> Unit,
    onEditClick: (SimpleTimer) -> Unit,
    onDeleteClick: (SimpleTimer) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2열 그리드. 최대 6개 + 마지막에 추가 버튼 (총 6개 미만일 때만)
    val items = timers.map<SimpleTimer, GridItem> { GridItem.Timer(it) }
    val withAdd = if (timers.size < MAX_TIMER_COUNT) items + GridItem.Add else items

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        withAdd.chunked(GRID_COL_COUNT).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1.6f)) {
                        when (item) {
                            is GridItem.Timer ->
                                TimerCell(
                                    timer = item.value,
                                    onClick = { onItemClick(item.value) },
                                    onEditClick = { onEditClick(item.value) },
                                    onDeleteClick = { onDeleteClick(item.value) },
                                )
                            GridItem.Add -> AddCell(onClick = onAddClick)
                        }
                    }
                }
                repeat(GRID_COL_COUNT - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private sealed interface GridItem {
    data class Timer(val value: SimpleTimer) : GridItem

    object Add : GridItem
}

@Composable
private fun TimerCell(
    timer: SimpleTimer,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(ColorWhite300)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Text(
            text = timer.time.toFormattedTime(),
            style = MaterialTheme.typography.contentBold,
            fontSize = 22.sp,
            color = ColorBlack,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = null,
                tint = ColorBlack,
                modifier =
                    Modifier
                        .padding(8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { menuExpanded = true },
                        ),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(com.project200.undabang.presentation.R.string.edit), color = ColorBlack) },
                    onClick = {
                        menuExpanded = false
                        onEditClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(com.project200.undabang.presentation.R.string.delete), color = ColorErrorRed) },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun AddCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(ColorWhite300)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = null,
            tint = ColorBlack,
        )
    }
}

private const val MAX_TIMER_COUNT = 6
private const val GRID_COL_COUNT = 2

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SimpleTimerScreenPreview() {
    AppTheme {
        SimpleTimerScreen(
            remainingTime = 90_000L,
            totalTime = 120_000L,
            isRunning = true,
            timers =
                listOf(
                    SimpleTimer(id = 1L, time = 60),
                    SimpleTimer(id = 2L, time = 120),
                    SimpleTimer(id = 3L, time = 300),
                ),
            onPlayPauseClick = {},
            onTimerItemClick = {},
            onTimerEditClick = {},
            onTimerDeleteClick = {},
            onAddClick = {},
            onSortClick = {},
            onBackClick = {},
        )
    }
}
