package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.repository.MatchingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class RegisterExercisePlaceUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: RegisterExercisePlaceUseCase

    private val sampleExercisePlace = ExercisePlace(
        id = 0L,
        name = "새 헬스장",
        address = "서울시 강남구 역삼동 789",
        latitude = 37.500,
        longitude = 127.036
    )

    @Before
    fun setUp() {
        useCase = RegisterExercisePlaceUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 운동 장소 등록 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.addExercisePlace(sampleExercisePlace) } returns successResult

        // When
        val result = useCase(sampleExercisePlace)

        // Then
        coVerify(exactly = 1) { mockRepository.addExercisePlace(sampleExercisePlace) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `운동 장소 등록 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("REGISTER_FAILED", "Failed to register exercise place")
        coEvery { mockRepository.addExercisePlace(sampleExercisePlace) } returns errorResult

        // When
        val result = useCase(sampleExercisePlace)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("REGISTER_FAILED")
    }

    @Test
    fun `다른 운동 장소 등록`() = runTest {
        // Given
        val anotherPlace = ExercisePlace(
            id = 0L,
            name = "부산 피트니스",
            address = "부산시 해운대구 456",
            latitude = 35.158,
            longitude = 129.160
        )
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.addExercisePlace(anotherPlace) } returns successResult

        // When
        val result = useCase(anotherPlace)

        // Then
        coVerify(exactly = 1) { mockRepository.addExercisePlace(anotherPlace) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `중복된 장소 등록 시 에러`() = runTest {
        // Given
        val errorResult = BaseResult.Error("DUPLICATE", "Place already exists")
        coEvery { mockRepository.addExercisePlace(sampleExercisePlace) } returns errorResult

        // When
        val result = useCase(sampleExercisePlace)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("DUPLICATE")
    }

    @Test
    fun `네트워크 오류로 등록 실패`() = runTest {
        // Given
        val networkError = BaseResult.Error("NETWORK_ERROR", "Network connection failed")
        coEvery { mockRepository.addExercisePlace(sampleExercisePlace) } returns networkError

        // When
        val result = useCase(sampleExercisePlace)

        // Then
        assertThat((result as BaseResult.Error).message).isEqualTo("Network connection failed")
    }
}
