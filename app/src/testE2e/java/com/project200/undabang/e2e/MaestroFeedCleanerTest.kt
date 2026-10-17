package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Comment
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedListResult
import com.project200.domain.model.FeedPicture
import com.project200.domain.model.Reply
import com.project200.domain.repository.FeedRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime

class MaestroFeedCleanerTest {
    private val repository = mockk<FeedRepository>()
    private val cleaner = MaestroFeedCleaner(repository)
    private val owner = MaestroFeedOwnership("member", "0123456789abcdef", "feed-comment")
    private val now = LocalDateTime.of(2026, 10, 15, 12, 0)

    @Test
    fun `작성자와 현재 실행의 정확한 본문이 모두 일치해야 소유한다`() {
        assertThat(owner.owns(feed(1))).isTrue()
        assertThat(owner.owns(feed(1).copy(feedContent = owner.editedPost))).isTrue()
        assertThat(owner.owns(feed(1).copy(memberId = "other"))).isFalse()
        for (content in listOf("${owner.post}-extra", "prefix-${owner.post}", "${owner.post}\n", "e2e612-old")) {
            assertThat(owner.owns(feed(1).copy(feedContent = content))).isFalse()
        }
        val otherRun = MaestroFeedOwnership("member", "1123456789abcdef", "feed-comment")
        assertThat(otherRun.owns(feed(1))).isFalse()
    }

    @Test
    fun `모든 페이지를 수집한 뒤 답글 댓글 사진 글 순서로 삭제하고 부재를 재조회한다`() =
        runTest {
            val picture = FeedPicture(3, "https://example.invalid/nonprivate.png")
            val owned = feed(10).copy(feedPictures = listOf(picture))
            val parent = comment().copy(children = listOf(reply()))
            coEvery { repository.getFeeds(null, 10) } returnsMany
                listOf(
                    success(FeedListResult(true, listOf(feed(20).copy(memberId = "other")))),
                    success(FeedListResult(false, emptyList())),
                )
            coEvery { repository.getFeeds(20, 10) } returns success(FeedListResult(false, listOf(owned)))
            coEvery { repository.getFeedDetail(10) } returnsMany
                listOf(success(owned), success(owned), success(owned.copy(feedPictures = emptyList())))
            coEvery { repository.getComments(10) } returnsMany
                listOf(success(listOf(parent)), success(listOf(parent)), success(emptyList()))
            coEvery { repository.deleteComment(any()) } returns success(Unit)
            coEvery { repository.deleteFeedImage(10, 3) } returns success(Unit)
            coEvery { repository.deleteFeed(10) } returns success(Unit)

            cleaner.clean(owner)

            coVerifyOrder {
                repository.getFeeds(null, 10)
                repository.getFeeds(20, 10)
                repository.deleteComment(2)
                repository.deleteComment(1)
                repository.deleteFeedImage(10, 3)
                repository.deleteFeed(10)
            }
            coVerify(exactly = 0) { repository.deleteFeed(20) }
            coVerify(exactly = 2) { repository.getFeeds(null, 10) }
        }

    @Test
    fun `뒤 페이지 조회 실패는 앞 페이지 후보도 보존한다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns success(FeedListResult(true, listOf(feed(10))))
            coEvery { repository.getFeeds(10, 10) } returns error()
            expectFailure("feed_fixture_read_failed") { cleaner.clean(owner) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `삭제 직전 작성자나 본문이 바뀐 글은 보존한다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returnsMany
                listOf(success(FeedListResult(false, listOf(feed(10)))), success(FeedListResult(false, emptyList())))
            coEvery { repository.getFeedDetail(10) } returns success(feed(10).copy(feedContent = "ordinary post"))
            cleaner.clean(owner)
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
            coVerify(exactly = 0) { repository.deleteComment(any()) }
        }

    @Test
    fun `다른 사람 댓글이나 예약하지 않은 본문이 있으면 글 전체를 보존하고 실패한다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns success(FeedListResult(false, listOf(feed(10))))
            coEvery { repository.getFeedDetail(10) } returns success(feed(10))
            coEvery { repository.getComments(10) } returns success(listOf(comment().copy(memberId = "other")))
            expectFailure("feed_fixture_foreign_comments") { cleaner.clean(owner) }
            coVerify(exactly = 0) { repository.deleteComment(any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `자식 삭제 실패를 성공으로 숨기거나 부모 삭제로 우회하지 않는다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns success(FeedListResult(false, listOf(feed(10))))
            coEvery { repository.getFeedDetail(10) } returns success(feed(10))
            coEvery { repository.getComments(10) } returns success(listOf(comment().copy(children = listOf(reply()))))
            coEvery { repository.deleteComment(2) } returns error()
            expectFailure("feed_fixture_delete_failed") { cleaner.clean(owner) }
            coVerify(exactly = 0) { repository.deleteComment(1) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `페이지 커서가 반복되면 삭제 전에 실패한다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns success(FeedListResult(true, listOf(feed(10))))
            coEvery { repository.getFeeds(10, 10) } returns success(FeedListResult(true, listOf(feed(10))))
            expectFailure("feed_fixture_read_failed") { cleaner.clean(owner) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `조회 후 댓글이 일반 본문으로 바뀌면 그 댓글과 글을 보존한다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns success(FeedListResult(false, listOf(feed(10))))
            coEvery { repository.getFeedDetail(10) } returns success(feed(10))
            coEvery { repository.getComments(10) } returnsMany
                listOf(success(listOf(comment())), success(listOf(comment().copy(content = "ordinary comment"))))
            expectFailure("feed_fixture_foreign_comments") { cleaner.clean(owner) }
            coVerify(exactly = 0) { repository.deleteComment(any()) }
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
        }

    @Test
    fun `유효성 검사 기준 목록은 모든 내 글 ID만 조회하고 삭제하지 않는다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns
                success(FeedListResult(true, listOf(feed(30).copy(memberId = "other"), feed(20).copy(feedContent = "ordinary"))))
            coEvery { repository.getFeeds(20, 10) } returns success(FeedListResult(false, listOf(feed(10))))
            assertThat(cleaner.memberPostIds(owner)).containsExactly(20L, 10L)
            coVerify(exactly = 0) { repository.deleteFeed(any()) }
            coVerify(exactly = 0) { repository.deleteComment(any()) }
        }

    @Test
    fun `삭제 뒤에도 정확한 실행 글이 남으면 정리 성공으로 보고하지 않는다`() =
        runTest {
            coEvery { repository.getFeeds(null, 10) } returns success(FeedListResult(false, listOf(feed(10))))
            coEvery { repository.getFeedDetail(10) } returns success(feed(10))
            coEvery { repository.getComments(10) } returns success(emptyList())
            coEvery { repository.deleteFeed(10) } returns success(Unit)
            expectFailure("feed_fixture_cleanup_incomplete") { cleaner.clean(owner) }
        }

    private fun feed(id: Long) =
        Feed(id, owner.post, 0, 0, null, null, null, now, false, false, owner.memberId, "CI", null, null, emptyList())

    private fun comment() = Comment(1, owner.memberId, "CI", null, null, owner.comment, 0, false, now, emptyList())

    private fun reply() = Reply(2, owner.memberId, "CI", null, null, owner.reply, 0, false, now)

    private fun <T> success(data: T): BaseResult<T> = BaseResult.Success(data)

    private fun error(): BaseResult<Nothing> = BaseResult.Error("SERVER_ERROR", "")

    private suspend fun expectFailure(
        reason: String,
        action: suspend () -> Unit,
    ) {
        try {
            action()
            throw AssertionError("정리 실패가 허용되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
