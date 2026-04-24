package com.project200.presentation.compose.components.display

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.ColorWhite300

/**
 * 그림자 있는 카드 컨테이너. 리스트 아이템·섹션 박스 등 시각적으로 묶어야 하는 영역에 쓴다.
 *
 * Material3 `Card` 래퍼로, 앱 기본 shape (medium, 8dp 라운드) 과 4dp elevation 을 기본값으로 준다.
 * 내부 여백은 호출부가 직접 관리 (카드는 레이아웃 껍데기, 안은 호출부 자유).
 */
@Composable
fun UndabangCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ColorWhite300,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
        ),
    ) {
        content()
    }
}
