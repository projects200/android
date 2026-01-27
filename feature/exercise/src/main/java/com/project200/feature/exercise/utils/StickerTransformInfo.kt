package com.project200.feature.exercise.utils

/**
 * 스티커 변환 정보
 * 모든 값은 부모 뷰 크기 대비 비율로 저장되어 해상도 독립적
 *
 * @property translationXRatio X 위치 (부모 너비 대비 비율, 0.0 = 왼쪽 끝)
 * @property translationYRatio Y 위치 (부모 높이 대비 비율, 0.0 = 위쪽 끝)
 * @property stickerWidthRatio 스티커 너비 (부모 너비 대비 비율, 0.45 = 45%)
 */
data class StickerTransformInfo(
    val translationXRatio: Float,
    val translationYRatio: Float,
    val stickerWidthRatio: Float
)