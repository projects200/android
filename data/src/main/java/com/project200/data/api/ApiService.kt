package com.project200.data.api

import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.dto.PostSignUpData
import com.project200.data.dto.PostSignUpRequest
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {
    // 회원 여부 확인
    @GET("v1/members/me/registration-status")
    suspend fun getIsRegistered(): BaseResponse<GetIsRegisteredData>

    // 회원가입
    @POST("auth/sign-up/v1")
    suspend fun postSignUp(
        @Body signUpRequest: PostSignUpRequest
    ): BaseResponse<PostSignUpData>

    // 운동 기록 상세 조회
    @GET("v1/exercises/{recordId}")
    suspend fun getExerciseRecordDetail(
        @Path("recordId") recordId: Long
    ): BaseResponse<GetExerciseRecordData>

    @POST("v1/exercises")
    suspend fun postExerciseRecord(
        @Body recordRequestDto: PostExerciseRequestDto
    ): BaseResponse<Long>

    @Multipart
    @POST("v1/exercises/{exerciseId}/pictures")
    suspend fun postExerciseImages(
        @Path("exerciseId") exerciseId: Long,
        @Part pictures: List<MultipartBody.Part>
    ): BaseResponse<Long>
}