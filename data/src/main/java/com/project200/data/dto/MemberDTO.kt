package com.project200.data.dto

import com.squareup.moshi.JsonClass
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class GetScoreDTO(
    val memberId: String,
    val memberScore: Int
)