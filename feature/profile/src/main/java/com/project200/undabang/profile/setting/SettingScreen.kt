package com.project200.undabang.profile.setting

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.feature.profile.R
import com.project200.undabang.presentation.R as PresentationR

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
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorWhite300),
    ) {
        com.project200.presentation.compose.components.layout.UndabangTopBar(
            title = stringResource(R.string.setting),
            onNavigationClick = onNavigateBack,
        )

        SettingMenuItem(
            iconRes = PresentationR.drawable.ic_customer_service,
            labelRes = R.string.customer_service,
            onClick = onCustomerServiceClick,
        )
        SettingMenuItem(
            iconRes = PresentationR.drawable.ic_logout,
            labelRes = R.string.logout,
            onClick = onLogoutClick,
        )
        SettingMenuItem(
            iconRes = PresentationR.drawable.ic_withdraw,
            labelRes = R.string.withdraw,
            onClick = onWithdrawClick,
        )
        SettingMenuItem(
            iconRes = PresentationR.drawable.ic_block,
            labelRes = R.string.block_members,
            onClick = onBlockMembersClick,
        )
        SettingMenuItem(
            iconRes = PresentationR.drawable.ic_document,
            labelRes = R.string.terms,
            onClick = onTermsClick,
        )
        SettingMenuItem(
            iconRes = PresentationR.drawable.ic_document,
            labelRes = R.string.privacy,
            onClick = onPrivacyClick,
        )
        SettingMenuItem(
            iconRes = R.drawable.ic_notification,
            labelRes = R.string.notification,
            onClick = onNotificationClick,
        )
        SettingMenuItem(
            iconRes = PresentationR.drawable.ic_version_info,
            labelRes = R.string.version_info,
            trailingText = versionName,
            showDivider = false,
        )
    }
}

@Composable
private fun SettingMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                    .padding(horizontal = 20.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = ColorBlack,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(id = labelRes),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.contentRegular,
                    color = ColorBlack,
                )
            }
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.contentRegular,
                    color = ColorGray200,
                )
            }
        }
        if (showDivider) {
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
