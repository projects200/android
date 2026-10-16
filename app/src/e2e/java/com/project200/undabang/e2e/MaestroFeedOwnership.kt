package com.project200.undabang.e2e

import com.project200.domain.model.Feed

internal class MaestroFeedOwnership(
    val memberId: String,
    val runId: String,
    val flow: String,
) {
    init {
        if (memberId.isBlank() || !runId.matches(RUN_ID) || flow !in FLOWS) {
            throw MaestroSetupFailure("feed_fixture_identity_invalid")
        }
    }

    val post = "maestro-feed-$runId-$flow-post"
    val editedPost = "$post-edited"
    val comment = "maestro-feed-$runId-$flow-comment"
    val reply = "maestro-feed-$runId-$flow-reply"

    fun owns(feed: Feed): Boolean = feed.memberId == memberId && (feed.feedContent == post || feed.feedContent == editedPost)

    companion object {
        private val RUN_ID = Regex("[0-9a-f]{16}")
        val FLOWS = setOf("feed-post-crud", "feed-like", "feed-comment", "feed-photo", "feed-form-validation")
    }
}
