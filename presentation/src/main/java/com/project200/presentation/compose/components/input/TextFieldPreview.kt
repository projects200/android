package com.project200.presentation.compose.components.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.AppTheme

@Preview(showBackground = true)
@Composable
fun TextFieldFilledSmallPreview() {
    AppTheme {
        var value by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "닉네임을 입력하세요",
                variant = TextFieldVariant.FilledSmall,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "닉네임을 입력하세요",
                variant = TextFieldVariant.FilledSmall,
                singleLine = true,
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextFieldFilledLargePreview() {
    AppTheme {
        var value by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "긴 내용을 입력하세요",
                variant = TextFieldVariant.FilledLarge,
                singleLine = false,
                maxLines = 5
            )
            Spacer(modifier = Modifier.height(12.dp))
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "긴 내용을 입력하세요",
                variant = TextFieldVariant.FilledLarge,
                singleLine = false,
                enabled = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextFieldOutlinedPreview() {
    AppTheme {
        var value by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "장소명을 입력하세요",
                variant = TextFieldVariant.Outlined,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "장소명을 입력하세요",
                variant = TextFieldVariant.Outlined,
                singleLine = true,
                isError = true,
                supportingText = "중복된 장소입니다"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextFieldNumericPreview() {
    AppTheme {
        var minute by remember { mutableStateOf("10") }
        var second by remember { mutableStateOf("30") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UndabangTextField(
                value = minute,
                onValueChange = { minute = it },
                hint = "분",
                variant = TextFieldVariant.FilledSmall,
                singleLine = true,
                keyboardType = KeyboardType.Number,
                maxLength = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            UndabangTextField(
                value = second,
                onValueChange = { second = it },
                hint = "초",
                variant = TextFieldVariant.FilledSmall,
                singleLine = true,
                keyboardType = KeyboardType.Number,
                maxLength = 2
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextFieldWithSupportingTextPreview() {
    AppTheme {
        var value by remember { mutableStateOf("undabang2024") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "닉네임",
                variant = TextFieldVariant.FilledSmall,
                singleLine = true,
                supportingText = "사용 가능한 닉네임입니다"
            )
            Spacer(modifier = Modifier.height(16.dp))
            UndabangTextField(
                value = value,
                onValueChange = { value = it },
                hint = "닉네임",
                variant = TextFieldVariant.FilledSmall,
                singleLine = true,
                isError = true,
                supportingText = "이미 사용 중인 닉네임입니다"
            )
        }
    }
}
