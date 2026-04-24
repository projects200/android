package com.project200.presentation.compose.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold

/**
 * 화면 상단 앱바. 왼쪽 네비게이션 아이콘, 가운데 제목, 오른쪽 액션 슬롯 구조.
 *
 * 흰색 배경 + 검정 글자 + 중앙 정렬 제목 — 기존 XML `view_base_toolbar` 디자인을 그대로 옮긴 것.
 * 액션 버튼은 [actions] 슬롯에 [IconButton] 등을 직접 넣어 원하는 개수만큼 배치한다.
 * 홈/루트 화면처럼 뒤로가기가 필요 없으면 [navigationIconVisible] = false 로 자리만 남기고 숨긴다.
 */
@Composable
fun UndabangTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigationClick: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationIconVisible: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(65.dp)
                .background(color = ColorWhite300),
    ) {
        Text(
            text = title,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 64.dp),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIconVisible) {
                IconButton(
                    onClick = { onNavigationClick?.invoke() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = "뒤로가기",
                        tint = ColorBlack,
                    )
                }
            } else {
                Box(modifier = Modifier.size(48.dp))
            }

            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .wrapContentWidth(align = Alignment.End),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
