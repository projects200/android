package com.project200.feature.matching.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBackground
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray100
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.presentation.compose.theme.header
import com.project200.presentation.compose.theme.mediumBold
import com.project200.undabang.feature.matching.R

@Composable
fun MatchingGuideScreen(
    onSkipClick: () -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        topBar = { Toolbar(onSkipClick = onSkipClick) },
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.guide_start),
                onClick = onStartClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 40.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))

            Box(
                modifier =
                    Modifier
                        .size(150.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ColorBackground)
                        .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_guide_character),
                    contentDescription = null,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.guide_title),
                style = MaterialTheme.typography.header,
                color = ColorBlack,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.guide_content),
                style = MaterialTheme.typography.mediumBold,
                color = ColorGray100,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(30.dp))

            GuideBeforeBox()
        }
    }
}

@Composable
private fun Toolbar(
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(60.dp),
    ) {
        Text(
            text = stringResource(R.string.guide_toolbar),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = stringResource(R.string.guide_skip),
            style = MaterialTheme.typography.contentRegular,
            color = ColorGray200,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkipClick,
                    )
                    .padding(4.dp),
        )
    }
}

@Composable
private fun GuideBeforeBox(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ColorBackground),
    ) {
        Spacer(Modifier.height(25.dp))
        Text(
            text = stringResource(R.string.guide_before_title),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
            modifier = Modifier.padding(start = 20.dp),
        )
        Spacer(Modifier.height(25.dp))
        GuideItem(
            iconRes = R.drawable.ic_guide_1,
            text = stringResource(R.string.guide_before_content_1),
        )
        Spacer(Modifier.height(25.dp))
        GuideItem(
            iconRes = R.drawable.ic_guide_2,
            text = stringResource(R.string.guide_before_content_2),
        )
        Spacer(Modifier.height(25.dp))
        GuideItem(
            iconRes = R.drawable.ic_guide_3,
            text = stringResource(R.string.guide_before_content_3),
        )
        Spacer(Modifier.height(25.dp))
    }
}

@Composable
private fun GuideItem(
    @DrawableRes iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.mediumBold,
            color = ColorBlack,
            fontSize = 14.sp,
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MatchingGuideScreenPreview() {
    AppTheme {
        MatchingGuideScreen(
            onSkipClick = {},
            onStartClick = {},
        )
    }
}
