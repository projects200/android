package com.project200.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GetExerciseRecordResult(
    val exerciseTitle: String,
    val exerciseDetail: String,
    val exercisePersonalType: String,
    val exerciseStartedAt: String,
    val exerciseEndedAt: String,
    val exerciseLocation: String,
    val exercisePictureUrls: List<String>
)
