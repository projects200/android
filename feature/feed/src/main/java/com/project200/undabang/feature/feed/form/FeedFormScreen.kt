package com.project200.undabang.feature.feed.form

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.project200.domain.model.UserProfile
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.subtext14
import com.project200.undabang.feature.feed.R
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun FeedFormScreen(
    isEditMode: Boolean,
    userProfile: UserProfile?,
    content: String,
    selectedTypeName: String?,
    registeredImages: List<RegisteredImage>,
    newImages: List<Uri>,
    isLoading: Boolean,
    onContentChange: (String) -> Unit,
    onDabangSelectClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onRemoveRegisteredImage: (Long) -> Unit,
    onRemoveNewImage: (Uri) -> Unit,
    onCompleteClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        topBar = {
            UndabangTopBar(
                title =
                    stringResource(
                        if (isEditMode) R.string.feed_form_edit_title else R.string.feed_form_title,
                    ),
                onNavigationClick = onBackClick,
                actions = {
                    PrimaryButton(
                        text = stringResource(PresentationR.string.complete),
                        onClick = onCompleteClick,
                        isCompact = true,
                        modifier = Modifier.padding(end = 20.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                ProfileRow(
                    userProfile = userProfile,
                    selectedTypeName = selectedTypeName,
                    onDabangSelectClick = onDabangSelectClick,
                )

                Spacer(Modifier.height(16.dp))

                BasicTextField(
                    value = content,
                    onValueChange = onContentChange,
                    textStyle =
                        TextStyle(
                            fontSize = 14.sp,
                            color = ColorBlack,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 60.dp)
                            .defaultMinSize(minHeight = 60.dp),
                    decorationBox = { inner ->
                        if (content.isEmpty()) {
                            Text(
                                text = stringResource(R.string.feed_form_content_hint),
                                style = MaterialTheme.typography.subtext14,
                                color = ColorGray200,
                            )
                        }
                        inner()
                    },
                )

                Spacer(Modifier.height(12.dp))

                AddImageButton(
                    onClick = onAddImageClick,
                    modifier = Modifier.padding(start = 60.dp),
                )

                Spacer(Modifier.height(16.dp))

                if (registeredImages.isNotEmpty() || newImages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 60.dp),
                    ) {
                        items(registeredImages, key = { "existing-${it.imageId}" }) { image ->
                            ImageCard(
                                model = image.imageUrl,
                                onDelete = { onRemoveRegisteredImage(image.imageId) },
                            )
                        }
                        items(newImages, key = { "new-$it" }) { uri ->
                            ImageCard(
                                model = uri,
                                onDelete = { onRemoveNewImage(uri) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    userProfile: UserProfile?,
    selectedTypeName: String?,
    onDabangSelectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = userProfile?.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(PresentationR.drawable.ic_profile_default),
            error = painterResource(PresentationR.drawable.ic_profile_default),
            fallback = painterResource(PresentationR.drawable.ic_profile_default),
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = userProfile?.nickname.orEmpty(),
                style = MaterialTheme.typography.contentBold,
                fontSize = 16.sp,
                color = ColorBlack,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDabangSelectClick,
                        ),
            ) {
                Icon(
                    painter = painterResource(PresentationR.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = ColorGray200,
                )
                Spacer(Modifier.width(8.dp))
                val typeLabel = selectedTypeName ?: stringResource(R.string.feed_select_dabang)
                val typeColor = if (selectedTypeName != null) ColorBlack else ColorGray200
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.subtext14,
                    color = typeColor,
                )
            }
        }
    }
}

@Composable
private fun AddImageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_add_image),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.feed_form_add_image),
            style = MaterialTheme.typography.subtext14,
            color = ColorGray200,
        )
    }
}

@Composable
private fun ImageCard(
    model: Any?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(end = 12.dp),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            placeholder = painterResource(R.drawable.ic_feed_image_placeholder),
            error = painterResource(R.drawable.ic_feed_image_placeholder),
            modifier =
                Modifier
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorWhite300),
        )
        Icon(
            painter = painterResource(R.drawable.ic_delete_circle),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun FeedFormScreenPreview() {
    AppTheme {
        FeedFormScreen(
            isEditMode = false,
            userProfile =
                UserProfile(
                    profileThumbnailUrl = null,
                    profileImageUrl = null,
                    nickname = "운다방 테스터",
                    gender = "M",
                    birthDate = "1990-01-01",
                    bio = null,
                    yearlyExerciseDays = 0,
                    exerciseCountInLast30Days = 0,
                    exerciseScore = 0,
                    preferredExercises = emptyList(),
                ),
            content = "오늘 운동 다녀왔어요!",
            selectedTypeName = "헬스",
            registeredImages = emptyList(),
            newImages = emptyList(),
            isLoading = false,
            onContentChange = {},
            onDabangSelectClick = {},
            onAddImageClick = {},
            onRemoveRegisteredImage = {},
            onRemoveNewImage = {},
            onCompleteClick = {},
            onBackClick = {},
        )
    }
}
