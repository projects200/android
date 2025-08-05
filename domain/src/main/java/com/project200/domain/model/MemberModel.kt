package com.project200.domain.model


data class Score(
    val score: Int,
    val maxScore: Int = 0,
    val minScore: Int = 100
)