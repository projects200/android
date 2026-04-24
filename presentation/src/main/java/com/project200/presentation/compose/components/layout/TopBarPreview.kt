package com.project200.presentation.compose.components.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.project200.presentation.compose.theme.AppTheme

@Preview(showBackground = true)
@Composable
fun TopBarPreviewBackOnly() {
    AppTheme {
        UndabangTopBar(
            title = "피드",
            onNavigationClick = {},
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
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "더보기",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopBarPreviewWithTwoActions() {
    AppTheme {
        UndabangTopBar(
            title = "채팅방",
            onNavigationClick = {},
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "공유",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopBarPreviewNoNavigation() {
    AppTheme {
        UndabangTopBar(
            title = "설정",
            navigationIconVisible = false,
            onNavigationClick = {},
        )
    }
}
