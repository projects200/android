package com.project200.undabang.e2e

import com.project200.domain.model.BaseResult
import com.project200.domain.model.Comment
import com.project200.domain.model.Feed
import com.project200.domain.repository.FeedRepository
import javax.inject.Inject

internal class MaestroFeedCleaner
    @Inject
    constructor(
        private val repository: FeedRepository,
    ) {
        suspend fun clean(owner: MaestroFeedOwnership) {
            // 페이지를 모두 수집하기 전에는 삭제하지 않아 커서 이동으로 후보가 누락되지 않게 합니다.
            val plans =
                collect(owner).mapNotNull { id ->
                    val feed = read(repository.getFeedDetail(id))
                    if (feed.feedId != id) throw MaestroSetupFailure("feed_fixture_read_failed")
                    if (!owner.owns(feed)) return@mapNotNull null
                    val comments = read(repository.getComments(id))
                    validateComments(owner, comments)
                    validatePictures(feed)
                    feed
                }
            for (collected in plans) {
                val feed = read(repository.getFeedDetail(collected.feedId))
                if (feed.feedId != collected.feedId) throw MaestroSetupFailure("feed_fixture_read_failed")
                if (!owner.owns(feed)) continue
                if (feed.feedPictures != collected.feedPictures) throw MaestroSetupFailure("feed_fixture_changed")
                // 삭제 직전 댓글을 다시 읽어 변경된 본문·작성자를 기존 ID로 지우지 않습니다.
                val comments = read(repository.getComments(feed.feedId))
                validateComments(owner, comments)
                for (comment in comments) {
                    for (reply in comment.children) deleted(repository.deleteComment(reply.commentId))
                    deleted(repository.deleteComment(comment.commentId))
                }
                if (read(repository.getComments(feed.feedId)).isNotEmpty()) {
                    throw MaestroSetupFailure("feed_fixture_foreign_comments")
                }
                for (picture in feed.feedPictures) {
                    deleted(repository.deleteFeedImage(feed.feedId, picture.feedPictureId))
                }
                val emptyFeed = read(repository.getFeedDetail(feed.feedId))
                if (!owner.owns(emptyFeed) || emptyFeed.feedId != feed.feedId || emptyFeed.feedPictures.isNotEmpty()) {
                    throw MaestroSetupFailure("feed_fixture_changed")
                }
                deleted(repository.deleteFeed(feed.feedId))
            }
            // 특정 404 코드나 서버 cascade를 가정하지 않고 실제 목록을 끝까지 다시 조회합니다.
            if (collect(owner).isNotEmpty()) throw MaestroSetupFailure("feed_fixture_cleanup_incomplete")
        }

        suspend fun memberPostIds(owner: MaestroFeedOwnership): Set<Long> = collect(owner, includeUnmarked = true)

        private suspend fun collect(
            owner: MaestroFeedOwnership,
            includeUnmarked: Boolean = false,
        ): Set<Long> {
            val candidates = linkedSetOf<Long>()
            val seen = mutableSetOf<Long>()
            var cursor: Long? = null
            do {
                val page = read(repository.getFeeds(cursor, 10))
                for (feed in page.feeds) {
                    if (feed.feedId <= 0 || !seen.add(feed.feedId)) throw MaestroSetupFailure("feed_fixture_read_failed")
                    val matches = if (includeUnmarked) feed.memberId == owner.memberId else owner.owns(feed)
                    if (matches) candidates.add(feed.feedId)
                }
                if (!page.hasNext) break
                cursor = page.feeds.lastOrNull()?.feedId ?: throw MaestroSetupFailure("feed_fixture_read_failed")
            } while (true)
            return candidates
        }

        private fun validateComments(
            owner: MaestroFeedOwnership,
            comments: List<Comment>,
        ) {
            val seen = mutableSetOf<Long>()
            for (comment in comments) {
                if (
                    comment.commentId <= 0 || !seen.add(comment.commentId) ||
                    comment.memberId != owner.memberId || comment.content != owner.comment
                ) {
                    throw MaestroSetupFailure("feed_fixture_foreign_comments")
                }
                for (reply in comment.children) {
                    if (
                        reply.commentId <= 0 || !seen.add(reply.commentId) ||
                        reply.memberId != owner.memberId || reply.content != owner.reply
                    ) {
                        throw MaestroSetupFailure("feed_fixture_foreign_comments")
                    }
                }
            }
        }

        private fun validatePictures(feed: Feed) {
            val ids = mutableSetOf<Long>()
            for (picture in feed.feedPictures) {
                if (picture.feedPictureId <= 0 || !ids.add(picture.feedPictureId)) {
                    throw MaestroSetupFailure("feed_fixture_read_failed")
                }
            }
        }

        private fun deleted(result: BaseResult<Unit>) {
            if (result is BaseResult.Error) throw MaestroSetupFailure("feed_fixture_delete_failed")
        }

        private fun <T> read(result: BaseResult<T>): T =
            when (result) {
                is BaseResult.Success -> result.data
                is BaseResult.Error -> throw MaestroSetupFailure("feed_fixture_read_failed")
            }
    }
