package com.project200.data // 실제 테스트 파일 패키지

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.PictureData
import com.project200.data.impl.ExerciseRecordRepositoryImpl
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response as RetrofitResponse
import java.io.IOException
import java.time.LocalDateTime
import java.util.NoSuchElementException

@ExperimentalCoroutinesApi
class ExerciseRecordRepositoryImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockApiService: ApiService

    private val testIoDispatcher: CoroutineDispatcher = StandardTestDispatcher()
    private lateinit var repository: ExerciseRecordRepositoryImpl

    // Moshi 인스턴스 (테스트 클래스 멤버로 선언)
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // Kotlin 지원 추가
        .build()

    private val now: LocalDateTime = LocalDateTime.now()

    private val samplePictureDataList = listOf(
        PictureData(1L, "https://s3/picture/pic1.jpg", "find", "jpg"),
        PictureData(2L, "https://s3/picture/pic2.jpg", "find2", "jpg")
    )

    private val sampleDtoWithPictures = GetExerciseRecordData(
        exerciseTitle = "운동제목",
        exerciseDetail = "운동내용",
        exercisePersonalType = "운동종류",
        exerciseStartedAt = now.minusHours(1),
        exerciseEndedAt = now,
        exerciseLocation = "운동위치",
        pictureDataList = samplePictureDataList
    )

    private val sampleDtoWithoutPictures = GetExerciseRecordData(
        exerciseTitle = "사진 없는 운동",
        exerciseDetail = "운동내용",
        exercisePersonalType = "운동종류",
        exerciseStartedAt = now.minusHours(2),
        exerciseEndedAt = now.minusHours(1),
        exerciseLocation = "다른 운동위치",
        pictureDataList = emptyList()
    )

    @Before
    fun setUp() {
        repository = ExerciseRecordRepositoryImpl(mockApiService, testIoDispatcher)
    }

    @Test
    fun `getExerciseDetail - API 성공 (succeed=true), 사진 있는 데이터`() = runTest(testIoDispatcher) {
        val recordId = 1
        val apiResponse = BaseResponse(
            succeed = true, code = "SUCCESS", message = "요청이 성공적으로 처리되었습니다.", data = sampleDtoWithPictures
        )
        coEvery { mockApiService.getExerciseRecordDetail(recordId) } returns apiResponse

        val result = repository.getExerciseDetail(recordId)

        coVerify(exactly = 1) { mockApiService.getExerciseRecordDetail(recordId) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
        val domainData = (result as BaseResult.Success<ExerciseRecord>).data
        assertThat(domainData.title).isEqualTo(sampleDtoWithPictures.exerciseTitle)
        assertThat(domainData.pictureUrls).isEqualTo(samplePictureDataList.map { it.pictureUrl })
    }

    @Test
    fun `getExerciseDetail - API 성공 (succeed=true), 사진 없는 데이터 (pictureDataList=null)`() = runTest(testIoDispatcher) {
        val recordId = 2
        val apiResponse = BaseResponse(
            succeed = true, code = "SUCCESS", message = "요청이 성공적으로 처리되었습니다.", data = sampleDtoWithoutPictures
        )
        coEvery { mockApiService.getExerciseRecordDetail(recordId) } returns apiResponse

        val result = repository.getExerciseDetail(recordId)

        coVerify(exactly = 1) { mockApiService.getExerciseRecordDetail(recordId) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
        val domainData = (result as BaseResult.Success<ExerciseRecord>).data
        assertThat(domainData.title).isEqualTo(sampleDtoWithoutPictures.exerciseTitle)
        assertThat(domainData.pictureUrls).isEmpty()
    }

    @Test
    fun `getExerciseDetail - API 성공 (succeed=true), 응답의 data 필드 자체가 null (NO_DATA)`() = runTest(testIoDispatcher) {
        val recordId = 4
        val apiResponse = BaseResponse<GetExerciseRecordData>(
            succeed = true, code = "SUCCESS_BUT_DATA_IS_NULL", message = "성공했으나 BaseResponse의 data가 null", data = null
        )
        coEvery { mockApiService.getExerciseRecordDetail(recordId) } returns apiResponse

        val result = repository.getExerciseDetail(recordId)

        coVerify(exactly = 1) { mockApiService.getExerciseRecordDetail(recordId) }
        assertThat(result).isInstanceOf(BaseResult.Error::class.java)
        val errorResult = result as BaseResult.Error
        assertThat(errorResult.errorCode).isEqualTo("NO_DATA")
        assertThat(errorResult.message).isEqualTo("성공했으나 BaseResponse의 data가 null")
        assertThat(errorResult.cause).isInstanceOf(NoSuchElementException::class.java)
    }

    // createHttpExceptionFromString 헬퍼 함수
    private fun createHttpExceptionFromString(
        httpStatusCode: Int,
        errorJsonString: String
    ): HttpException {
        val responseBody = errorJsonString.toResponseBody("application/json".toMediaTypeOrNull())
        return HttpException(RetrofitResponse.error<Any>(httpStatusCode, responseBody))
    }

    @Test
    fun `getExerciseDetail - 접근 권한 없음 (AUTHORIZATION_DENIED, HTTP 403)`() = runTest(testIoDispatcher) {
        val recordId = 10
        val errorResponseObject = BaseResponse<Any?>(
            succeed = false,
            code = "AUTHORIZATION_DENIED",
            message = "접근 권한이 없습니다.",
            data = null
        )

        val type = Types.newParameterizedType(BaseResponse::class.java, Object::class.java)
        val adapter = moshi.adapter<BaseResponse<Any?>>(type)
        val errorJsonString = adapter.toJson(errorResponseObject) // JSON 문자열로 직렬화

        val httpException = createHttpExceptionFromString(403, errorJsonString)
        coEvery { mockApiService.getExerciseRecordDetail(recordId) } throws httpException

        val result = repository.getExerciseDetail(recordId)

        assertThat(result).isInstanceOf(BaseResult.Error::class.java)
        val errorResult = result as BaseResult.Error
        assertThat(errorResult.errorCode).isEqualTo("403")
        assertThat(errorResult.message).isEqualTo("HTTP 오류 403: $errorJsonString")
        assertThat(errorResult.cause).isEqualTo(httpException)
    }

    @Test
    fun `getExerciseDetail - 잘못된 입력 값 (INVALID_INPUT_VALUE, HTTP 400)`() = runTest(testIoDispatcher) {
        val recordId = -1
        val errorDataMap = mapOf("findMemberExerciseRecord.recordId" to "올바른 Record를 다시 입력해주세요")
        val errorResponseObject = BaseResponse<Map<String, String>>(
            succeed = false,
            code = "INVALID_INPUT_VALUE",
            message = "엔티티 유효성 검증에 실패했습니다.",
            data = errorDataMap
        )

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val responseType = Types.newParameterizedType(BaseResponse::class.java, mapType)
        val adapter = moshi.adapter<BaseResponse<Map<String, String>>>(responseType)
        val errorJsonString = adapter.toJson(errorResponseObject)

        val httpException = createHttpExceptionFromString(400, errorJsonString)
        coEvery { mockApiService.getExerciseRecordDetail(recordId) } throws httpException

        val result = repository.getExerciseDetail(recordId)

        assertThat(result).isInstanceOf(BaseResult.Error::class.java)
        val errorResult = result as BaseResult.Error
        assertThat(errorResult.errorCode).isEqualTo("400")
        assertThat(errorResult.message).isEqualTo("HTTP 오류 400: $errorJsonString")
        assertThat(errorResult.cause).isEqualTo(httpException)
    }

    @Test
    fun `getExerciseDetail - 운동 기록 없음 (EXERCISE_RECORD_NOT_FOUND, HTTP 404)`() = runTest(testIoDispatcher) {
        val recordId = 99999
        val errorResponseObject = BaseResponse<Any?>( // data가 null
            succeed = false,
            code = "EXERCISE_RECORD_NOT_FOUND",
            message = "운동 기록을 찾을 수 없습니다.",
            data = null
        )

        val type = Types.newParameterizedType(BaseResponse::class.java, Object::class.java)
        val adapter = moshi.adapter<BaseResponse<Any?>>(type)
        val errorJsonString = adapter.toJson(errorResponseObject)

        val httpException = createHttpExceptionFromString(404, errorJsonString)
        coEvery { mockApiService.getExerciseRecordDetail(recordId) } throws httpException

        val result = repository.getExerciseDetail(recordId)

        assertThat(result).isInstanceOf(BaseResult.Error::class.java)
        val errorResult = result as BaseResult.Error
        assertThat(errorResult.errorCode).isEqualTo("404")
        assertThat(errorResult.message).isEqualTo("HTTP 오류 404: $errorJsonString")
        assertThat(errorResult.cause).isEqualTo(httpException)
    }
}