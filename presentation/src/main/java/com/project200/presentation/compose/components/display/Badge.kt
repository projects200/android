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
 * 숫자 표시용 원형 뱃지
 * 100 이상은 "99+"로 표기합니다
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

/** 숫자 없는 작은 상태 점 (읽지 않음 표시 등) */
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
