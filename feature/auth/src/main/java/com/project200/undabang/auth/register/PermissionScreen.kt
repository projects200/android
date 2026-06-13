package com.project200.undabang.auth.register

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.header
import com.project200.presentation.compose.theme.subtext12
import com.project200.undabang.feature.auth.R
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun PermissionScreen(
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        bottomBar = {
            PrimaryButton(
                text = stringResource(PresentationR.string.confirm),
                onClick = onNextClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
            )
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
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.header,
            )

            Spacer(Modifier.height(77.dp))

            PermissionRow(
                iconRes = R.drawable.ic_permission_location,
                title = stringResource(R.string.location_title),
                desc = stringResource(R.string.location_desc),
            )

            Spacer(Modifier.height(24.dp))

            PermissionRow(
                iconRes = R.drawable.ic_permission_notify,
                title = stringResource(R.string.notify_title),
                desc = stringResource(R.string.notify_desc),
            )

            Spacer(Modifier.height(24.dp))

            PermissionRow(
                iconRes = R.drawable.ic_permission_gallery,
                title = stringResource(R.string.gallery_title),
                desc = stringResource(R.string.gallery_desc),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    @DrawableRes iconRes: Int,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
        )
        Spacer(Modifier.width(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.contentBold,
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.subtext12,
                color = ColorGray200,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PermissionScreenPreview() {
    AppTheme {
        PermissionScreen(onNextClick = {})
    }
}
