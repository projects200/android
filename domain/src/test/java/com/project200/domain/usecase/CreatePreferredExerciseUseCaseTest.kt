package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.repository.MemberRepository
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
class CreatePreferredExerciseUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: CreatePreferredExerciseUseCase

    private val samplePreferredExercises = listOf(
        PreferredExercise(
            preferredExerciseId = -1L,
            exerciseTypeId = 10L,
            name = "웨이트",
            skillLevel = "중급",
            daysOfWeek = listOf(true, false, true, false, true, false, false),
            imageUrl = "https://example.com/weight.jpg"
        ),
        PreferredExercise(
            preferredExerciseId = -1L,
            exerciseTypeId = 20L,
            name = "러닝",
            skillLevel = "초급",
            daysOfWeek = listOf(false, true, false, true, false, true, true),
            imageUrl = "https://example.com/running.jpg"
        )
    )

    @Before
    fun setUp() {
        useCase = CreatePreferredExerciseUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 선호 운동 생성 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.createPreferredExercise(samplePreferredExercises) } returns successResult

        // When
        val result = useCase(samplePreferredExercises)

        // Then
        coVerify(exactly = 1) { mockRepository.createPreferredExercise(samplePreferredExercises) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `선호 운동 생성 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("CREATE_FAILED", "Failed to create preferred exercises")
        coEvery { mockRepository.createPreferredExercise(samplePreferredExercises) } returns errorResult

        // When
        val result = useCase(samplePreferredExercises)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("CREATE_FAILED")
    }

    @Test
    fun `단일 선호 운동 생성`() = runTest {
        // Given
        val singleExercise = listOf(samplePreferredExercises[0])
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.createPreferredExercise(singleExercise) } returns successResult

        // When
        val result = useCase(singleExercise)

        // Then
        coVerify(exactly = 1) { mockRepository.createPreferredExercise(singleExercise) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `빈 목록으로 생성 호출`() = runTest {
        // Given
        val emptyList = emptyList<PreferredExercise>()
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.createPreferredExercise(emptyList) } returns successResult

        // When
        val result = useCase(emptyList)

        // Then
        coVerify(exactly = 1) { mockRepository.createPreferredExercise(emptyList) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `여러 개의 선호 운동 생성`() = runTest {
        // Given
        val manyExercises = (1..5).map { 
            PreferredExercise(
                preferredExerciseId = -1L,
                exerciseTypeId = it.toLong(),
                name = "운동$it",
                skillLevel = "중급",
                daysOfWeek = List(7) { false },
                imageUrl = null
            )
        }
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.createPreferredExercise(manyExercises) } returns successResult

        // When
        val result = useCase(manyExercises)

        // Then
        coVerify(exactly = 1) { mockRepository.createPreferredExercise(manyExercises) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
