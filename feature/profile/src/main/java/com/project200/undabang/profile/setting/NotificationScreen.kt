package com.project200.undabang.profile.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.feature.profile.R

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NotificationScreen(
    isExerciseOn: Boolean,
    isChattingOn: Boolean,
    onNavigateBack: () -> Unit,
    onExerciseToggle: (Boolean) -> Unit,
    onChattingToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    syncState: NotificationSyncState = NotificationSyncState.Ready,
    hasDevicePermission: Boolean = true,
) {
    UndabangScaffold(
        // Maestro id 셀렉터가 이 화면 하위 testTag를 android:id처럼 찾게 합니다
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            UndabangTopBar(
                title = stringResource(R.string.notification),
                onNavigationClick = { if (syncState != NotificationSyncState.Saving) onNavigateBack() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .testTag("notification_${syncState.name.lowercase()}"),
        ) {
            when (syncState) {
                NotificationSyncState.Loading -> CircularProgressIndicator()
                NotificationSyncState.Error -> Text(text = stringResource(R.string.noti_state_failed))
                NotificationSyncState.Ready, NotificationSyncState.Saving -> {
                    if (!hasDevicePermission) {
                        Text(
                            text = stringResource(R.string.noti_permission_announce),
                            modifier = Modifier.testTag("notification_permission_denied"),
                        )
                    }
                    NotificationSwitchRow(
                        labelRes = R.string.exercise_notification,
                        checked = isExerciseOn,
                        onCheckedChange = onExerciseToggle,
                        testTag = "exercise_noti_switch",
                        enabled = syncState == NotificationSyncState.Ready,
                    )
                    NotificationSwitchRow(
                        labelRes = R.string.chatting_notification,
                        checked = isChattingOn,
                        onCheckedChange = onChattingToggle,
                        testTag = "chatting_noti_switch",
                        enabled = syncState == NotificationSyncState.Ready,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSwitchRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
                enabled = enabled,
                // 운동/채팅 알림 스위치를 구분해 토글·상태 검증에 씁니다
                modifier = Modifier.testTag(testTag),
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
