package com.project200.presentation.compose.components.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.project200.presentation.compose.theme.ColorWhite300

/**
 * 앱 공통 화면 골격
 * Material3 Scaffold를 감싸 containerColor 기본값(ColorWhite300)을 지정한다
 * 본문 스크롤은 호출부에서 Column + verticalScroll 또는 LazyColumn으로 선택
 *
 * 키보드/시스템 바 인셋은 Scaffold가 자동으로 분배하므로
 * bottomBar에 둔 버튼은 키보드가 올라올 때 함께 밀린다
 */
@Composable
fun UndabangScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    containerColor: Color = ColorWhite300,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = containerColor,
        content = content,
    )
}
