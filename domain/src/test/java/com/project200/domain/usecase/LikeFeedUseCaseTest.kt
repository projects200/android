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
class LikeFeedUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: LikeFeedUseCase

    @Before
    fun setUp() {
        useCase = LikeFeedUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 피드 좋아요 성공`() = runTest {
        // Given
        val feedId = 1L
        val liked = true
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.likeFeed(feedId, liked) } returns successResult

        // When
        val result = useCase(feedId, liked)

        // Then
        coVerify(exactly = 1) { mockRepository.likeFeed(feedId, liked) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 피드 좋아요 취소 성공`() = runTest {
        // Given
        val feedId = 1L
        val liked = false
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.likeFeed(feedId, liked) } returns successResult

        // When
        val result = useCase(feedId, liked)

        // Then
        coVerify(exactly = 1) { mockRepository.likeFeed(feedId, liked) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 좋아요 실패`() = runTest {
        // Given
        val feedId = 1L
        val liked = true
        val errorResult = BaseResult.Error("ERR", "Like failed")
        coEvery { mockRepository.likeFeed(feedId, liked) } returns errorResult

        // When
        val result = useCase(feedId, liked)

        // Then
        coVerify(exactly = 1) { mockRepository.likeFeed(feedId, liked) }
        assertThat(result).isEqualTo(errorResult)
    }
}
