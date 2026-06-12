package com.project200.undabang.profile.mypage

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.input.TextFieldVariant
import com.project200.presentation.compose.components.input.UndabangTextField
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.presentation.compose.theme.subtext14
import com.project200.undabang.feature.profile.R
import com.project200.undabang.profile.mypage.ProfileEditFragment.Companion.FEMALE
import com.project200.undabang.profile.mypage.ProfileEditFragment.Companion.HIDDEN
import com.project200.undabang.profile.mypage.ProfileEditFragment.Companion.MALE
import com.project200.undabang.profile.utils.NicknameValidationState
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun ProfileEditScreen(
    nickname: String,
    gender: String?,
    introduction: String,
    initProfileImageUrl: String?,
    newProfileImageUri: Uri?,
    nicknameValidationState: NicknameValidationState,
    isNicknameChecked: Boolean,
    onNicknameChange: (String) -> Unit,
    onIntroductionChange: (String) -> Unit,
    onGenderSelect: (String) -> Unit,
    onProfileImageClick: () -> Unit,
    onDuplicateCheckClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorWhite300),
    ) {
        UndabangTopBar(
            title = stringResource(R.string.profile_edit),
            onNavigationClick = onBackClick,
            actions = {
                PrimaryButton(
                    text = stringResource(PresentationR.string.complete),
                    onClick = onCompleteClick,
                    isCompact = true,
                    modifier = Modifier.padding(end = 20.dp),
                )
            },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            ProfileImageSection(
                initImageUrl = initProfileImageUrl,
                newImageUri = newProfileImageUri,
                onClick = onProfileImageClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(90.dp))

            NicknameSection(
                nickname = nickname,
                validationState = nicknameValidationState,
                isChecked = isNicknameChecked,
                onNicknameChange = onNicknameChange,
                onDuplicateCheckClick = onDuplicateCheckClick,
            )

            Spacer(Modifier.height(32.dp))

            GenderSection(
                gender = gender,
                onSelect = onGenderSelect,
            )

            Spacer(Modifier.height(32.dp))

            IntroductionSection(
                introduction = introduction,
                onChange = onIntroductionChange,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileImageSection(
    initImageUrl: String?,
    newImageUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(120.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        AsyncImage(
            model = newImageUri ?: initImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(PresentationR.drawable.ic_profile_default),
            error = painterResource(PresentationR.drawable.ic_profile_default),
            fallback = painterResource(PresentationR.drawable.ic_profile_default),
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
        )
        Icon(
            painter = painterResource(R.drawable.ic_add_profile_img_btn),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(40.dp),
        )
    }
}

@Composable
private fun NicknameSection(
    nickname: String,
    validationState: NicknameValidationState,
    isChecked: Boolean,
    onNicknameChange: (String) -> Unit,
    onDuplicateCheckClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelText = stringResource(R.string.nickname)
    val labelStyle = MaterialTheme.typography.contentBold

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = labelText, style = labelStyle, color = ColorBlack)
            Spacer(Modifier.width(20.dp))
            UndabangTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                modifier = Modifier.weight(1f),
                variant = TextFieldVariant.Underline,
                maxLength = 30,
            )
            Spacer(Modifier.width(8.dp))
            DuplicateCheckButton(
                onClick = onDuplicateCheckClick,
                enabled = !isChecked && nickname.isNotBlank(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row {
            // 라벨 영역 reserve (invisible) — 안내문이 TextField 좌측 정렬과 맞춰지게
            Text(text = labelText, style = labelStyle, color = Color.Transparent)
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (validationState != NicknameValidationState.INVISIBLE) {
                    val (message, color) =
                        when (validationState) {
                            NicknameValidationState.INVALID ->
                                stringResource(R.string.error_nickname_invalid) to ColorErrorRed
                            NicknameValidationState.DUPLICATED ->
                                stringResource(R.string.error_nickname_duplicated) to ColorErrorRed
                            NicknameValidationState.AVAILABLE ->
                                stringResource(R.string.usable_nickname) to ColorMain
                            NicknameValidationState.INVISIBLE -> "" to ColorBlack
                        }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.subtext14,
                        color = color,
                    )
                }
                Text(
                    text = stringResource(R.string.profile_edit_nickname_rule),
                    style = MaterialTheme.typography.subtext14,
                    color = ColorGray200,
                )
            }
        }
    }
}

@Composable
private fun GenderSection(
    gender: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(PresentationR.string.gender),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
        )
        Spacer(Modifier.width(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GenderRow(
                label = stringResource(R.string.profile_edit_male),
                selected = gender == MALE,
                onClick = { onSelect(MALE) },
            )
            GenderRow(
                label = stringResource(R.string.profile_edit_female),
                selected = gender == FEMALE,
                onClick = { onSelect(FEMALE) },
            )
            GenderRow(
                label = stringResource(R.string.profile_edit_hidden),
                selected = gender == HIDDEN,
                onClick = { onSelect(HIDDEN) },
            )
        }
    }
}

@Composable
private fun GenderRow(
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

@Composable
private fun DuplicateCheckButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (enabled) ColorMain else ColorGray200
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(5.dp))
                .background(ColorWhite300)
                .border(1.dp, color, RoundedCornerShape(5.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_edit_duplicate_check),
            style = MaterialTheme.typography.contentRegular,
            color = color,
        )
    }
}

@Composable
private fun IntroductionSection(
    introduction: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.profile_edit_introduction),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
        )
        Spacer(Modifier.height(8.dp))
        UndabangTextField(
            value = introduction,
            onValueChange = onChange,
            hint = stringResource(R.string.empty_introduction),
            variant = TextFieldVariant.FilledLarge,
            singleLine = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ProfileEditScreenPreview() {
    AppTheme {
        var nickname by remember { mutableStateOf("운다방테스터") }
        var gender by remember { mutableStateOf<String?>(MALE) }
        var introduction by remember { mutableStateOf("안녕하세요") }
        ProfileEditScreen(
            nickname = nickname,
            gender = gender,
            introduction = introduction,
            initProfileImageUrl = null,
            newProfileImageUri = null,
            nicknameValidationState = NicknameValidationState.AVAILABLE,
            isNicknameChecked = true,
            onNicknameChange = { nickname = it },
            onIntroductionChange = { introduction = it },
            onGenderSelect = { gender = it },
            onProfileImageClick = {},
            onDuplicateCheckClick = {},
            onCompleteClick = {},
            onBackClick = {},
        )
    }
}
