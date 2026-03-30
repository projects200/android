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
class EditExercisePlaceUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: EditExercisePlaceUseCase

    private val sampleExercisePlace = ExercisePlace(
        id = 1L,
        name = "수정된 헬스장",
        address = "서울시 강남구 역삼동 789-1",
        latitude = 37.501,
        longitude = 127.037
    )

    @Before
    fun setUp() {
        useCase = EditExercisePlaceUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 운동 장소 수정 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editExercisePlace(sampleExercisePlace) } returns successResult

        // When
        val result = useCase(sampleExercisePlace)

        // Then
        coVerify(exactly = 1) { mockRepository.editExercisePlace(sampleExercisePlace) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `운동 장소 수정 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("EDIT_FAILED", "Failed to edit exercise place")
        coEvery { mockRepository.editExercisePlace(sampleExercisePlace) } returns errorResult

        // When
        val result = useCase(sampleExercisePlace)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("EDIT_FAILED")
    }

    @Test
    fun `존재하지 않는 장소 수정 시 에러`() = runTest {
        // Given
        val nonExistentPlace = sampleExercisePlace.copy(id = 999L)
        val errorResult = BaseResult.Error("NOT_FOUND", "Place not found")
        coEvery { mockRepository.editExercisePlace(nonExistentPlace) } returns errorResult

        // When
        val result = useCase(nonExistentPlace)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `이름만 수정`() = runTest {
        // Given
        val nameChangedPlace = sampleExercisePlace.copy(name = "새로운 이름의 헬스장")
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editExercisePlace(nameChangedPlace) } returns successResult

        // When
        val result = useCase(nameChangedPlace)

        // Then
        coVerify(exactly = 1) { mockRepository.editExercisePlace(nameChangedPlace) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `위치 좌표 수정`() = runTest {
        // Given
        val locationChangedPlace = sampleExercisePlace.copy(
            latitude = 35.158,
            longitude = 129.160
        )
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.editExercisePlace(locationChangedPlace) } returns successResult

        // When
        val result = useCase(locationChangedPlace)

        // Then
        coVerify(exactly = 1) { mockRepository.editExercisePlace(locationChangedPlace) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
