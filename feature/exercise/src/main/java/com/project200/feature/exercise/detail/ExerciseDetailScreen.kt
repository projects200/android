package com.project200.feature.exercise.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.project200.common.utils.CommonDateTimeFormatters
import com.project200.domain.model.ExerciseRecord
import com.project200.presentation.compose.components.feedback.UndabangAlertDialog
import com.project200.presentation.compose.components.feedback.UndabangNoticeBar
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite100
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.presentation.compose.theme.subtext14
import com.project200.presentation.utils.UiState
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.presentation.R as PresentationR

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExerciseDetailScreen(
    state: UiState<ExerciseRecord>,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    canShowBodyRetry: Boolean = false,
    remoteDeleted: Boolean = false,
    onRetryClick: () -> Unit = {},
    onDiscardRemoteClick: () -> Unit = {},
) {
    val isSuccess = state is UiState.Success
    // 확인을 연 순간의 계정·revision에 묶인 콜백을 보존합니다
    var confirmDiscard by remember(remoteDeleted, isSuccess) { mutableStateOf<(() -> Unit)?>(null) }
    if (isSuccess && remoteDeleted && confirmDiscard != null) {
        UndabangAlertDialog(
            title = stringResource(R.string.exercise_remote_discard_title),
            message = stringResource(R.string.exercise_remote_discard_message),
            confirmText = stringResource(PresentationR.string.delete),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                val action = confirmDiscard
                confirmDiscard = null
                action?.invoke()
            },
            onCancel = { confirmDiscard = null },
            onDismiss = { confirmDiscard = null },
            confirmButtonModifier = Modifier.testTag("exercise_remote_discard_confirm"),
            cancelButtonModifier = Modifier.testTag("exercise_remote_discard_cancel"),
            messageStyle = MaterialTheme.typography.contentRegular,
            confirmContainerColor = ColorErrorRed,
            confirmContentColor = ColorWhite300,
        )
    }
    UndabangScaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            UndabangTopBar(
                title = stringResource(R.string.exercise_detail),
                onNavigationClick = onBackClick,
                actions = {
                    // 우측: 공유 + 메뉴
                    IconButton(onClick = onShareClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = stringResource(R.string.share),
                            tint = Color.Unspecified,
                        )
                    }
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("exercise_detail_menu"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = stringResource(R.string.exercise_detail_menu),
                            tint = Color.Unspecified,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (isSuccess && (remoteDeleted || canShowBodyRetry)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                            ),
                ) {
                    if (remoteDeleted) {
                        UndabangNoticeBar(
                            title = stringResource(R.string.exercise_remote_deleted),
                            message = stringResource(R.string.exercise_remote_deleted_description),
                            actionIcon = painterResource(PresentationR.drawable.ic_trash),
                            actionContentDescription = stringResource(R.string.exercise_remote_discard),
                            onActionClick = { confirmDiscard = onDiscardRemoteClick },
                            modifier = Modifier.fillMaxWidth().testTag("exercise_sync_notice"),
                            titleModifier = Modifier.testTag("exercise_remote_deleted"),
                            actionModifier = Modifier.testTag("exercise_remote_discard"),
                        )
                    } else {
                        UndabangNoticeBar(
                            title = stringResource(R.string.exercise_body_failure),
                            message = stringResource(R.string.exercise_body_failure_description),
                            actionIcon = painterResource(PresentationR.drawable.ic_notice_retry),
                            actionContentDescription = stringResource(R.string.exercise_body_retry),
                            onActionClick = onRetryClick,
                            modifier = Modifier.fillMaxWidth().testTag("exercise_sync_notice"),
                            emphasizeTitle = true,
                            titleModifier = Modifier.testTag("exercise_body_failure"),
                            actionModifier = Modifier.testTag("exercise_body_retry"),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            when (state) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Success -> {
                    DetailContent(
                        record = state.data,
                    )
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
    val pictures = record.pictures.orEmpty()
    // 고정된 하단 안내가 바뀌어도 기록 필드의 key와 스크롤 offset을 유지합니다
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize().testTag("exercise_detail_scroll"),
        contentPadding = PaddingValues(top = if (pictures.isEmpty()) 20.dp else 0.dp, bottom = 40.dp),
    ) {
        if (pictures.isNotEmpty()) {
            item(key = "pictures") {
                Column {
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
                                model = pictures[page].source,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
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
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
        item(key = "title") {
            Column {
                DetailField(
                    label = stringResource(R.string.exercise_detail_title_label),
                    value = record.title,
                    valueModifier = Modifier.testTag("exercise_detail_title"),
                )
            }
        }
        if (!record.personalType.isNullOrBlank()) {
            item(key = "type") {
                Column {
                    DetailField(
                        label = stringResource(R.string.exercise_record_type),
                        value = record.personalType.orEmpty(),
                    )
                }
            }
        }
        val formatter = CommonDateTimeFormatters.YY_MM_DD_HH_MM
        val startStr = record.startedAt.format(formatter)
        val endStr = record.endedAt.format(formatter)
        if (!startStr.isNullOrBlank() || !endStr.isNullOrBlank()) {
            item(key = "time") {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.exercise_detail_time_label),
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
                            modifier = Modifier.weight(1f).testTag("exercise_detail_start_time"),
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
                            modifier = Modifier.weight(1f).testTag("exercise_detail_end_time"),
                        )
                    }
                }
            }
        }
        if (!record.location.isNullOrBlank()) {
            item(key = "location") {
                Column {
                    DetailField(
                        label = stringResource(R.string.exercise_record_location),
                        value = record.location.orEmpty(),
                    )
                }
            }
        }
        if (!record.detail.isNullOrBlank()) {
            item(key = "detail") {
                Column {
                    DetailField(
                        label = stringResource(R.string.exercise_record_desc),
                        value = record.detail.orEmpty(),
                        minHeight = 120.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    valueModifier: Modifier = Modifier,
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
            modifier = valueModifier.defaultMinSize(minHeight = minHeight),
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
