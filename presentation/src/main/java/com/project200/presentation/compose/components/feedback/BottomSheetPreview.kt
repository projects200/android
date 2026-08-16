package com.project200.presentation.compose.components.feedback

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.project200.presentation.compose.theme.AppTheme

@Preview(showBackground = true, widthDp = 400)
@Composable
fun SelectionBottomSheetContentPreview() {
    AppTheme {
        SelectionBottomSheetContent(
            items = listOf("러닝", "헬스", "수영", "등산", "자전거"),
            selectedItem = "헬스",
            onItemSelected = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun SelectionBottomSheetContentNoSelectionPreview() {
    AppTheme {
        SelectionBottomSheetContent(
            items = listOf("최신순", "오래된순", "이름순"),
            selectedItem = null,
            onItemSelected = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun MenuBottomSheetContentPreview() {
    AppTheme {
        MenuBottomSheetContent(
            showEdit = true,
            onEditClick = {},
            onDeleteClick = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
fun MenuBottomSheetContentDeleteOnlyPreview() {
    AppTheme {
        MenuBottomSheetContent(
            showEdit = false,
            onDeleteClick = {},
            onClose = {},
        )
    }
}
