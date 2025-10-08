package com.project200.data.api

import com.project200.data.dto.BaseResponse
import com.project200.data.dto.CustomTimerIdDTO
import com.project200.data.dto.EditExercisePlaceDTO
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.ExpectedScoreInfoDTO
import com.project200.data.dto.GetChattingRoomsDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExercisePlaceDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.GetIsNicknameDuplicated
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.GetMatchingMembersDto
import com.project200.data.dto.GetMatchingProfileDTO
import com.project200.data.dto.GetOpenChatUrlDTO
import com.project200.data.dto.GetProfileDTO
import com.project200.data.dto.GetProfileImageResponseDto
import com.project200.data.dto.GetScoreDTO
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.PatchCustomTimerTitleRequest
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PolicyGroupDTO
import com.project200.data.dto.PostCustomTimerRequest
import com.project200.data.dto.PostExercisePlaceDTO
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.dto.PostExerciseResponseDTO
import com.project200.data.dto.PostSignUpData
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.dto.PutProfileRequest
import com.project200.data.dto.SimpleTimerIdDTO
import com.project200.data.dto.SimpleTimerRequest
import com.project200.data.utils.AccessTokenApi
import com.project200.data.utils.AccessTokenWithFcmApi
import com.project200.data.utils.IdTokenApi
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate

interface ApiService {
    /** 인증 */
    // 로그인
    @POST("api/v1/login")
    @AccessTokenWithFcmApi
    suspend fun postLogin(): BaseResponse<Any?>

    // 로그아웃
    @POST("api/v1/logout")
    @AccessTokenWithFcmApi
    suspend fun postLogout(): BaseResponse<Any?>

    // 회원 여부 확인
    @GET("auth/v1/registration-status")
    @IdTokenApi
    suspend fun getIsRegistered(): BaseResponse<GetIsRegisteredData>

    // 회원가입
    @POST("auth/v1/sign-up")
    @IdTokenApi
    suspend fun postSignUp(
        @Body signUpRequest: PostSignUpRequest,
    ): BaseResponse<PostSignUpData>

    /** 회원 */
    // 프로필 조회
    @GET("api/v1/profile")
    @AccessTokenApi
    suspend fun getProfile(): BaseResponse<GetProfileDTO>

    // 닉네임 중복 체크
    @GET("open/v1/nicknames/check")
    suspend fun getIsNicknameDuplicated(
        @Query("nickname") nickname: String,
    ): BaseResponse<GetIsNicknameDuplicated>

    // 프로필 수정
    @PUT("api/v1/profile")
    @AccessTokenApi
    suspend fun editProfile(
        @Body profile: PutProfileRequest,
    ): BaseResponse<Any?>

    // 프로필 사진 생성
    @Multipart
    @POST("api/v1/profile-pictures")
    @AccessTokenApi
    suspend fun postProfileImage(
        @Part profilePicture: MultipartBody.Part,
    ): BaseResponse<Any?>

    // 프로필 사진 리스트 조회
    @GET("api/v1/profile-pictures")
    @AccessTokenApi
    suspend fun getProfileImages(): BaseResponse<GetProfileImageResponseDto>

    // 프로필 대표사진 수정
    @PUT("api/v1/profile-pictures/{pictureId}/represent")
    @AccessTokenApi
    suspend fun changeThumbnailImage(
        @Path("pictureId") pictureId: Long,
    ): BaseResponse<Any?>

    // 프로필 사진 삭제
    @DELETE("api/v1/profile-pictures/{pictureId}")
    @AccessTokenApi
    suspend fun deleteProfileImage(
        @Path("pictureId") pictureId: Long,
    ): BaseResponse<Any?>

    // 내 오픈채팅방 URL 조회
    @GET("api/v1/open-chats")
    @AccessTokenApi
    suspend fun getOpenChatUrl(): BaseResponse<GetOpenChatUrlDTO>

    // 내 오픈채팅방 URL 등록
    @POST("api/v1/open-chats")
    @AccessTokenApi
    suspend fun postOpenChatUrl(
        @Body url: String,
    ): BaseResponse<Any?>

