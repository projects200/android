package com.project200.common.constants

object RuleConstants {
    const val MAX_IMAGE = 5
    const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    val ALLOWED_EXTENSIONS = setOf("jpeg", "png", "jpg")

    const val MIN_YEAR = 1945

    const val SCORE_HIGH_LEVEL = 0.70
    const val SCORE_MIDDLE_LEVEL = 0.33
}