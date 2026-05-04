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

/** XML `dialog_base_alert` 기준 다이얼로그 버튼 높이 (XML 45dp). */
private val DialogButtonHeight = 45.dp

/**
 * 제목 + 메시지 + 액션 버튼으로 구성된 공통 확인 다이얼로그 (Compose `Dialog` 기반).
 *
 * Compose 화면에서 직접 띄우는 경우에 사용. Fragment 컨텍스트에서는 [UndabangAlertDialogFragment] 활용.
 *
 * [onCancel] 을 주면 취소/확인 두 버튼이 나란히 표시되고, null 이면 확인 버튼 하나만 전체 폭으로 표시된다.
 * [isCancelable] = false 면 뒤로가기·바깥 탭으로 닫히지 않아 네트워크 오류 같은 블로킹 안내에 쓴다.
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
 * 제목 + 메시지 + 액션 버튼 콘텐츠 (Dialog 래핑 없음).
 *
 * 기존 XML `dialog_base_alert` 비주얼 그대로 — 제목/메시지 모두 content_bold(15sp bold),
 * 섹션 간 간격 32dp, 버튼 높이 45dp. [message] 가 빈 문자열이면 메시지 영역을 그리지 않는다.
 *
 * Compose `Dialog` 가 아닌 다른 호스트 (예: [UndabangAlertDialogFragment] 의 ComposeView) 에서
 * 다이얼로그 콘텐츠만 띄울 때 사용.
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
