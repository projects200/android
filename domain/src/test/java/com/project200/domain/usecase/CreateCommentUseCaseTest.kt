package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CreateCommentResult
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
class CreateCommentUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: FeedRepository

    private lateinit var useCase: CreateCommentUseCase

    @Before
    fun setUp() {
        useCase = CreateCommentUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 댓글 생성 성공`() = runTest {
        // Given
        val feedId = 1L
        val content = "테스트 댓글"
        val commentId = 100L
        val successResult = BaseResult.Success(CreateCommentResult(commentId))
        coEvery { mockRepository.createComment(feedId, content, null, null) } returns successResult

        // When
        val result = useCase(feedId, content)

        // Then
        coVerify(exactly = 1) { mockRepository.createComment(feedId, content, null, null) }
        assertThat(result).isEqualTo(successResult)
        assertThat((result as BaseResult.Success).data.commentId).isEqualTo(commentId)
    }

    @Test
    fun `대댓글 생성 성공`() = runTest {
        // Given
        val feedId = 1L
        val content = "대댓글 내용"
        val parentCommentId = 50L
        val commentId = 101L
        val successResult = BaseResult.Success(CreateCommentResult(commentId))
        coEvery { mockRepository.createComment(feedId, content, parentCommentId, null) } returns successResult

        // When
        val result = useCase(feedId, content, parentCommentId)

        // Then
        coVerify(exactly = 1) { mockRepository.createComment(feedId, content, parentCommentId, null) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `멤버 태그가 있는 댓글 생성 성공`() = runTest {
        // Given
        val feedId = 1L
        val content = "@사용자 테스트"
        val parentCommentId = 50L
        val taggedMemberId = "member123"
        val commentId = 102L
        val successResult = BaseResult.Success(CreateCommentResult(commentId))
        coEvery { mockRepository.createComment(feedId, content, parentCommentId, taggedMemberId) } returns successResult

        // When
        val result = useCase(feedId, content, parentCommentId, taggedMemberId)

        // Then
        coVerify(exactly = 1) { mockRepository.createComment(feedId, content, parentCommentId, taggedMemberId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 댓글 생성 실패`() = runTest {
        // Given
        val feedId = 1L
        val content = "테스트 댓글"
        val errorResult = BaseResult.Error("ERR", "Comment creation failed")
        coEvery { mockRepository.createComment(feedId, content, null, null) } returns errorResult

        // When
        val result = useCase(feedId, content)

        // Then
        coVerify(exactly = 1) { mockRepository.createComment(feedId, content, null, null) }
        assertThat(result).isEqualTo(errorResult)
    }
}
