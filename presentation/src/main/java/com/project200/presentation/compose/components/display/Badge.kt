package com.project200.presentation.compose.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300

@Composable
fun UndabangBadge(
    count: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ColorErrorRed,
    contentColor: Color = ColorWhite300,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = backgroundColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun UndabangDot(
    modifier: Modifier = Modifier,
    color: Color = ColorErrorRed,
    size: androidx.compose.ui.unit.Dp = 12.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = color,
                shape = CircleShape
            )
    )
}
