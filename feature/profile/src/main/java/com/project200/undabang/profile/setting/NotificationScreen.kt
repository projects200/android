package com.project200.undabang.profile.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.feature.profile.R

@Composable
fun NotificationScreen(
    isExerciseOn: Boolean,
    isChattingOn: Boolean,
    onNavigateBack: () -> Unit,
    onExerciseToggle: (Boolean) -> Unit,
    onChattingToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorWhite300),
    ) {
        UndabangTopBar(
            title = stringResource(R.string.notification),
            onNavigationClick = onNavigateBack,
        )

        NotificationSwitchRow(
            labelRes = R.string.exercise_notification,
            checked = isExerciseOn,
            onCheckedChange = onExerciseToggle,
        )
        NotificationSwitchRow(
            labelRes = R.string.chatting_notification,
            checked = isChattingOn,
            onCheckedChange = onChattingToggle,
        )
    }
}

@Composable
private fun NotificationSwitchRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(id = labelRes),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.contentRegular,
                color = ColorBlack,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = ColorWhite300,
                        checkedTrackColor = ColorMain,
                        checkedBorderColor = ColorMain,
                        uncheckedThumbColor = ColorWhite300,
                        uncheckedTrackColor = ColorGray200,
                        uncheckedBorderColor = ColorGray200,
                    ),
            )
        }
        HorizontalDivider(thickness = 0.3.dp, color = ColorBlack)
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationScreenPreview() {
    AppTheme {
        NotificationScreen(
            isExerciseOn = true,
            isChattingOn = false,
            onNavigateBack = {},
            onExerciseToggle = {},
            onChattingToggle = {},
        )
    }
}
