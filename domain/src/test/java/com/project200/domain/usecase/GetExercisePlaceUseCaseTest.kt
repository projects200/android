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
class GetExercisePlaceUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MatchingRepository

    private lateinit var useCase: GetExercisePlaceUseCase

    private val sampleExercisePlaces = listOf(
        ExercisePlace(
            id = 1L,
            name = "강남 헬스장",
            address = "서울시 강남구 역삼동 123",
            latitude = 37.500,
            longitude = 127.036
        ),
        ExercisePlace(
            id = 2L,
            name = "홍대 피트니스",
            address = "서울시 마포구 서교동 456",
            latitude = 37.556,
            longitude = 126.923
        )
    )

    @Before
    fun setUp() {
        useCase = GetExercisePlaceUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 운동 장소 목록 성공적으로 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(sampleExercisePlaces)
        coEvery { mockRepository.getExercisePlaces() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getExercisePlaces() }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `빈 운동 장소 목록 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(emptyList<ExercisePlace>())
        coEvery { mockRepository.getExercisePlaces() } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `운동 장소 조회 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch exercise places")
        coEvery { mockRepository.getExercisePlaces() } returns errorResult

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `여러 지역의 운동 장소 반환`() = runTest {
        // Given
        val multipleRegions = listOf(
            sampleExercisePlaces[0],
            sampleExercisePlaces[1],
            ExercisePlace(
                id = 3L,
                name = "부산 헬스클럽",
                address = "부산시 해운대구 123",
                latitude = 35.158,
                longitude = 129.160
            )
        )
        coEvery { mockRepository.getExercisePlaces() } returns BaseResult.Success(multipleRegions)

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data).hasSize(3)
    }
}
