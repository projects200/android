package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FeedRepository
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
class DeleteFeedUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: DeleteFeedUseCase

    @Before
    fun setUp() {
        useCase = DeleteFeedUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 피드 삭제 성공`() = runTest {
        // Given
        val feedId = 1L
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteFeed(feedId) } returns successResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteFeed(feedId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 피드 삭제 실패`() = runTest {
        // Given
        val feedId = 1L
        val errorResult = BaseResult.Error("ERR", "Delete failed")
        coEvery { mockRepository.deleteFeed(feedId) } returns errorResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteFeed(feedId) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `존재하지 않는 피드 삭제 시도`() = runTest {
        // Given
        val feedId = 999L
        val errorResult = BaseResult.Error("NOT_FOUND", "Feed not found")
        coEvery { mockRepository.deleteFeed(feedId) } returns errorResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteFeed(feedId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