    // 내 오픈채팅방 URL 수정
    @PATCH("api/v1/open-chats/{openChatId}")
    @AccessTokenApi
    suspend fun patchOpenChatUrl(
        @Path("openChatId") openChatId: Long,
        @Body url: String,
    ): BaseResponse<Any?>

    /** 운동 기록 */
    // 구간별 운동 기록 횟수 조회
    @GET("api/v1/exercises/count")
    @AccessTokenApi
    suspend fun getExerciseCountsByRange(
        @Query("start") startDate: LocalDate,
        @Query("end") endDate: LocalDate,
    ): BaseResponse<List<GetExerciseCountByRangeDTO>>

    // 운동 기록 상세 조회
    @GET("api/v1/exercises/{exerciseId}")
    @AccessTokenApi
    suspend fun getExerciseRecordDetail(
        @Path("exerciseId") recordId: Long,
    ): BaseResponse<GetExerciseRecordData>

    // 하루 운동 기록 리스트 조회
    @GET("api/v1/exercises")
    @AccessTokenApi
    suspend fun getExerciseList(
        @Query("date") date: LocalDate,
    ): BaseResponse<List<GetExerciseRecordListDto>>

    // 운동 기록 생성
    @POST("api/v1/exercises")
    @AccessTokenApi
    suspend fun postExerciseRecord(
        @Body recordRequestDto: PostExerciseRequestDto,
    ): BaseResponse<PostExerciseResponseDTO>

    // 운동 기록 수정
    @PATCH("api/v1/exercises/{exerciseId}")
    @AccessTokenApi
    suspend fun patchExerciseRecord(
        @Path("exerciseId") exerciseId: Long,
        @Body recordRequestDto: PatchExerciseRequestDto,
    ): BaseResponse<ExerciseIdDto>

    // 운동 기록 이미지 업로드
    @Multipart
    @POST("api/v1/exercises/{exerciseId}/pictures")
    @AccessTokenApi
    suspend fun postExerciseImages(
        @Path("exerciseId") exerciseId: Long,
        @Part pictures: List<MultipartBody.Part>,
    ): BaseResponse<ExerciseIdDto>

    // 운동 기록 이미지 삭제
    @DELETE("api/v1/exercises/{exerciseId}/pictures")
    @AccessTokenApi
    suspend fun deleteExerciseImages(
        @Path("exerciseId") exerciseId: Long,
        @Query("pictureIds") pictureIds: List<Long>,
    ): BaseResponse<Any?>

    // 운동 기록 삭제
    @DELETE("api/v1/exercises/{exerciseId}")
    @AccessTokenApi
    suspend fun deleteExerciseRecord(
        @Path("exerciseId") exerciseId: Long,
    ): BaseResponse<Any?>

    // 점수 조회
    @GET("api/v1/members/score")
    @AccessTokenApi
    suspend fun getScore(): BaseResponse<GetScoreDTO>

    // 예상 획득 점수 정보 조회
    @GET("api/v1/scores/expected-points-info")
    suspend fun getExpectedScoreInfo(): BaseResponse<ExpectedScoreInfoDTO>

    /** 정책 */
    // 정책 그룹 조회
    @GET("open/v1/policy-groups/{groupName}/policies")
    suspend fun getPolicyGroup(
        @Path("groupName") groupName: String,
    ): BaseResponse<PolicyGroupDTO>

    /** 타이머 */
    // 심플 타이머 조회
    @GET("api/v1/simple-timers")
    @AccessTokenApi
    suspend fun getSimpleTimers(): BaseResponse<GetSimpleTimersDTO>

    // 심플 타이머 수정
    @PATCH("api/v1/simple-timers/{simpleTimerId}")
    @AccessTokenApi
    suspend fun patchSimpleTimer(
        @Path("simpleTimerId") simpleTimerId: Long,
        @Body time: SimpleTimerRequest,
    ): BaseResponse<Any?>

    // 심플 타이머 추가
    @POST("api/v1/simple-timers")
    @AccessTokenApi
    suspend fun postSimpleTimer(
        @Body time: SimpleTimerRequest,
    ): BaseResponse<SimpleTimerIdDTO>

    // 심플 타이머 삭제
    @DELETE("api/v1/simple-timers/{simpleTimerId}")
    @AccessTokenApi
    suspend fun deleteSimpleTimer(
        @Path("simpleTimerId") simpleTimerId: Long,
    ): BaseResponse<Any?>

