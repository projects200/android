package com.project200.undabang.auth.register

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorGray100
import com.project200.presentation.compose.theme.header
import com.project200.presentation.compose.theme.subtext14
import com.project200.undabang.feature.auth.R
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun TermsScreen(
    serviceChecked: Boolean,
    privacyChecked: Boolean,
    isAllRequiredChecked: Boolean,
    onToggleService: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onServiceClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        bottomBar = {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                TermsRow(
                    title = stringResource(R.string.terms_required_service),
                    checked = serviceChecked,
                    onTitleClick = onServiceClick,
                    onToggle = onToggleService,
                )
                Spacer(Modifier.height(16.dp))
                TermsRow(
                    title = stringResource(R.string.terms_required_privacy),
                    checked = privacyChecked,
                    onTitleClick = onPrivacyClick,
                    onToggle = onTogglePrivacy,
                )
                Spacer(Modifier.height(77.dp))
                PrimaryButton(
                    text = stringResource(PresentationR.string.confirm),
                    onClick = onNextClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAllRequiredChecked,
                )
                Spacer(Modifier.height(32.dp))
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(114.dp))

            Text(
                text = stringResource(R.string.terms_title),
                style = MaterialTheme.typography.header,
            )
        }
    }
}

@Composable
private fun TermsRow(
    title: String,
    checked: Boolean,
    onTitleClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onTitleClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtext14,
                color = ColorGray100,
            )
            Spacer(Modifier.size(4.dp))
            Icon(
                painter = painterResource(PresentationR.drawable.ic_arrow_right),
                contentDescription = null,
                tint = Color.Unspecified,
            )
        }
        Icon(
            painter =
                painterResource(
                    if (checked) PresentationR.drawable.ic_select_check else R.drawable.ic_uncheck,
                ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.clickable(onClick = onToggle),
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun TermsScreenPreview() {
    AppTheme {
        var service by remember { mutableStateOf(false) }
        var privacy by remember { mutableStateOf(false) }
        TermsScreen(
            serviceChecked = service,
            privacyChecked = privacy,
            isAllRequiredChecked = service && privacy,
            onToggleService = { service = !service },
            onTogglePrivacy = { privacy = !privacy },
            onServiceClick = {},
            onPrivacyClick = {},
            onNextClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun TermsScreenAllCheckedPreview() {
    AppTheme {
        TermsScreen(
            serviceChecked = true,
            privacyChecked = true,
            isAllRequiredChecked = true,
            onToggleService = {},
            onTogglePrivacy = {},
            onServiceClick = {},
            onPrivacyClick = {},
            onNextClick = {},
        )
    }
}
