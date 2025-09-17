package com.project200.data.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PolicyGroupDTO(
    val groupName: String,
    val size: Int,
    val policies: List<PolicyDTO>,
)

@JsonClass(generateAdapter = true)
data class PolicyDTO(
    val policyKey: String,
    val policyValue: String,
    val policyUnit: String,
    val policyDescription: String,
)
