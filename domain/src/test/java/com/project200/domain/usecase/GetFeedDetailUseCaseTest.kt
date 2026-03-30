package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
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
class GetFeedDetailUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: GetFeedDetailUseCase

    private val sampleFeed = Feed(
        feedId = 1L,
        feedContent = "테스트 피드 내용",
        feedLikesCount = 10,
        feedCommentsCount = 5,
        feedTypeId = 1L,
        feedTypeName = "운동",
        feedTypeDesc = "운동 관련 피드",
        feedCreatedAt = LocalDateTime.now(),
        feedIsLiked = true,
        feedHasCommented = true,
        memberId = "member1",
        nickname = "테스트유저",
        profileUrl = "https://example.com/profile.jpg",
        thumbnailUrl = "https://example.com/thumb.jpg",
        feedPictures = emptyList()
    )

    @Before
    fun setUp() {
        useCase = GetFeedDetailUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 피드 상세 정보 성공적으로 반환`() = runTest {
        // Given
        val feedId = 1L
        val successResult = BaseResult.Success(sampleFeed)
        coEvery { mockRepository.getFeedDetail(feedId) } returns successResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.getFeedDetail(feedId) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.feedId).isEqualTo(feedId)
    }

    @Test
    fun `존재하지 않는 피드 조회 시 에러 반환`() = runTest {
        // Given
        val feedId = 999L
        val errorResult = BaseResult.Error("NOT_FOUND", "Feed not found")
        coEvery { mockRepository.getFeedDetail(feedId) } returns errorResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.getFeedDetail(feedId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
