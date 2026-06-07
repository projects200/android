package com.project200.feature.matching.place

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.project200.domain.model.ExercisePlace
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorErrorRed
import com.project200.presentation.compose.theme.ColorGray100
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.presentation.compose.theme.subtext12
import com.project200.undabang.feature.matching.R
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun ExercisePlaceScreen(
    places: List<ExercisePlace>,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (ExercisePlace) -> Unit,
    onDeleteClick: (ExercisePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorWhite300),
    ) {
        UndabangTopBar(
            title = stringResource(R.string.exercise_place),
            onNavigationClick = onBackClick,
            actions = {
                AddPlaceButton(
                    onClick = onAddClick,
                    modifier = Modifier.padding(end = 20.dp),
                )
            },
        )

        if (places.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.empty_exercise_place),
                    style = MaterialTheme.typography.contentRegular,
                    color = ColorGray200,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Spacer(Modifier.height(32.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = places, key = { it.id }) { place ->
                    ExercisePlaceItem(
                        place = place,
                        onEditClick = { onEditClick(place) },
                        onDeleteClick = { onDeleteClick(place) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPlaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(width = 62.dp, height = 33.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(ColorMain)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.exercise_place_add),
            style = MaterialTheme.typography.contentBold,
            color = ColorWhite300,
        )
    }
}

@Composable
private fun ExercisePlaceItem(
    place: ExercisePlace,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(100.dp)
                .drawBehind {
                    val strokePx = 1.dp.toPx()
                    drawLine(
                        color = ColorGray300,
                        start = Offset(0f, size.height - strokePx / 2),
                        end = Offset(size.width, size.height - strokePx / 2),
                        strokeWidth = strokePx,
                    )
                }
                .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_member_marker),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ColorBlack),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.contentBold,
                color = ColorBlack,
            )
            Text(
                text = place.address,
                style = MaterialTheme.typography.subtext12,
                color = ColorGray100,
            )
        }
        MenuButton(
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
        )
    }
}

@Composable
private fun MenuButton(
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_menu),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(PresentationR.string.edit),
                        color = ColorBlack,
                    )
                },
                leadingIcon = {
                    Image(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onEditClick()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(PresentationR.string.delete),
                        color = ColorErrorRed,
                    )
                },
                leadingIcon = {
                    Image(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onDeleteClick()
                },
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ExercisePlaceScreenPreview() {
    AppTheme {
        ExercisePlaceScreen(
            places =
                listOf(
                    ExercisePlace(1L, "운다방 헬스장", "서울특별시 강남구 테헤란로 123", 37.5, 127.0),
                    ExercisePlace(2L, "한강공원 수영장", "서울특별시 영등포구 여의도동", 37.5, 127.0),
                ),
            onBackClick = {},
            onAddClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ExercisePlaceScreenEmptyPreview() {
    AppTheme {
        ExercisePlaceScreen(
            places = emptyList(),
            onBackClick = {},
            onAddClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}
