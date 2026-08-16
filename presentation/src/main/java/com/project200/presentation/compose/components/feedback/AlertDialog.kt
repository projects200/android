package com.project200.presentation.compose.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.button.SecondaryButton
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold

/** 다이얼로그 버튼 높이 (XML dialog_base_alert 기준 45dp) */
private val DialogButtonHeight = 45.dp

/**
 * 공통 확인 다이얼로그 (Compose Dialog 기반)
 * Compose 화면에서 직접 띄울 때 사용하고, Fragment 컨텍스트에서는 UndabangAlertDialogFragment를 사용합니다
 * onCancel이 null이면 확인 버튼만 전체 폭으로 표시됩니다
 */
@Composable
fun UndabangAlertDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "확인",
    cancelText: String = "취소",
    onCancel: (() -> Unit)? = null,
    isCancelable: Boolean = true,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = isCancelable,
                dismissOnClickOutside = isCancelable,
            ),
    ) {
        UndabangAlertDialogContent(
            title = title,
            message = message,
            onConfirm = {
                onConfirm()
                onDismiss()
            },
            modifier = modifier.fillMaxWidth(0.85f),
            confirmText = confirmText,
            cancelText = cancelText,
            onCancel =
                onCancel?.let {
                    {
                        it()
                        onDismiss()
                    }
                },
        )
    }
}

/**
 * UndabangAlertDialog의 콘텐츠만 분리한 Composable (Dialog 래핑 없음)
 * Compose Dialog가 아닌 다른 호스트(UndabangAlertDialogFragment의 ComposeView 등)에서 콘텐츠만 띄울 때 사용합니다
 * message가 빈 문자열이면 메시지 영역을 그리지 않습니다
 */
@Composable
fun UndabangAlertDialogContent(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "확인",
    cancelText: String = "취소",
    onCancel: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ColorWhite300,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.contentBold,
                color = ColorBlack,
                textAlign = TextAlign.Center,
            )

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.contentBold,
                    color = ColorBlack,
                    textAlign = TextAlign.Center,
                )
            }

            if (onCancel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        text = cancelText,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        height = DialogButtonHeight,
                    )
                    PrimaryButton(
                        text = confirmText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        height = DialogButtonHeight,
                    )
                }
            } else {
                PrimaryButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    height = DialogButtonHeight,
                )
            }
        }
    }
}
