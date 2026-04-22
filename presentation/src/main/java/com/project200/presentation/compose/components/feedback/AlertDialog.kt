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
import com.project200.presentation.compose.theme.mediumBold
import com.project200.presentation.compose.theme.subtext14

@Composable
fun UndabangAlertDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    confirmText: String = "확인",
    cancelText: String = "취소",
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    isCancelable: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = isCancelable,
            dismissOnClickOutside = isCancelable
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.85f),
            shape = MaterialTheme.shapes.large,
            color = ColorWhite300
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.mediumBold,
                    color = ColorBlack,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.subtext14,
                    color = ColorBlack,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onCancel != null) {
                        SecondaryButton(
                            text = cancelText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onCancel()
                                onDismiss()
                            },
                            isCompact = true
                        )
                    }

                    PrimaryButton(
                        text = confirmText,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        isCompact = true
                    )
                }
            }
        }
    }
}

@Composable
fun UndabangConfirmDialog(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    confirmText: String = "확인",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isCancelable: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = isCancelable,
            dismissOnClickOutside = isCancelable
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.85f),
            shape = MaterialTheme.shapes.large,
            color = ColorWhite300
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.mediumBold,
                    color = ColorBlack,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.subtext14,
                    color = ColorBlack,
                    textAlign = TextAlign.Center,
                )

                PrimaryButton(
                    text = confirmText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    onClick = {
                        onConfirm()
                        onDismiss()
                    },
                    isCompact = true
                )
            }
        }
    }
}
