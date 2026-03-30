package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
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
class DeletePreferredExerciseUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: DeletePreferredExerciseUseCase

    private val sampleIds = listOf(1L, 2L, 3L)

    @Before
    fun setUp() {
        useCase = DeletePreferredExerciseUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 선호 운동 삭제 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deletePreferredExercise(sampleIds) } returns successResult

        // When
        val result = useCase(sampleIds)

        // Then
        coVerify(exactly = 1) { mockRepository.deletePreferredExercise(sampleIds) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `선호 운동 삭제 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("DELETE_FAILED", "Failed to delete preferred exercises")
        coEvery { mockRepository.deletePreferredExercise(sampleIds) } returns errorResult

        // When
        val result = useCase(sampleIds)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("DELETE_FAILED")
    }

    @Test
    fun `단일 선호 운동 삭제`() = runTest {
        // Given
        val singleId = listOf(1L)
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deletePreferredExercise(singleId) } returns successResult

        // When
        val result = useCase(singleId)

        // Then
        coVerify(exactly = 1) { mockRepository.deletePreferredExercise(singleId) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `존재하지 않는 ID로 삭제 시 에러`() = runTest {
        // Given
        val nonExistentIds = listOf(999L, 1000L)
        val errorResult = BaseResult.Error("NOT_FOUND", "Exercise not found")
        coEvery { mockRepository.deletePreferredExercise(nonExistentIds) } returns errorResult

        // When
        val result = useCase(nonExistentIds)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `빈 ID 목록으로 삭제 호출`() = runTest {
        // Given
        val emptyIds = emptyList<Long>()
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deletePreferredExercise(emptyIds) } returns successResult

        // When
        val result = useCase(emptyIds)

        // Then
        coVerify(exactly = 1) { mockRepository.deletePreferredExercise(emptyIds) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `많은 수의 선호 운동 삭제`() = runTest {
        // Given
        val manyIds = (1L..10L).toList()
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deletePreferredExercise(manyIds) } returns successResult

        // When
        val result = useCase(manyIds)

        // Then
        coVerify(exactly = 1) { mockRepository.deletePreferredExercise(manyIds) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
