package com.project200.presentation.compose.components.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.AppTheme

@Preview(showBackground = true)
@Composable
fun TopBarPreviewBackOnly() {
    AppTheme {
        UndabangTopBar(
            title = "피드",
            onNavigationClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TopBarPreviewWithOneAction() {
    AppTheme {
        UndabangTopBar(
            title = "피드 상세",
            onNavigationClick = {},
            actionIcon1 = Icons.Default.MoreVert,
            onAction1Click = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TopBarPreviewWithTwoActions() {
    AppTheme {
        UndabangTopBar(
            title = "채팅방",
            onNavigationClick = {},
            actionIcon1 = Icons.Default.Share,
            onAction1Click = {},
            actionIcon2 = Icons.Default.Delete,
            onAction2Click = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TopBarPreviewNoNavigation() {
    AppTheme {
        UndabangTopBar(
            title = "설정",
            navigationIconVisible = false,
            onNavigationClick = {}
        )
    }
}
