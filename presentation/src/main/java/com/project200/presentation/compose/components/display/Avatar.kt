package com.project200.presentation.compose.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300

@Composable
fun UndabangAvatar(
    imageUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    borderColor: Color = ColorMain,
    borderWidth: Dp = 2.dp,
    contentDescription: String = "프로필 이미지",
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size - borderWidth * 2)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = null,
            error = null,
            fallback = null,
        )
    }
}

@Composable
fun UndabangPlaceholderAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = ColorGray300,
    borderColor: Color = ColorMain,
    borderWidth: Dp = 2.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color = backgroundColor, shape = CircleShape)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "기본 프로필",
            modifier = Modifier.size(size * 0.5f),
            tint = ColorGray200,
        )
    }
}

@Composable
fun UndabangCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ColorWhite300,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
        ),
    ) {
        content()
    }
}
