package com.project200.undabang.feature.feed

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.Comment
import com.project200.domain.model.Reply
import com.project200.undabang.feature.feed.detail.CommentItem
import com.project200.undabang.feature.feed.detail.toCommentItems
import org.junit.Test
import java.time.LocalDateTime

class CommentItemTest {
    @Test
    fun `답글의 부모 ID와 본문은 화면 위치가 아니라 서버 children 관계에서 얻는다`() {
        val now = LocalDateTime.of(2026, 10, 15, 12, 0)
        val reply = Reply(3, "member", "CI", null, null, "owned-reply", 0, false, now)
        val first = Comment(1, "member", "CI", null, null, "unrelated-parent", 0, false, now, emptyList())
        val second = Comment(2, "member", "CI", null, null, "owned-parent", 0, false, now, listOf(reply))

        val items = listOf(first, second).toCommentItems()

        assertThat(items).hasSize(3)
        val actual = items[2] as CommentItem.ReplyData
        assertThat(actual.parentCommentId).isEqualTo(2)
        assertThat(actual.parentContent).isEqualTo("owned-parent")
        assertThat(actual.content).isEqualTo("owned-reply")
    }

    @Test
    fun `두 번째 최상위 댓글에는 답글 모델을 만들지 않는다`() {
        val now = LocalDateTime.of(2026, 10, 15, 12, 0)
        val first = Comment(1, "member", "CI", null, null, "owned-parent", 0, false, now, emptyList())
        val second = first.copy(commentId = 2, content = "owned-reply")

        val items = listOf(first, second).toCommentItems()

        assertThat(items.all { it is CommentItem.CommentData }).isTrue()
    }
}
