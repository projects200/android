package com.project200.feature.timer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.domain.model.CustomTimer
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBackground
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.header
import com.project200.undabang.feature.timer.R

@Composable
fun TimerListScreen(
    customTimers: List<CustomTimer>,
    onBackClick: () -> Unit,
    onSimpleTimerClick: () -> Unit,
    onCustomTimerClick: (CustomTimer) -> Unit,
    onAddCustomTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorBackground),
    ) {
        UndabangTopBar(
            title = stringResource(R.string.timer_title),
            onNavigationClick = onBackClick,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(20.dp))
            SimpleTimerCard(onClick = onSimpleTimerClick)
            Spacer(Modifier.height(15.dp))
            customTimers.forEach { timer ->
                CustomTimerCard(
                    name = timer.name,
                    onClick = { onCustomTimerClick(timer) },
                )
            }
            AddCustomTimerCard(
                isEmpty = customTimers.isEmpty(),
                onClick = onAddCustomTimerClick,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SimpleTimerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(120.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(15.dp), clip = false)
                .clip(RoundedCornerShape(15.dp))
                .background(ColorMain)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.simple_timer),
            style = MaterialTheme.typography.header,
            color = ColorWhite300,
        )
        Image(
            painter = painterResource(R.drawable.ic_timer_arrow_right),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ColorWhite300),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(24.dp),
        )
    }
}

@Composable
private fun CustomTimerCard(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 5.dp)
                .height(60.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(15.dp), clip = false)
                .clip(RoundedCornerShape(15.dp))
                .background(ColorWhite300)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.header,
            color = ColorBlack,
        )
        Image(
            painter = painterResource(R.drawable.ic_timer_arrow_right),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(24.dp),
        )
    }
}

@Composable
private fun AddCustomTimerCard(
    isEmpty: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 5.dp)
                .height(60.dp)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(15.dp), clip = false)
                .clip(RoundedCornerShape(15.dp))
                .background(ColorWhite300)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        if (isEmpty) {
            // 빈 상태: 가운데 텍스트 + 우측 plus
            Text(
                text = stringResource(R.string.custom_timer_default),
                style = MaterialTheme.typography.header,
                color = ColorBlack,
                modifier = Modifier.align(Alignment.Center),
            )
            Image(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 15.dp),
            )
        } else {
            // 일반 상태: 가운데 plus만
            Image(
                painter = painterResource(R.drawable.ic_plus),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun TimerListScreenPreview() {
    AppTheme {
        TimerListScreen(
            customTimers =
                listOf(
                    CustomTimer(id = 1L, name = "인터벌 트레이닝", steps = emptyList()),
                    CustomTimer(id = 2L, name = "스트레칭", steps = emptyList()),
                ),
            onBackClick = {},
            onSimpleTimerClick = {},
            onCustomTimerClick = {},
            onAddCustomTimerClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun TimerListScreenEmptyPreview() {
    AppTheme {
        TimerListScreen(
            customTimers = emptyList(),
            onBackClick = {},
            onSimpleTimerClick = {},
            onCustomTimerClick = {},
            onAddCustomTimerClick = {},
        )
    }
}
