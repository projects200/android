package com.project200.presentation.compose.components.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorWhite100
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.subtext14

/**
 * 주 액션 버튼 (완료/확인/제출 등)
 * 기본 높이 55dp, isCompact = true면 36dp
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCompact: Boolean = false,
    height: Dp = if (isCompact) 36.dp else 55.dp,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = ColorGray200,
                disabledContentColor = ColorWhite300,
            ),
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * 보조 액션 버튼 (취소/뒤로 등)
 * PrimaryButton과 짝으로 배치되며, 크기와 shape는 동일하게 유지됩니다
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCompact: Boolean = false,
    height: Dp = if (isCompact) 36.dp else 55.dp,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ColorGray300,
                contentColor = ColorWhite300,
                disabledContainerColor = ColorGray200,
                disabledContentColor = ColorWhite300,
            ),
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * 인라인 텍스트 버튼 (중복 확인/재전송/더보기 등)
 * 기본은 compact (32dp)로, 입력 필드 옆에 붙이는 용도입니다
 */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCompact: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(if (isCompact) 32.dp else 48.dp),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = ColorWhite100,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = ColorGray200,
            ),
        shape = MaterialTheme.shapes.small,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (isCompact) 4.dp else 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.subtext14,
        )
    }
}
