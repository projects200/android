package com.project200.undabang.profile.mypage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.project200.domain.model.ProfileImage
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.feature.profile.R
import com.project200.undabang.presentation.R as PresentationR

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileImageDetailScreen(
    images: List<ProfileImage>,
    onBackClick: () -> Unit,
    onChangeThumbnail: (Long) -> Unit,
    onSaveImage: (ProfileImage) -> Unit,
    onDeleteImage: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { images.size })
    val currentImage = images.getOrNull(pagerState.currentPage) ?: return
    val showMenu = currentImage.id != ProfileImageDetailViewModel.EMPTY_ID

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorBlack),
    ) {
        ProfileImageDetailToolbar(
            currentPage = pagerState.currentPage + 1,
            totalCount = images.size,
            showMenu = showMenu,
            isThumbnailVisible = pagerState.currentPage != 0,
            onBackClick = onBackClick,
            onChangeThumbnailClick = { onChangeThumbnail(currentImage.id) },
            onSaveClick = { onSaveImage(currentImage) },
            onDeleteClick = { onDeleteImage(currentImage.id) },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val image = images[page]
            AsyncImage(
                model = image.url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                error = painterResource(PresentationR.drawable.ic_profile_default),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProfileImageDetailToolbar(
    currentPage: Int,
    totalCount: Int,
    showMenu: Boolean,
    isThumbnailVisible: Boolean,
    onBackClick: () -> Unit,
    onChangeThumbnailClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(PresentationR.drawable.ic_arrow_back),
                contentDescription = null,
                tint = ColorWhite300,
            )
        }

        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentPage.toString(),
                style = MaterialTheme.typography.contentRegular,
                color = ColorWhite300,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.profile_image_detail_divider),
                color = ColorGray200,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                text = totalCount.toString(),
                style = MaterialTheme.typography.contentRegular,
                color = ColorGray200,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
        }

        Box {
            var menuExpanded by remember { mutableStateOf(false) }
            if (showMenu) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(PresentationR.drawable.ic_menu),
                        contentDescription = null,
                        tint = ColorWhite300,
                    )
                }
            } else {
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        painter = painterResource(PresentationR.drawable.ic_menu),
                        contentDescription = null,
                        tint = Color.Transparent,
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (isThumbnailVisible) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.change_thumbnail)) },
                        onClick = {
                            menuExpanded = false
                            onChangeThumbnailClick()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.save_profile_image)) },
                    onClick = {
                        menuExpanded = false
                        onSaveClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(PresentationR.string.delete)) },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick()
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageDetailScreenPreview() {
    AppTheme {
        ProfileImageDetailScreen(
            images =
                listOf(
                    ProfileImage(id = 1L, url = ""),
                    ProfileImage(id = 2L, url = ""),
                    ProfileImage(id = 3L, url = ""),
                ),
            onBackClick = {},
            onChangeThumbnail = {},
            onSaveImage = {},
            onDeleteImage = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageDetailScreenEmptyPreview() {
    AppTheme {
        ProfileImageDetailScreen(
            images = listOf(ProfileImage(id = ProfileImageDetailViewModel.EMPTY_ID, url = "")),
            onBackClick = {},
            onChangeThumbnail = {},
            onSaveImage = {},
            onDeleteImage = {},
        )
    }
}
