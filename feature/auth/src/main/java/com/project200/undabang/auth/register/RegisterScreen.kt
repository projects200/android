package com.project200.undabang.auth.register

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.input.TextFieldVariant
import com.project200.presentation.compose.components.input.UndabangTextField
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.presentation.compose.theme.header
import com.project200.presentation.compose.theme.subtext14
import com.project200.undabang.auth.register.RegisterFragment.Companion.FEMALE
import com.project200.undabang.auth.register.RegisterFragment.Companion.HIDDEN
import com.project200.undabang.auth.register.RegisterFragment.Companion.MALE
import com.project200.undabang.feature.auth.R
import com.project200.undabang.presentation.R as PresentationR

private const val LARGE_FONT_SCALE_THRESHOLD = 1.5f

@Composable
fun RegisterScreen(
    nickname: String,
    birth: String?,
    gender: String?,
    isFormValid: Boolean,
    onNicknameChange: (String) -> Unit,
    onBirthClick: () -> Unit,
    onGenderSelect: (String) -> Unit,
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalConfiguration.current.fontScale
    val nicknameMargin = if (fontScale > LARGE_FONT_SCALE_THRESHOLD) 60.dp else 100.dp

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorWhite300)
                .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(114.dp))

            Text(
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.header,
                color = ColorBlack,
            )

            Spacer(Modifier.height(90.dp))

            NicknameSection(
                nickname = nickname,
                nicknameMargin = nicknameMargin,
                onNicknameChange = onNicknameChange,
            )

            Spacer(Modifier.height(32.dp))

            BirthSection(
                birth = birth,
                nicknameMargin = nicknameMargin,
                onClick = onBirthClick,
            )

            Spacer(Modifier.height(32.dp))

            GenderSection(
                gender = gender,
                nicknameMargin = nicknameMargin,
                onSelect = onGenderSelect,
            )

            Spacer(Modifier.height(40.dp))
        }

        PrimaryButton(
            text = stringResource(PresentationR.string.complete),
            onClick = onCompleteClick,
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NicknameSection(
    nickname: String,
    nicknameMargin: Dp,
    onNicknameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelText = stringResource(PresentationR.string.nickname)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.contentBold,
                color = ColorBlack,
            )
            Spacer(Modifier.width(nicknameMargin))
            UndabangTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                variant = TextFieldVariant.Underline,
                maxLength = 30,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Spacer(Modifier.width(nicknameMargin))
            Text(
                text = stringResource(R.string.register_nickname_rule),
                style = MaterialTheme.typography.subtext14,
                color = ColorGray200,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun BirthSection(
    birth: String?,
    nicknameMargin: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(PresentationR.string.birth),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
        )
        Spacer(Modifier.width(nicknameMargin))
        Text(
            text = birth ?: stringResource(R.string.birth_default),
            style = MaterialTheme.typography.contentRegular,
            color = ColorBlack,
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                    .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun GenderSection(
    gender: String?,
    nicknameMargin: Dp,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = stringResource(PresentationR.string.gender),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
        )
        Spacer(Modifier.width(nicknameMargin))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GenderItem(
                    label = stringResource(R.string.male),
                    selected = gender == MALE,
                    onClick = { onSelect(MALE) },
                )
                Spacer(Modifier.width(12.dp))
                GenderItem(
                    label = stringResource(R.string.female),
                    selected = gender == FEMALE,
                    onClick = { onSelect(FEMALE) },
                )
            }
            Spacer(Modifier.height(12.dp))
            GenderItem(
                label = stringResource(R.string.hidden),
                selected = gender == HIDDEN,
                onClick = { onSelect(HIDDEN) },
            )
        }
    }
}

@Composable
private fun GenderItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.contentRegular,
            color = ColorBlack,
        )
        Spacer(Modifier.width(4.dp))
        Image(
            painter =
                painterResource(
                    if (selected) R.drawable.ic_check else R.drawable.ic_uncheck,
                ),
            contentDescription = null,
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RegisterScreenPreview() {
    AppTheme {
        var nickname by remember { mutableStateOf("운다방테스터") }
        var birth by remember { mutableStateOf<String?>("1990-01-01") }
        var gender by remember { mutableStateOf<String?>(MALE) }
        RegisterScreen(
            nickname = nickname,
            birth = birth,
            gender = gender,
            isFormValid = true,
            onNicknameChange = { nickname = it },
            onBirthClick = {},
            onGenderSelect = { gender = it },
            onCompleteClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RegisterScreenEmptyPreview() {
    AppTheme {
        RegisterScreen(
            nickname = "",
            birth = null,
            gender = null,
            isFormValid = false,
            onNicknameChange = {},
            onBirthClick = {},
            onGenderSelect = {},
            onCompleteClick = {},
        )
    }
}
