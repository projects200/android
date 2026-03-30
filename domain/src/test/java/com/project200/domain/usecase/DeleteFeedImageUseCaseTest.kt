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
class DeleteFeedImageUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: DeleteFeedImageUseCase

    @Before
    fun setUp() {
        useCase = DeleteFeedImageUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 이미지 삭제 성공`() = runTest {
        // Given
        val feedId = 1L
        val imageId = 100L
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteFeedImage(feedId, imageId) } returns successResult

        // When
        val result = useCase(feedId, imageId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteFeedImage(feedId, imageId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 이미지 삭제 실패`() = runTest {
        // Given
        val feedId = 1L
        val imageId = 100L
        val errorResult = BaseResult.Error("ERR", "Delete failed")
        coEvery { mockRepository.deleteFeedImage(feedId, imageId) } returns errorResult

        // When
        val result = useCase(feedId, imageId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteFeedImage(feedId, imageId) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `존재하지 않는 이미지 삭제 시도`() = runTest {
        // Given
        val feedId = 1L
        val imageId = 999L
        val errorResult = BaseResult.Error("NOT_FOUND", "Image not found")
        coEvery { mockRepository.deleteFeedImage(feedId, imageId) } returns errorResult

        // When
        val result = useCase(feedId, imageId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteFeedImage(feedId, imageId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
