package com.project200.undabang.profile.setting

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.feature.profile.R
import com.project200.undabang.presentation.R as PresentationR

private data class SettingMenuItemData(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val onClick: (() -> Unit)? = null,
    val trailingText: String? = null,
    val showDivider: Boolean = true,
)

@Composable
fun SettingScreen(
    versionName: String,
    onNavigateBack: () -> Unit,
    onCustomerServiceClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onBlockMembersClick: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 메뉴 항목을 데이터로 묶어 반복 코드 정리
    val items =
        remember(versionName) {
            listOf(
                SettingMenuItemData(
                    iconRes = PresentationR.drawable.ic_customer_service,
                    labelRes = R.string.customer_service,
                    onClick = onCustomerServiceClick,
                ),
                SettingMenuItemData(
                    iconRes = PresentationR.drawable.ic_logout,
                    labelRes = R.string.logout,
                    onClick = onLogoutClick,
                ),
                SettingMenuItemData(
                    iconRes = PresentationR.drawable.ic_withdraw,
                    labelRes = R.string.withdraw,
                    onClick = onWithdrawClick,
                ),
                SettingMenuItemData(
                    iconRes = PresentationR.drawable.ic_block,
                    labelRes = R.string.block_members,
                    onClick = onBlockMembersClick,
                ),
                SettingMenuItemData(
                    iconRes = PresentationR.drawable.ic_document,
                    labelRes = R.string.terms,
                    onClick = onTermsClick,
                ),
                SettingMenuItemData(
                    iconRes = PresentationR.drawable.ic_document,
                    labelRes = R.string.privacy,
                    onClick = onPrivacyClick,
                ),
                SettingMenuItemData(
                    iconRes = R.drawable.ic_notification,
                    labelRes = R.string.notification,
                    onClick = onNotificationClick,
                ),
                SettingMenuItemData(
                    iconRes = PresentationR.drawable.ic_version_info,
                    labelRes = R.string.version_info,
                    trailingText = versionName,
                    showDivider = false,
                ),
            )
        }

    UndabangScaffold(
        modifier = modifier,
        topBar = {
            UndabangTopBar(
                title = stringResource(R.string.setting),
                onNavigationClick = onNavigateBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(items, key = { it.labelRes }) { item ->
                SettingMenuItem(item = item)
            }
        }
    }
}

@Composable
private fun SettingMenuItem(
    item: SettingMenuItemData,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .let { if (item.onClick != null) it.clickable(onClick = item.onClick) else it }
                    .padding(horizontal = 20.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    tint = ColorBlack,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(id = item.labelRes),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.contentRegular,
                    color = ColorBlack,
                )
            }
            if (item.trailingText != null) {
                Text(
                    text = item.trailingText,
                    style = MaterialTheme.typography.contentRegular,
                    color = ColorGray200,
                )
            }
        }
        if (item.showDivider) {
            HorizontalDivider(thickness = 0.3.dp, color = ColorBlack)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingScreenPreview() {
    AppTheme {
        SettingScreen(
            versionName = "0.8.3",
            onNavigateBack = {},
            onCustomerServiceClick = {},
            onLogoutClick = {},
            onWithdrawClick = {},
            onBlockMembersClick = {},
            onTermsClick = {},
            onPrivacyClick = {},
            onNotificationClick = {},
        )
    }
}
