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
 * 그림자 있는 카드 컨테이너
 * 기본 shape는 medium(8dp 라운드), elevation은 4dp입니다
 */
@Composable
fun UndabangCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ColorWhite300,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor,
            ),
        shape = MaterialTheme.shapes.medium,
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 4.dp,
            ),
    ) {
        content()
    }
}
