package com.project200.data.api

import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.dto.PostSignUpData
import com.project200.data.dto.PostSignUpRequest
import com.project200.domain.model.ExerciseRecordPicture
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate

interface ApiService {
    // 회원 여부 확인
    @GET("api/v1/members/me/registration-status")
    suspend fun getIsRegistered(): BaseResponse<GetIsRegisteredData>

    // 회원가입
    @POST("auth/v1/sign-up")
    suspend fun postSignUp(
        @Body signUpRequest: PostSignUpRequest
    ): BaseResponse<PostSignUpData>

    // 운동 기록 상세 조회
    @GET("api/v1/exercises/{exerciseId}")
    suspend fun getExerciseRecordDetail(
        @Path("exerciseId") recordId: Long
    ): BaseResponse<GetExerciseRecordData>

    // 하루 운동 기록 리스트 조회
    @GET("api/v1/exercises")
    suspend fun getExerciseList(
        @Query("date") date: LocalDate
    ): BaseResponse<List<GetExerciseRecordListDto>>

    // 운동 기록 생성
    @POST("api/v1/exercises")
    suspend fun postExerciseRecord(
        @Body recordRequestDto: PostExerciseRequestDto
    ): BaseResponse<ExerciseIdDto>

    // 운동 기록 수정
    @PATCH("api/v1/exercises/{exerciseId}")
    suspend fun patchExerciseRecord(
        @Path("exerciseId") exerciseId: Long,
        @Body recordRequestDto: PatchExerciseRequestDto
    ): BaseResponse<ExerciseIdDto>

    // 운동 기록 이미지 업로드
    @Multipart
    @POST("api/v1/exercises/{exerciseId}/pictures")
    suspend fun postExerciseImages(
        @Path("exerciseId") exerciseId: Long,
        @Part pictures: List<MultipartBody.Part>
    ): BaseResponse<ExerciseIdDto>

    // 운동 기록 이미지 삭제
    @DELETE("api/v1/exercises/{exerciseId}/pictures")
    suspend fun deleteExerciseImages(
        @Path("exerciseId") exerciseId: Long,
        @Query("pictureIds") pictureIds: List<Long>
    ): BaseResponse<Any?>
}