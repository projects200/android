package com.project200.data.api

import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.ExpectedScoreInfoDTO
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.GetScoreDTO
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PolicyGroupDTO
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.dto.PostExerciseResponseDTO
import com.project200.data.dto.PostSignUpData
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.dto.FcmTokenRequest
import com.project200.data.dto.GetCustomTimerDTO
import com.project200.data.utils.AccessTokenApi
import com.project200.data.utils.IdTokenApi
import com.project200.domain.model.CustomTimer
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
    @GET("auth/v1/registration-status")
    @IdTokenApi
    suspend fun getIsRegistered(): BaseResponse<GetIsRegisteredData>

    // 회원가입
    @POST("auth/v1/sign-up")
    @IdTokenApi
    suspend fun postSignUp(
        @Body signUpRequest: PostSignUpRequest
    ): BaseResponse<PostSignUpData>

    // 구간별 운동 기록 횟수 조회
    @GET("api/v1/exercises/count")
    @AccessTokenApi
    suspend fun getExerciseCountsByRange(
        @Query("start") startDate: LocalDate,
        @Query("end") endDate: LocalDate
    ): BaseResponse<List<GetExerciseCountByRangeDTO>>

    // 운동 기록 상세 조회
    @GET("api/v1/exercises/{exerciseId}")
    @AccessTokenApi
    suspend fun getExerciseRecordDetail(
        @Path("exerciseId") recordId: Long
    ): BaseResponse<GetExerciseRecordData>

    // 하루 운동 기록 리스트 조회
    @GET("api/v1/exercises")
    @AccessTokenApi
    suspend fun getExerciseList(
        @Query("date") date: LocalDate
    ): BaseResponse<List<GetExerciseRecordListDto>>

    // 운동 기록 생성
    @POST("api/v1/exercises")
    @AccessTokenApi
    suspend fun postExerciseRecord(
        @Body recordRequestDto: PostExerciseRequestDto
    ): BaseResponse<PostExerciseResponseDTO>

    // 운동 기록 수정
    @PATCH("api/v1/exercises/{exerciseId}")
    @AccessTokenApi
    suspend fun patchExerciseRecord(
        @Path("exerciseId") exerciseId: Long,
        @Body recordRequestDto: PatchExerciseRequestDto
    ): BaseResponse<ExerciseIdDto>

    // 운동 기록 이미지 업로드
    @Multipart
    @POST("api/v1/exercises/{exerciseId}/pictures")
    @AccessTokenApi
    suspend fun postExerciseImages(
        @Path("exerciseId") exerciseId: Long,
        @Part pictures: List<MultipartBody.Part>
    ): BaseResponse<ExerciseIdDto>

    // 운동 기록 이미지 삭제
    @DELETE("api/v1/exercises/{exerciseId}/pictures")
    @AccessTokenApi
    suspend fun deleteExerciseImages(
        @Path("exerciseId") exerciseId: Long,
        @Query("pictureIds") pictureIds: List<Long>
    ): BaseResponse<Any?>

    // 운동 기록 삭제
    @DELETE("api/v1/exercises/{exerciseId}")
    @AccessTokenApi
    suspend fun deleteExerciseRecord(
        @Path("exerciseId") exerciseId: Long
    ): BaseResponse<Any?>

    // 점수 조회
    @GET("api/v1/members/score")
    @AccessTokenApi
    suspend fun getScore(): BaseResponse<GetScoreDTO>

    // 예상 획득 점수 정보 조회
    @GET("api/v1/scores/expected-points-info")
    suspend fun getExpectedScoreInfo(): BaseResponse<ExpectedScoreInfoDTO>

    // 정책 그룹 조회
    @GET("open/v1/policy-groups/{groupName}/policies")
    suspend fun getPolicyGroup(
        @Path("groupName") groupName: String
    ): BaseResponse<PolicyGroupDTO>

    // FCM 토큰 전송
    // TODO: 토큰 전송 api 연결
    @POST("")
    suspend fun sendFcmToken(@Body fcmTokenRequest: FcmTokenRequest): BaseResponse<Unit>

    // 커스텀 타이머 리스트 조회
    @GET("api/v1/custom-timers")
    @AccessTokenApi
    suspend fun getCustomTimers(): BaseResponse<GetCustomTimerDTO>
}