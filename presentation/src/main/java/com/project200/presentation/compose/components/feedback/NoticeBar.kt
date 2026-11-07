package com.project200.presentation.compose.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorNoticeBackground
import com.project200.presentation.compose.theme.ColorNoticeText
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.subtext12

/**
 * 두 문장과 아이콘 액션을 표시하는 공통 안내 바
 * 표시 조건과 화면 배치, 시스템 인셋은 호출부에서 처리합니다
 */
@Composable
fun UndabangNoticeBar(
    title: String,
    message: String,
    actionIcon: Painter,
    actionContentDescription: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasizeTitle: Boolean = false,
    titleModifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(color = ColorWhite300)
                    drawRect(color = ColorNoticeBackground)
                    drawRect(
                        color = ColorErrorRed,
                        size = Size(size.width, 1.dp.toPx()),
                    )
                }
                // 48dp 터치 영역 안의 20dp 아이콘에 오른쪽 여백 20dp를 확보합니다
                .padding(start = 20.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
        ) {
            Text(
                text = title,
                modifier = titleModifier,
                style = MaterialTheme.typography.subtext12,
                fontWeight = if (emphasizeTitle) FontWeight.Bold else FontWeight.Normal,
                color = ColorNoticeText,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.subtext12,
                color = ColorNoticeText,
            )
        }
        IconButton(
            onClick = onActionClick,
            modifier = actionModifier.size(48.dp),
        ) {
            Icon(
                painter = actionIcon,
                contentDescription = actionContentDescription,
                modifier = Modifier.size(20.dp),
                tint = ColorErrorRed,
            )
        }
    }
}
