package com.project200.feature.exercise.utils

import com.project200.domain.model.ExerciseRecord
import com.project200.feature.exercise.share.StickerTheme

data class ShareEventData(
    val record: ExerciseRecord,
    val theme: StickerTheme,
    val transformInfo: StickerTransformInfo,
)

data class StickerState(
    val record: ExerciseRecord,
    val theme: StickerTheme,
)
