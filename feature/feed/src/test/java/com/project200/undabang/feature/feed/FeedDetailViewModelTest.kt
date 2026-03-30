package com.project200.undabang.feature.feed

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Comment
import com.project200.domain.model.Feed
import com.project200.domain.usecase.CreateCommentUseCase
import com.project200.domain.usecase.DeleteCommentUseCase
import com.project200.domain.usecase.DeleteFeedUseCase
import com.project200.domain.usecase.GetCommentsUseCase
import com.project200.domain.usecase.GetFeedDetailUseCase
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.LikeCommentUseCase
import com.project200.domain.usecase.LikeFeedUseCase
import com.project200.undabang.feature.feed.detail.FeedDetailViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class FeedDetailViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var getFeedDetailUseCase: GetFeedDetailUseCase

    @MockK
    private lateinit var getMemberIdUseCase: GetMemberIdUseCase

    @MockK
    private lateinit var deleteFeedUseCase: DeleteFeedUseCase

    @MockK
    private lateinit var getCommentsUseCase: GetCommentsUseCase

    @MockK
    private lateinit var createCommentUseCase: CreateCommentUseCase

    @MockK
    private lateinit var likeCommentUseCase: LikeCommentUseCase

    @MockK
    private lateinit var deleteCommentUseCase: DeleteCommentUseCase

    @MockK
    private lateinit var likeFeedUseCase: LikeFeedUseCase

    private lateinit var viewModel: FeedDetailViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleFeed = Feed(
        feedId = 1L,
        memberId = "member1",
        nickname = "테스터",
        feedTypeName = "헬스",
        feedTypeId = 1L,
        feedContent = "테스트 피드",
        feedPictures = emptyList(),
        feedLikesCount = 10,
        feedCommentsCount = 5,
        feedIsLiked = false,
        createdAt = "2025-01-01T10:00:00",
        thumbnailUrl = null
    )

    private val sampleComment = Comment(
        commentId = 1L,
        memberId = "member1",
        memberNickname = "테스터",
        memberProfileImageUrl = null,
        content = "테스트 댓글",
        likesCount = 0,
        isLiked = false,
        createdAt = "2025-01-01T10:00:00",
        taggedMemberId = null,
        taggedMemberNickname = null,
        children = emptyList()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = FeedDetailViewModel(
            getFeedDetailUseCase,
            getMemberIdUseCase,
            deleteFeedUseCase,
            getCommentsUseCase,
            createCommentUseCase,
            likeCommentUseCase,
            deleteCommentUseCase,
            likeFeedUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setFeedId - 피드 상세 정보를 로드한다`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(listOf(sampleComment))

        // When
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.feed.value).isEqualTo(sampleFeed)
        assertThat(viewModel.comments.value).hasSize(1)
    }

    @Test
    fun `setFeedId - 내 피드인 경우 isMyFeed가 true`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())

        // When
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.isMyFeed.value).isTrue()
    }

    @Test
    fun `setFeedId - 다른 사람 피드인 경우 isMyFeed가 false`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member2"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())

        // When
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.isMyFeed.value).isFalse()
    }

    @Test
    fun `setFeedId - 피드 로드 실패 시 에러 이벤트 발생`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Error("ERROR", "Failed")
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())

        // When & Then
        viewModel.feedLoadError.test {
            viewModel.setFeedId(1L)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `deleteFeed - 삭제 성공 시 feedDeleted 이벤트 발생`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())
        coEvery { deleteFeedUseCase(1L) } returns BaseResult.Success(Unit)
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        viewModel.feedDeleted.test {
            viewModel.deleteFeed()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `deleteFeed - 삭제 실패 시 토스트 이벤트 발생`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())
        coEvery { deleteFeedUseCase(1L) } returns BaseResult.Error("ERROR", "Failed")
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        viewModel.toastEvent.test {
            viewModel.deleteFeed()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isNotNull()
        }
    }

    @Test
    fun `createComment - 빈 내용은 무시한다`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.createComment("")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { createCommentUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun `createComment - 댓글 생성 성공 시 목록을 새로고침한다`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())
        coEvery { createCommentUseCase(1L, "테스트", null, null) } returns BaseResult.Success(Unit)
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.createComment("테스트")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(atLeast = 2) { getCommentsUseCase(1L) }
    }

    @Test
    fun `deleteComment - 삭제 성공 시 목록을 새로고침한다`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(listOf(sampleComment))
        coEvery { deleteCommentUseCase(1L) } returns BaseResult.Success(Unit)
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.deleteComment(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(atLeast = 2) { getCommentsUseCase(1L) }
    }

    @Test
    fun `toggleFeedLike - 좋아요 토글 시 로컬 상태가 즉시 업데이트된다`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())
        coEvery { likeFeedUseCase(1L, true) } returns BaseResult.Success(Unit)
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.toggleFeedLike()

        // Then
        assertThat(viewModel.feed.value?.feedIsLiked).isTrue()
        assertThat(viewModel.feed.value?.feedLikesCount).isEqualTo(11)
    }

    @Test
    fun `toggleFeedLike - 디바운스 후 서버에 반영된다`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())
        coEvery { likeFeedUseCase(1L, true) } returns BaseResult.Success(Unit)
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.toggleFeedLike()
        advanceTimeBy(1100)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { likeFeedUseCase(1L, true) }
    }

    @Test
    fun `refreshFeed - 피드와 댓글을 다시 로드한다`() = runTest {
        // Given
        coEvery { getMemberIdUseCase() } returns "member1"
        coEvery { getFeedDetailUseCase(1L) } returns BaseResult.Success(sampleFeed)
        coEvery { getCommentsUseCase(1L) } returns BaseResult.Success(emptyList())
        viewModel.setFeedId(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.refreshFeed()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(atLeast = 2) { getFeedDetailUseCase(1L) }
        coVerify(atLeast = 2) { getCommentsUseCase(1L) }
    }
}
