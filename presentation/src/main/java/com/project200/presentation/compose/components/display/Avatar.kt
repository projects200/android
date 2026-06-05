package com.project200.presentation.compose.components.display

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorMain
import com.project200.undabang.presentation.R

/**
 * 원형 프로필 아바타
 * imageUrl이 null/blank거나 로딩 실패 시 placeholderRes drawable을 표시합니다
 */
@Composable
fun UndabangAvatar(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    borderColor: Color = ColorMain,
    borderWidth: Dp = 2.dp,
    placeholderBackground: Color = ColorGray300,
    contentDescription: String = "프로필 이미지",
    @DrawableRes placeholderRes: Int = R.drawable.ic_profile_default,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(color = placeholderBackground, shape = CircleShape)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val innerModifier =
            Modifier
                .size(size - borderWidth * 2)
                .clip(CircleShape)

        if (imageUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(placeholderRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = innerModifier,
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                placeholder = painterResource(placeholderRes),
                error = painterResource(placeholderRes),
                fallback = painterResource(placeholderRes),
                contentScale = ContentScale.Crop,
                modifier = innerModifier,
            )
        }
    }
}
