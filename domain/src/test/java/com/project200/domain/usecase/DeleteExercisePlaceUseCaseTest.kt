package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
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
class DeleteExercisePlaceUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: DeleteExercisePlaceUseCase

    private val samplePlaceId = 123L

    @Before
    fun setUp() {
        useCase = DeleteExercisePlaceUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 운동 장소 삭제 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteExercisePlace(samplePlaceId) } returns successResult

        // When
        val result = useCase(samplePlaceId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteExercisePlace(samplePlaceId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `운동 장소 삭제 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("DELETE_FAILED", "Failed to delete exercise place")
        coEvery { mockRepository.deleteExercisePlace(samplePlaceId) } returns errorResult

        // When
        val result = useCase(samplePlaceId)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("DELETE_FAILED")
    }

    @Test
    fun `존재하지 않는 장소 삭제 시 에러`() = runTest {
        // Given
        val nonExistentId = 999L
        val errorResult = BaseResult.Error("NOT_FOUND", "Place not found")
        coEvery { mockRepository.deleteExercisePlace(nonExistentId) } returns errorResult

        // When
        val result = useCase(nonExistentId)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `다른 장소 ID로 삭제`() = runTest {
        // Given
        val anotherId = 456L
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteExercisePlace(anotherId) } returns successResult

        // When
        val result = useCase(anotherId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteExercisePlace(anotherId) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `네트워크 오류로 삭제 실패`() = runTest {
        // Given
        val networkError = BaseResult.Error("NETWORK_ERROR", "Network connection failed")
        coEvery { mockRepository.deleteExercisePlace(samplePlaceId) } returns networkError

        // When
        val result = useCase(samplePlaceId)

        // Then
        assertThat((result as BaseResult.Error).message).isEqualTo("Network connection failed")
    }
}
