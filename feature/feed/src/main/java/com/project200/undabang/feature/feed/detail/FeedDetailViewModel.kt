package com.project200.undabang.feature.feed.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.project200.presentation.utils.UiText
import com.project200.undabang.feature.feed.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedDetailViewModel
    @Inject
    constructor(
        private val getFeedDetailUseCase: GetFeedDetailUseCase,
        private val getMemberIdUseCase: GetMemberIdUseCase,
        private val deleteFeedUseCase: DeleteFeedUseCase,
        private val getCommentsUseCase: GetCommentsUseCase,
        private val createCommentUseCase: CreateCommentUseCase,
        private val likeCommentUseCase: LikeCommentUseCase,
        private val deleteCommentUseCase: DeleteCommentUseCase,
        private val likeFeedUseCase: LikeFeedUseCase,
    ) : ViewModel() {
        private var feedId: Long = -1L

        private val _feed = MutableStateFlow<Feed?>(null)
        val feed: StateFlow<Feed?> = _feed.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _toastEvent = MutableSharedFlow<UiText>()
        val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

        private val _feedDeleted = MutableSharedFlow<Unit>()
        val feedDeleted: SharedFlow<Unit> = _feedDeleted.asSharedFlow()

        private val _feedLoadError = MutableSharedFlow<Unit>()
        val feedLoadError: SharedFlow<Unit> = _feedLoadError.asSharedFlow()

        private val _currentMemberId = MutableStateFlow<String?>(null)
        val currentMemberId: StateFlow<String?> = _currentMemberId.asStateFlow()

        private val _isMyFeed = MutableStateFlow(false)
        val isMyFeed: StateFlow<Boolean> = _isMyFeed.asStateFlow()

        private val _comments = MutableStateFlow<List<Comment>>(emptyList())
        val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

        private val _replyTarget = MutableStateFlow<CommentItem?>(null)
        val replyTarget: StateFlow<CommentItem?> = _replyTarget.asStateFlow()

        // 댓글별 좋아요 토글 디바운스 처리용 (서버 중복 호출 방지)
        private val commentLikeJobs = mutableMapOf<Long, Job>()
        private val pendingCommentLikes = mutableMapOf<Long, Boolean>()

        private var feedLikeJob: Job? = null
        private var pendingFeedLike: Boolean? = null
        private var originalFeedLikeState: Boolean? = null

        fun setFeedId(feedId: Long) {
            this.feedId = feedId
            loadCurrentMemberId()
            loadFeedDetail()
            loadComments()
        }

        private fun loadCurrentMemberId() {
            viewModelScope.launch {
                _currentMemberId.value = getMemberIdUseCase()
            }
        }

        private fun loadFeedDetail() {
            if (feedId == -1L) {
                viewModelScope.launch {
                    _toastEvent.emit(UiText.StringResource(R.string.feed_load_error))
                    _feedLoadError.emit(Unit)
                }
                return
            }

            _isLoading.value = true
            viewModelScope.launch {
                when (val result = getFeedDetailUseCase(feedId)) {
                    is BaseResult.Success -> {
                        _feed.value = result.data
                        checkIsMyFeed(result.data.memberId)
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.feed_load_error))
                        _feedLoadError.emit(Unit)
                    }
                }
                _isLoading.value = false
            }
        }

        private fun checkIsMyFeed(feedMemberId: String) {
            val currentId = _currentMemberId.value
            _isMyFeed.value = currentId != null && currentId == feedMemberId
        }

        fun refreshFeed() {
            loadFeedDetail()
            loadComments()
        }

        fun deleteFeed() {
            viewModelScope.launch {
                when (deleteFeedUseCase(feedId)) {
                    is BaseResult.Success -> {
                        _toastEvent.emit(UiText.StringResource(R.string.feed_deleted))
                        _feedDeleted.emit(Unit)
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.feed_delete_error))
                    }
                }
            }
        }

        fun loadComments() {
            if (feedId == -1L) return

            viewModelScope.launch {
                when (val result = getCommentsUseCase(feedId)) {
                    is BaseResult.Success -> {
                        _comments.value = result.data
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.comment_load_error))
                    }
                }
            }
        }

        fun createComment(content: String) {
            if (content.isBlank() || feedId == -1L) return

            viewModelScope.launch {
                val target = _replyTarget.value
                val parentCommentId =
                    when (target) {
                        is CommentItem.CommentData -> target.commentId
                        is CommentItem.ReplyData -> target.parentCommentId
                        null -> null
                    }
                // 대댓글에 답글 달 때만 태그 추가 (댓글에 답글 달 때는 태그 X)
                val taggedMemberId =
                    when (target) {
                        is CommentItem.ReplyData -> target.memberId
                        else -> null
                    }
                when (createCommentUseCase(feedId, content, parentCommentId, taggedMemberId)) {
                    is BaseResult.Success -> {
                        _toastEvent.emit(UiText.StringResource(R.string.comment_created))
                        _replyTarget.value = null
                        loadComments()
                        refreshFeedCommentsCount()
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.comment_create_error))
                    }
                }
            }
        }

        fun setReplyTarget(target: CommentItem?) {
            _replyTarget.value = target
        }

        // 좋아요 토글 시 로컬 상태 즉시 업데이트 + 1초 딜레이 후 서버 반영 (중복 요청 방지)
        fun toggleCommentLike(item: CommentItem) {
            val commentId = item.commentId
            val currentLikedState = pendingCommentLikes[commentId] ?: item.isLiked
            val newLikedState = !currentLikedState

            pendingCommentLikes[commentId] = newLikedState
            updateCommentLikeLocally(commentId, newLikedState)

            commentLikeJobs[commentId]?.cancel()
            commentLikeJobs[commentId] =
                viewModelScope.launch {
                    delay(LIKE_DEBOUNCE_MS)

                    val finalLikedState = pendingCommentLikes[commentId] ?: return@launch
                    if (finalLikedState == item.isLiked) {
                        pendingCommentLikes.remove(commentId)
                        return@launch
                    }

                    when (likeCommentUseCase(commentId, finalLikedState)) {
                        is BaseResult.Success -> {
                            pendingCommentLikes.remove(commentId)
                        }
                        is BaseResult.Error -> {
                            pendingCommentLikes.remove(commentId)
                            updateCommentLikeLocally(commentId, item.isLiked)
                            _toastEvent.emit(UiText.StringResource(R.string.like_error))
                        }
                    }
                }
        }

        // 좋아요 상태를 업데이트할 수 있도록 수정
        private fun updateCommentLikeLocally(
            commentId: Long,
            isLiked: Boolean,
        ) {
            val currentComments = _comments.value
            val updatedComments =
                currentComments.map { comment ->
                    if (comment.commentId == commentId) {
                        comment.copy(
                            isLiked = isLiked,
                            likesCount = if (isLiked) comment.likesCount + 1 else comment.likesCount - 1,
                        )
                    } else {
                        val updatedChildren =
                            comment.children.map { reply ->
                                if (reply.commentId == commentId) {
                                    reply.copy(
                                        isLiked = isLiked,
                                        likesCount = if (isLiked) reply.likesCount + 1 else reply.likesCount - 1,
                                    )
                                } else {
                                    reply
                                }
                            }
                        comment.copy(children = updatedChildren)
                    }
                }
            _comments.value = updatedComments
        }

        fun deleteComment(commentId: Long) {
            viewModelScope.launch {
                when (deleteCommentUseCase(commentId)) {
                    is BaseResult.Success -> {
                        _toastEvent.emit(UiText.StringResource(R.string.comment_deleted))
                        loadComments()
                        refreshFeedCommentsCount()
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.comment_delete_error))
                    }
                }
            }
        }

        private fun refreshFeedCommentsCount() {
            viewModelScope.launch {
                when (val result = getFeedDetailUseCase(feedId)) {
                    is BaseResult.Success -> {
                        _feed.value = result.data
                    }
                    is BaseResult.Error -> { }
                }
            }
        }

        // 좋아요 토글 시 로컬 상태 즉시 업데이트 + 1초 딜레이 후 서버 반영 (중복 요청 방지)
        fun toggleFeedLike() {
            val currentFeed = _feed.value ?: return

            if (originalFeedLikeState == null) {
                originalFeedLikeState = currentFeed.feedIsLiked
            }

            val currentLikedState = pendingFeedLike ?: currentFeed.feedIsLiked
            val newLikedState = !currentLikedState

            pendingFeedLike = newLikedState
            updateFeedLikeLocally(newLikedState)

            feedLikeJob?.cancel()
            feedLikeJob =
                viewModelScope.launch {
                    delay(LIKE_DEBOUNCE_MS)

                    val finalLikedState = pendingFeedLike ?: return@launch
                    val originalState = originalFeedLikeState ?: return@launch

                    if (finalLikedState == originalState) {
                        resetFeedLikePendingState()
                        return@launch
                    }

                    when (likeFeedUseCase(feedId, finalLikedState)) {
                        is BaseResult.Success -> {
                            resetFeedLikePendingState()
                        }
                        is BaseResult.Error -> {
                            resetFeedLikePendingState()
                            updateFeedLikeLocally(originalState)
                            _toastEvent.emit(UiText.StringResource(R.string.like_error))
                        }
                    }
                }
        }

        // 좋아요 상태 변경이 완료되거나 취소된 후에 pending 상태 초기화
        private fun resetFeedLikePendingState() {
            pendingFeedLike = null
            originalFeedLikeState = null
        }

        private fun updateFeedLikeLocally(isLiked: Boolean) {
            val currentFeed = _feed.value ?: return
            if (currentFeed.feedIsLiked == isLiked) return

            _feed.value =
                currentFeed.copy(
                    feedIsLiked = isLiked,
                    feedLikesCount =
                        if (isLiked) {
                            currentFeed.feedLikesCount + 1
                        } else {
                            currentFeed.feedLikesCount - 1
                        },
                )
        }

        companion object {
            private const val LIKE_DEBOUNCE_MS = 1000L
        }
    }
