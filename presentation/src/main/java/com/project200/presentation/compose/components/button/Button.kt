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
 * 주 액션 (완료·확인·제출) 에 쓰는 메인 버튼.
 *
 * 브랜드 primary 색 배경 + onPrimary 글자. 기본 높이 55dp, [isCompact] = true 면 36dp.
 * 화면 폭을 꽉 채우려면 호출부에서 `modifier = Modifier.fillMaxWidth()` 를 전달한다.
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
 * 보조 액션 (취소·뒤로) 에 쓰는 버튼. Primary 와 짝으로 배치되는 경우가 많다.
 *
 * 회색 배경 + 흰 글자. 크기·shape 는 [PrimaryButton] 과 동일하게 유지해 같은 줄에 놓아도 어울린다.
 * 색 조합은 XML 시절 디자인을 그대로 따르고 있어 ColorScheme slot 매핑 대신 raw 색을 쓴다.
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
 * 텍스트성 보조 액션 (중복 확인·재전송·더보기 등) 에 쓰는 가벼운 버튼.
 *
 * 연회색 배경 + primary 글자로, 다른 입력 요소 옆에 붙여 써도 시선을 덜 뺏는다.
 * 기본은 compact (32dp) — 인라인 입력 필드 옆에 붙이는 용도로 최적화되어 있다.
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
