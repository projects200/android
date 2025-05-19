package com.project200.domain.model

data class ExerciseRecord(
    val title: String,
    val detail: String,
    val personalType: String,
    val startedAt: String,
    val endedAt: String,
    val location: String,
    val pictureUrls: List<String>
)
