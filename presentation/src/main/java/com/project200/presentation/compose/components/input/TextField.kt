package com.project200.presentation.compose.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite100

/**
 * [UndabangTextField] 의 시각 변형.
 *
 * - [FilledSmall]: 한 줄 입력 (닉네임, 검색어 등). 연회색 배경, 48dp 고정 높이
 * - [FilledLarge]: 여러 줄 입력 (본문, 소개글 등). 연회색 배경, 높이 제한 없음
 * - [Outlined]: 에러/검증 상태를 테두리로 강조하는 입력 (장소명, 이메일 등)
 */
enum class TextFieldVariant {
    FilledSmall,
    FilledLarge,
    Outlined,
}

/**
 * 기본 텍스트 입력 필드. 상태 hoisting 패턴으로 [value] / [onValueChange] 를 호출부가 관리한다.
 *
 * [variant] 로 한 줄/여러 줄/아웃라인 중 선택. [maxLength] 를 주면 붙여넣기로 초과 입력 시 잘라서 반영한다.
 * 에러 안내는 [isError] + [supportingText] 로, 도움말 텍스트는 [supportingText] (에러 아닐 때) 로 표시한다.
 */
@Composable
fun UndabangTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    variant: TextFieldVariant = TextFieldVariant.FilledSmall,
    singleLine: Boolean = variant == TextFieldVariant.FilledSmall,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    maxLength: Int? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String = "",
    onFocusChange: (Boolean) -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val style = when (variant) {
        TextFieldVariant.FilledSmall -> TextFieldStyle(
            backgroundColor = ColorWhite100,
            cornerRadius = 8.dp,
            borderColor = null,
            borderWidth = null,
            padding = 12.dp,
            minHeight = 48.dp,
            hintColor = ColorGray200,
        )
        TextFieldVariant.FilledLarge -> TextFieldStyle(
            backgroundColor = ColorWhite100,
            cornerRadius = 8.dp,
            borderColor = null,
            borderWidth = null,
            padding = 16.dp,
            minHeight = 0.dp,
            hintColor = ColorGray200,
        )
        TextFieldVariant.Outlined -> TextFieldStyle(
            backgroundColor = ColorWhite100,
            cornerRadius = 8.dp,
            borderColor = if (isError) ColorErrorRed else ColorGray200,
            borderWidth = 1.dp,
            padding = 12.dp,
            minHeight = 48.dp,
            hintColor = ColorGray200,
        )
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (style.minHeight > 0.dp) Modifier.height(style.minHeight) else Modifier,
                )
                .background(
                    color = if (enabled) style.backgroundColor else ColorGray300,
                    shape = RoundedCornerShape(style.cornerRadius),
                )
                .then(
                    if (style.borderColor != null && style.borderWidth != null) {
                        Modifier.border(
                            width = style.borderWidth,
                            color = style.borderColor,
                            shape = RoundedCornerShape(style.cornerRadius),
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(style.padding),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    val next = if (maxLength != null) newValue.take(maxLength) else newValue
                    if (next != value) onValueChange(next)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState -> onFocusChange(focusState.isFocused) }
                    .then(
                        if (style.minHeight > 0.dp) Modifier else Modifier.defaultMinSize(minHeight = 56.dp),
                    ),
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
                keyboardOptions = keyboardOptions,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    color = if (enabled) ColorBlack else ColorGray200,
                ),
                enabled = enabled && !readOnly,
                readOnly = readOnly,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = hint,
                                color = style.hintColor,
                                fontSize = 14.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        if (supportingText.isNotEmpty()) {
            Text(
                text = supportingText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = style.padding, end = style.padding),
                color = if (isError) ColorErrorRed else ColorMain,
                fontSize = 12.sp,
            )
        }
    }
}

private data class TextFieldStyle(
    val backgroundColor: Color,
    val cornerRadius: Dp,
    val borderColor: Color?,
    val borderWidth: Dp?,
    val padding: Dp,
    val minHeight: Dp,
    val hintColor: Color,
)
