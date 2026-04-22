package com.project200.presentation.compose.components.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.project200.presentation.compose.theme.AppTheme

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun AlertDialogPreview() {
    AppTheme {
        var showDialog by remember { mutableStateOf(true) }

        if (showDialog) {
            UndabangAlertDialog(
                title = "삭제 확인",
                message = "정말로 이 항목을 삭제하시겠습니까?",
                confirmText = "삭제",
                cancelText = "취소",
                onConfirm = { showDialog = false },
                onCancel = { showDialog = false },
                onDismiss = { showDialog = false }
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun AlertDialogConfirmOnlyPreview() {
    AppTheme {
        var showDialog by remember { mutableStateOf(true) }

        if (showDialog) {
            UndabangAlertDialog(
                title = "완료",
                message = "피드가 성공적으로 업로드되었습니다!",
                confirmText = "확인",
                cancelText = "",
                onConfirm = { showDialog = false },
                onDismiss = { showDialog = false },
                isCancelable = true
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun ConfirmDialogPreview() {
    AppTheme {
        var showDialog by remember { mutableStateOf(true) }

        if (showDialog) {
            UndabangConfirmDialog(
                title = "완료",
                message = "변경사항이 저장되었습니다.",
                confirmText = "확인",
                onConfirm = { showDialog = false },
                onDismiss = { showDialog = false }
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun AlertDialogNotCancelablePreview() {
    AppTheme {
        var showDialog by remember { mutableStateOf(true) }

        if (showDialog) {
            UndabangAlertDialog(
                title = "중요 알림",
                message = "네트워크 연결을 확인해주세요.",
                confirmText = "확인",
                onConfirm = { showDialog = false },
                onDismiss = { showDialog = false },
                isCancelable = false
            )
        }
    }
}
