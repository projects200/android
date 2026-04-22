package com.project200.presentation.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.project200.undabang.presentation.R

val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_regular, FontWeight.Normal),
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.4.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 21.6.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.8.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 12.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PretendardFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    ),
)

// XML styles.xml 이름으로 접근할 수 있는 확장 프로퍼티
// 사용: MaterialTheme.typography.header
val Typography.header: TextStyle get() = displayLarge
val Typography.mediumBold: TextStyle get() = displayMedium
val Typography.contentBold: TextStyle get() = titleMedium
val Typography.contentRegular: TextStyle get() = bodyLarge
val Typography.subtext14: TextStyle get() = bodyMedium
val Typography.subtext12: TextStyle get() = bodySmall
val Typography.subtext10: TextStyle get() = labelSmall
