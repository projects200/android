package com.project200.feature.exercise.share

import androidx.annotation.DrawableRes
import com.project200.undabang.feature.exercise.R

enum class StickerTheme(
    @DrawableRes val backgroundDrawable: Int?,
    val textColorRes: Int,
    val subTextColorRes: Int,
    val showMascot: Boolean
) {
    DARK(
        backgroundDrawable = R.drawable.bg_sticker_dark,
        textColorRes = android.R.color.white,
        subTextColorRes = com.project200.undabang.presentation.R.color.gray200,
        showMascot = true
    ),
    LIGHT(
        backgroundDrawable = R.drawable.bg_sticker_light,
        textColorRes = android.R.color.black,
        subTextColorRes = com.project200.undabang.presentation.R.color.gray100,
        showMascot = true
    ),
    MINIMAL(
        backgroundDrawable = null,
        textColorRes = android.R.color.black,
        subTextColorRes = com.project200.undabang.presentation.R.color.gray100,
        showMascot = false
    )
}
