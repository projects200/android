package com.project200.presentation.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.project200.presentation.compose.theme.AppTheme

/**
 * ComposeView 진입점에서 AppTheme 감싸기와 컴포지션 전략 설정을 강제하는 확장.
 *
 * Fragment 안에서 ComposeView 를 쓸 때 `DisposeOnViewTreeLifecycleDestroyed` 가
 * 안정적이며, AppTheme 을 빠뜨리면 Material3 기본 색(보라)으로 렌더링된다.
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
