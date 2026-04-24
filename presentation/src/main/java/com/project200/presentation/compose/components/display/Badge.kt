package com.project200.presentation.compose.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 숫자 표시용 원형 뱃지. 탭바 아이콘·프로필 썸네일 위에 올려 알림 개수 등을 노출한다.
 *
 * 100 이상이면 "99+" 로 표기해 폭이 폭발하지 않는다. 기본 색은 error (빨강) — 중요 알림용.
 */
@Composable
fun UndabangBadge(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError,
    size: Dp = 24.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(
                    color = backgroundColor,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

/**
 * 숫자 없는 작은 상태 점. "읽지 않음" 표시처럼 존재 여부만 알리고 싶을 때 쓴다.
 *
 * [UndabangBadge] 와 달리 카운트를 보여주지 않아 화면 지저분함을 줄인다.
 */
@Composable
fun UndabangDot(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.error,
    size: Dp = 12.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(
                    color = color,
                    shape = CircleShape,
                ),
    )
}
