package com.project200.presentation.compose.components.button

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.AppTheme

@Preview(showBackground = true)
@Composable
fun PrimaryButtonPreview() {
    AppTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            PrimaryButton(
                text = "완료",
                onClick = {},
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = "완료",
                onClick = {},
                enabled = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PrimaryButtonCompactPreview() {
    AppTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            PrimaryButton(
                text = "완료",
                onClick = {},
                isCompact = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = "완료",
                onClick = {},
                isCompact = true,
                enabled = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SecondaryButtonPreview() {
    AppTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            SecondaryButton(
                text = "취소",
                onClick = {},
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = "취소",
                onClick = {},
                enabled = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextButtonPreview() {
    AppTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            TextButton(
                text = "중복 확인",
                onClick = {},
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = "중복 확인",
                onClick = {},
                enabled = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ButtonGroupPreview() {
    AppTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            PrimaryButton(text = "확인", onClick = {})
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton(text = "취소", onClick = {})
        }
    }
}
