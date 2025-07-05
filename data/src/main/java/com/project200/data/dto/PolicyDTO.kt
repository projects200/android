package com.project200.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScorePolicyDTO(
    val policyKey: String,
    val policyValue: Int,
    val policyUnit: String,
)