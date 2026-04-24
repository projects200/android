package com.project200.presentation.compose.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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

/**
 * 원형 프로필 아바타. 이미지 로드 실패·URL 부재를 내부에서 폴백 처리한다.
 *
 * [imageUrl] 이 null 또는 blank 면 Person 아이콘 placeholder 를 보여주므로,
 * 호출부가 "프로필 설정 여부" 로 분기하지 않아도 된다. 크기·테두리 색은 목록·상세 등 상황에 맞게 조절.
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
        if (imageUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.5f),
                tint = ColorGray200,
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier =
                    Modifier
                        .size(size - borderWidth * 2)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
