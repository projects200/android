package com.project200.feature.timer.custom

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.domain.model.Step
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBackground
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.header
import com.project200.undabang.feature.timer.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun CustomTimerFormScreen(
    title: String,
    listItems: List<TimerFormListItem>,
    isEditMode: Boolean,
    onTitleChange: (String) -> Unit,
    onStepNameChange: (Long, String) -> Unit,
    onStepTimeClick: (Long, Int) -> Unit,
    onStepDelete: (Long) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onNewStepNameChange: (String) -> Unit,
    onNewStepTimeClick: (Int) -> Unit,
    onAddStep: () -> Unit,
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
                        if (isEditMode) R.string.custom_timer_edit else R.string.custom_timer_create,
                    ),
                onNavigationClick = onBackClick,
                actions = {
                    PrimaryButton(
                        text = stringResource(com.project200.undabang.presentation.R.string.complete),
                        onClick = onCompleteClick,
                        isCompact = true,
                        modifier = Modifier.padding(end = 20.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val reorderableState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                onMove(from.index, to.index)
            }

        // 원본: 제목 EditText는 RecyclerView 위(흰색 배경), 리스트만 main_background
        // Compose에서도 제목 영역과 리스트 영역의 배경을 분리
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                textStyle =
                    TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorBlack,
                    ),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 30.dp),
                decorationBox = { inner ->
                    if (title.isEmpty()) {
                        Text(
                            text = stringResource(R.string.custom_timer_title_hint),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorGray200,
                        )
                    }
                    inner()
                },
            )

            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(ColorBackground),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                items(
                    items = listItems,
                    key = { it.id.takeIf { id -> id != 0L } ?: "footer" },
                ) { item ->
                    when (item) {
                        is TimerFormListItem.StepItem -> {
                            ReorderableItem(reorderableState, key = item.id) {
                                StepRow(
                                    step = item.step,
                                    reorderModifier =
                                        Modifier.draggableHandle(
                                            interactionSource = remember { MutableInteractionSource() },
                                        ),
                                    onNameChange = { onStepNameChange(item.step.id, it) },
                                    onTimeClick = { onStepTimeClick(item.step.id, item.step.time) },
                                    onDelete = { onStepDelete(item.step.id) },
                                )
                            }
                        }
                        is TimerFormListItem.FooterItem -> {
                            FooterRow(
                                name = item.name,
                                time = item.time,
                                onNameChange = onNewStepNameChange,
                                onTimeClick = { onNewStepTimeClick(item.time) },
                                onAdd = onAddStep,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    step: Step,
    reorderModifier: Modifier,
    onNameChange: (String) -> Unit,
    onTimeClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .shadow(2.dp, RoundedCornerShape(15.dp))
                .clip(RoundedCornerShape(15.dp))
                .background(ColorWhite300)
                .height(60.dp)
                .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_order_handler),
            contentDescription = null,
            modifier =
                reorderModifier
                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        )
        Image(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorBlack),
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = step.name,
            onValueChange = onNameChange,
            textStyle =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorBlack,
                ),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatStepTime(step.time),
            style = MaterialTheme.typography.header,
            color = ColorBlack,
            modifier =
                Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTimeClick,
                    )
                    .padding(start = 12.dp),
        )
        Image(
            painter = painterResource(R.drawable.ic_minus),
            contentDescription = null,
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
        )
    }
}

@Composable
private fun FooterRow(
    name: String,
    time: Int,
    onNameChange: (String) -> Unit,
    onTimeClick: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 20.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(ColorGray300)
                .drawWithContent {
                    drawContent()
                    // 점선 테두리: width 1.5dp, gray200, dash 10dp / gap 5dp
                    val strokeWidthPx = 1.5.dp.toPx()
                    val radiusPx = 15.dp.toPx()
                    drawRoundRect(
                        color = ColorGray200,
                        topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                        size =
                            Size(
                                size.width - strokeWidthPx,
                                size.height - strokeWidthPx,
                            ),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style =
                            Stroke(
                                width = strokeWidthPx,
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(10.dp.toPx(), 5.dp.toPx()),
                                        0f,
                                    ),
                            ),
                    )
                }
                .height(60.dp)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorGray200),
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            textStyle =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorBlack,
                ),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.custom_timer_step_hint),
                        style = MaterialTheme.typography.contentBold,
                        fontSize = 18.sp,
                        color = ColorGray200,
                    )
                }
                inner()
            },
        )
        Text(
            text = formatStepTime(time),
            style = MaterialTheme.typography.header,
            color = ColorBlack,
            modifier =
                Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTimeClick,
                    )
                    .padding(start = 12.dp),
        )
        Image(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = null,
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAdd,
                    ),
        )
    }
}

private fun formatStepTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CustomTimerFormScreenPreview() {
    AppTheme {
        CustomTimerFormScreen(
            title = "운동 루틴",
            listItems =
                listOf(
                    TimerFormListItem.StepItem(Step(id = 1L, order = 0, time = 30, name = "준비")),
                    TimerFormListItem.StepItem(Step(id = 2L, order = 1, time = 60, name = "스쿼트")),
                    TimerFormListItem.StepItem(Step(id = 3L, order = 2, time = 90, name = "휴식")),
                    TimerFormListItem.FooterItem(name = "", time = 60),
                ),
            isEditMode = false,
            onTitleChange = {},
            onStepNameChange = { _, _ -> },
            onStepTimeClick = { _, _ -> },
            onStepDelete = {},
            onMove = { _, _ -> },
            onNewStepNameChange = {},
            onNewStepTimeClick = {},
            onAddStep = {},
            onCompleteClick = {},
            onBackClick = {},
        )
    }
}