    // 커스텀 타이머 리스트 조회
    @GET("api/v1/custom-timers")
    @AccessTokenApi
    suspend fun getCustomTimerList(): BaseResponse<GetCustomTimerListDTO>

    // 커스텀 타이머 상세 조회
    @GET("api/v1/custom-timers/{customTimerId}")
    @AccessTokenApi
    suspend fun getCustomTimer(
        @Path("customTimerId") customTimerId: Long,
    ): BaseResponse<GetCustomTimerDetailDTO>

    // 커스텀 타이머 생성
    @POST("api/v1/custom-timers")
    @AccessTokenApi
    suspend fun postCustomTimer(
        @Body customTimer: PostCustomTimerRequest,
    ): BaseResponse<CustomTimerIdDTO>

    // 커스텀 타이머 삭제
    @DELETE("api/v1/custom-timers/{customTimerId}")
    suspend fun deleteCustomTimer(
        @Path("customTimerId") customTimerId: Long,
    ): BaseResponse<Any?>

    // 커스텀 타이머 이름 수정
    @PATCH("api/v1/custom-timers/{customTimerId}")
    suspend fun patchCustomTimerTitle(
        @Path("customTimerId") customTimerId: Long,
        @Body title: PatchCustomTimerTitleRequest,
    ): BaseResponse<CustomTimerIdDTO>

    // 커스텀 타이머 전체 수정
    @PUT("api/v1/custom-timers/{customTimerId}")
    suspend fun putCustomTimer(
        @Path("customTimerId") customTimerId: Long,
        @Body customTimer: PostCustomTimerRequest,
    ): BaseResponse<CustomTimerIdDTO>

    /** 매칭 - 회원 */
    // 매칭지도 회원들 조회
    @GET("api/v1/members")
    @AccessTokenApi
    suspend fun getMatchingMembers(): BaseResponse<List<GetMatchingMembersDto>>

    // 매칭 타 회원 프로필 조회
    @GET("api/v1/members/{memberId}/profile")
    @AccessTokenApi
    suspend fun getMatchingProfile(
        @Path("memberId") memberId: String,
    ): BaseResponse<GetMatchingProfileDTO>

    // 타 회원 캘린더 운동 기록 횟수 조회
    @GET("api/v1/members/{memberId}/calendars")
    @AccessTokenApi
    suspend fun getMatchingMemberCalendar(
        @Path("memberId") memberId: String,
        @Query("start") startDate: LocalDate,
        @Query("end") endDate: LocalDate,
    ): BaseResponse<List<GetExerciseCountByRangeDTO>>

    // 타 회원 오픈채팅방 URL 조회
    @GET("api/v1/members/{memberId}/open-chat")
    @AccessTokenApi
    suspend fun getMatchingMemberOpenChatUrl(
        @Path("memberId") memberId: String,
    ): BaseResponse<GetOpenChatUrlDTO>

    /** 매칭 - 장소 */
    // 운동 장소 리스트 조회
    @GET("api/v1/exercise-locations")
    @AccessTokenApi
    suspend fun getExercisePlaces(): BaseResponse<List<GetExercisePlaceDTO>>

    // 운동 장소 삭제
    @DELETE("api/v1/exercise-locations/{locationId}")
    @AccessTokenApi
    suspend fun deleteExercisePlace(
        @Path("locationId") locationId: Long,
    ): BaseResponse<Any?>

    // 운동 장소 등록
    @POST("api/v1/exercise-locations")
    @AccessTokenApi
    suspend fun postExercisePlace(
        @Body placeInfo: PostExercisePlaceDTO,
    ): BaseResponse<Any?>

    // 운동 장소 수정
    @PATCH("api/v1/exercise-locations/{locationId}")
    @AccessTokenApi
    suspend fun putExercisePlace(
        @Path("locationId") locationId: Long,
        @Body placeName: EditExercisePlaceDTO,
    ): BaseResponse<Any?>

    /** 채팅 */
    // 내 채팅방 목록 조회
    @GET("api/v1/chat-rooms")
    @AccessTokenApi
    suspend fun getChattingRooms(): BaseResponse<List<GetChattingRoomsDTO>>
}
