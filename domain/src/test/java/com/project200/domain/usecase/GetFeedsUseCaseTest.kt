package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedListResult
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class GetFeedsUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: GetFeedsUseCase

    private val sampleFeeds = listOf(
        Feed(
            feedId = 1L,
            feedContent = "첫 번째 피드",
            feedLikesCount = 10,
            feedCommentsCount = 5,
            feedTypeId = 1L,
            feedTypeName = "운동",
            feedTypeDesc = "운동 관련 피드",
            feedCreatedAt = LocalDateTime.now(),
            feedIsLiked = false,
            feedHasCommented = false,
            memberId = "member1",
            nickname = "사용자1",
            profileUrl = null,
            thumbnailUrl = null,
            feedPictures = emptyList()
        )
    )

    @Before
    fun setUp() {
        useCase = GetFeedsUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 피드 목록 성공적으로 반환`() = runTest {
        // Given
        val feedListResult = FeedListResult(hasNext = true, feeds = sampleFeeds)
        val successResult = BaseResult.Success(feedListResult)
        coEvery { mockRepository.getFeeds(null, null) } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getFeeds(null, null) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.feeds).hasSize(1)
    }

    @Test
    fun `prevFeedId와 size 파라미터로 페이징 조회`() = runTest {
        // Given
        val prevFeedId = 10L
        val size = 20
        val feedListResult = FeedListResult(hasNext = false, feeds = sampleFeeds)
        val successResult = BaseResult.Success(feedListResult)
        coEvery { mockRepository.getFeeds(prevFeedId, size) } returns successResult

        // When
        val result = useCase(prevFeedId, size)

        // Then
        coVerify(exactly = 1) { mockRepository.getFeeds(prevFeedId, size) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `피드 조회 실패 시 에러 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Failed to fetch feeds")
        coEvery { mockRepository.getFeeds(null, null) } returns errorResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.getFeeds(null, null) }
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `빈 피드 목록 반환`() = runTest {
        // Given
        val emptyFeedListResult = FeedListResult(hasNext = false, feeds = emptyList())
        val successResult = BaseResult.Success(emptyFeedListResult)
        coEvery { mockRepository.getFeeds(null, null) } returns successResult

        // When
        val result = useCase()

        // Then
        assertThat((result as BaseResult.Success).data.feeds).isEmpty()
        assertThat(result.data.hasNext).isFalse()
    }
}
