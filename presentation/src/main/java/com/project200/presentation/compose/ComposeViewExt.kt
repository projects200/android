package com.project200.presentation.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.project200.presentation.compose.theme.AppTheme

/**
 * ComposeView에 AppTheme과 컴포지션 전략을 적용하는 확장 함수
 * Fragment의 ComposeView 진입점에서 사용합니다
 */
fun ComposeView.applyAppTheme(content: @Composable () -> Unit) {
    setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
    )
    setContent {
        AppTheme {
            content()
        }
    }
}
