package com.project200.feature.matching.place

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.input.TextFieldVariant
import com.project200.presentation.compose.components.input.UndabangTextField
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.feature.matching.R

@Composable
fun ExercisePlaceRegisterScreen(
    placeName: String,
    placeAddress: String,
    placeNameInput: String,
    onPlaceNameChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
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
            title = stringResource(R.string.exercise_place_register_title),
            onNavigationClick = onBackClick,
        )

        PlaceInfoSection(
            placeName = placeName,
            placeAddress = placeAddress,
        )

        Spacer(Modifier.height(30.dp))

        Text(
            text = stringResource(R.string.place_name_title),
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(4.dp))

        UndabangTextField(
            value = placeNameInput,
            onValueChange = onPlaceNameChange,
            hint = stringResource(R.string.place_name_hint),
            variant = TextFieldVariant.Outlined,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp),
        )

        Spacer(Modifier.height(30.dp))

        NoticeSection()

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.exercise_place_register_complete),
            onClick = onRegisterClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PlaceInfoSection(
    placeName: String,
    placeAddress: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_member_marker),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ColorBlack),
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = placeName,
                style = MaterialTheme.typography.contentBold,
                color = ColorBlack,
            )
            Text(
                text = placeAddress,
                style = MaterialTheme.typography.contentRegular,
                color = ColorBlack,
            )
        }
    }
}

@Composable
private fun NoticeSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.place_name_notice_title),
                color = ColorGray200,
                fontSize = 12.sp,
                style = MaterialTheme.typography.contentBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.place_name_notice_content),
            color = ColorGray200,
            fontSize = 12.sp,
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ExercisePlaceRegisterScreenPreview() {
    AppTheme {
        var name by remember { mutableStateOf("운다방 체육관") }
        ExercisePlaceRegisterScreen(
            placeName = "체육관",
            placeAddress = "서울특별시 강남구 테헤란로 123",
            placeNameInput = name,
            onPlaceNameChange = { name = it },
            onRegisterClick = {},
            onBackClick = {},
        )
    }
}
