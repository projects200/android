package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Comment
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
class GetCommentsUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: GetCommentsUseCase

    private val sampleComments = listOf(
        Comment(
            commentId = 1L,
            memberId = "member1",
            memberNickname = "사용자1",
            memberProfileImageUrl = null,
            memberThumbnailUrl = null,
            content = "첫 번째 댓글",
            likesCount = 5,
            isLiked = false,
            createdAt = LocalDateTime.now(),
            children = emptyList()
        ),
        Comment(
            commentId = 2L,
            memberId = "member2",
            memberNickname = "사용자2",
            memberProfileImageUrl = "https://example.com/profile.jpg",
            memberThumbnailUrl = "https://example.com/thumb.jpg",
            content = "두 번째 댓글",
            likesCount = 10,
            isLiked = true,
            createdAt = LocalDateTime.now(),
            children = emptyList()
        )
    )

    @Before
    fun setUp() {
        useCase = GetCommentsUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 댓글 목록 성공적으로 반환`() = runTest {
        // Given
        val feedId = 1L
        val successResult = BaseResult.Success(sampleComments)
        coEvery { mockRepository.getComments(feedId) } returns successResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.getComments(feedId) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data).hasSize(2)
    }

    @Test
    fun `댓글이 없는 피드 조회`() = runTest {
        // Given
        val feedId = 1L
        val successResult = BaseResult.Success(emptyList<Comment>())
        coEvery { mockRepository.getComments(feedId) } returns successResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.getComments(feedId) }
        assertThat((result as BaseResult.Success).data).isEmpty()
    }

    @Test
    fun `댓글 조회 실패`() = runTest {
        // Given
        val feedId = 1L
        val errorResult = BaseResult.Error("ERR", "Failed to fetch comments")
        coEvery { mockRepository.getComments(feedId) } returns errorResult

        // When
        val result = useCase(feedId)

        // Then
        coVerify(exactly = 1) { mockRepository.getComments(feedId) }
        assertThat(result).isEqualTo(errorResult)
    }
}
