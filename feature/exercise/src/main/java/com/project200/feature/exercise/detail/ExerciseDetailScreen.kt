package com.project200.feature.exercise.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.project200.common.utils.CommonDateTimeFormatters
import com.project200.domain.model.ExerciseRecord
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite100
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.subtext14
import com.project200.presentation.utils.UiState
import com.project200.undabang.feature.exercise.R

@Composable
fun ExerciseDetailScreen(
    state: UiState<ExerciseRecord>,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        topBar = {
            UndabangTopBar(
                title = stringResource(R.string.exercise_detail),
                onNavigationClick = onBackClick,
                actions = {
                    // 우측: 공유 + 메뉴
                    IconButton(onClick = onShareClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = null,
                            tint = Color.Unspecified,
                        )
                    }
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = null,
                            tint = Color.Unspecified,
                        )
                    }
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
            when (state) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Success -> {
                    DetailContent(record = state.data)
                }
                is UiState.Error -> {
                    Text(
                        text = stringResource(R.string.exercise_load_fail),
                        style = MaterialTheme.typography.subtext14,
                        color = ColorGray200,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailContent(
    record: ExerciseRecord,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
    ) {
        // 이미지 슬라이더 (HorizontalPager + 인디케이터)
        val pictures = record.pictures.orEmpty()
        if (pictures.isNotEmpty()) {
            val pagerState = rememberPagerState(pageCount = { pictures.size })
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(ColorBlack),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                ) { page ->
                    AsyncImage(
                        model = pictures[page].url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 페이지 인디케이터 (하단 가운데)
                if (pictures.size > 1) {
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                    ) {
                        repeat(pictures.size) { index ->
                            val color = if (index == pagerState.currentPage) ColorMain else ColorWhite300
                            Box(
                                modifier =
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(color),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 제목 (항상 표시)
        DetailField(
            label = stringResource(R.string.exercise_record_title),
            value = record.title,
        )

        // 운동 종류 (있을 때만)
        if (!record.personalType.isNullOrBlank()) {
            DetailField(
                label = stringResource(R.string.exercise_record_type),
                value = record.personalType.orEmpty(),
            )
        }

        // 운동 시간 (시작/종료)
        val formatter = CommonDateTimeFormatters.YY_MM_DD_HH_MM
        val startStr = record.startedAt.format(formatter)
        val endStr = record.endedAt.format(formatter)
        if (!startStr.isNullOrBlank() || !endStr.isNullOrBlank()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.exercise_record_time),
                style = MaterialTheme.typography.contentBold,
                color = ColorBlack,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = startStr.orEmpty(),
                    style = MaterialTheme.typography.subtext14,
                    color = ColorBlack,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(com.project200.undabang.presentation.R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = ColorGray200,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = endStr.orEmpty(),
                    style = MaterialTheme.typography.subtext14,
                    color = ColorBlack,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 운동 장소 (있을 때만)
        if (!record.location.isNullOrBlank()) {
            DetailField(
                label = stringResource(R.string.exercise_record_location),
                value = record.location.orEmpty(),
            )
        }

        // 운동 내용 (있을 때만)
        if (!record.detail.isNullOrBlank()) {
            DetailField(
                label = stringResource(R.string.exercise_record_desc),
                value = record.detail.orEmpty(),
                minHeight = 120.dp,
            )
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.contentBold,
        color = ColorBlack,
        modifier = modifier.padding(start = 20.dp),
    )
    Spacer(Modifier.height(8.dp))
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ColorWhite100)
                .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.subtext14,
            color = ColorBlack,
            modifier = Modifier.defaultMinSize(minHeight = minHeight),
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ExerciseDetailScreenPreview() {
    AppTheme {
        ExerciseDetailScreen(
            state = UiState.Loading,
            onBackClick = {},
            onShareClick = {},
            onMenuClick = {},
        )
    }
}
