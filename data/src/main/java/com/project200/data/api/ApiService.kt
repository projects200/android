package com.project200.data.api

import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.PostSignUpData
import com.project200.data.dto.PostSignUpRequest
import retrofit2.http.*

interface ApiService {

    // 회원가입 여부 확인
    @GET("v1/members/me/registration-status")
    suspend fun getIsRegistered(): BaseResponse<GetIsRegisteredData>

    // 회원가입
    @POST("v1/auth/sign-up")
    suspend fun postSignUp(
        @Body signUpRequest: PostSignUpRequest
    ): BaseResponse<PostSignUpData>
}