package com.project200.data.dto

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import java.time.LocalDate
import java.time.LocalDateTime

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val nickname: String
)

@JsonClass(generateAdapter = true)
data class GetIsRegisteredData(
    val memberId: String,
    val isRegistered: Boolean
)

@JsonClass(generateAdapter = true)
data class PostSignUpRequest(
    val memberGender: String,
    val memberBday: LocalDate,
    val memberNickname: String
)

@JsonClass(generateAdapter = true)
data class PostSignUpData(
    val memberId: String,
    val memberEmail: String,
    val memberGender: String,
    val memberBday: LocalDate,
    val memberNickName: String,
    val memberDesc: String,
    val memberScore: Int?,
    val memberCreatedAt: LocalDateTime
)