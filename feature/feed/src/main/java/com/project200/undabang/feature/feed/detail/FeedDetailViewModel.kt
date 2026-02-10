package com.project200.undabang.feature.feed.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
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
import com.project200.domain.usecase.UnlikeCommentUseCase
import com.project200.domain.usecase.UnlikeFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedDetailViewModel @Inject constructor(
    private val getFeedDetailUseCase: GetFeedDetailUseCase,
    private val getMemberIdUseCase: GetMemberIdUseCase,
    private val deleteFeedUseCase: DeleteFeedUseCase,
    private val getCommentsUseCase: GetCommentsUseCase,
    private val createCommentUseCase: CreateCommentUseCase,
    private val likeCommentUseCase: LikeCommentUseCase,
    private val unlikeCommentUseCase: UnlikeCommentUseCase,
    private val deleteCommentUseCase: DeleteCommentUseCase,
    private val likeFeedUseCase: LikeFeedUseCase,
    private val unlikeFeedUseCase: UnlikeFeedUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val feedId: Long = savedStateHandle.get<Long>("feedId") ?: -1L

    private val _feed = MutableLiveData<Feed>()
    val feed: LiveData<Feed> get() = _feed

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val _currentMemberId = MutableLiveData<String>()
    val currentMemberId: LiveData<String> get() = _currentMemberId

    private val _isMyFeed = MutableLiveData<Boolean>(false)
    val isMyFeed: LiveData<Boolean> get() = _isMyFeed

    private val _deleteSuccess = MutableLiveData<Boolean>()
    val deleteSuccess: LiveData<Boolean> get() = _deleteSuccess

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> get() = _comments

    private val _commentsLoading = MutableLiveData<Boolean>(false)
    val commentsLoading: LiveData<Boolean> get() = _commentsLoading

    private val _replyTarget = MutableLiveData<CommentItem?>()
    val replyTarget: LiveData<CommentItem?> get() = _replyTarget

    private val _commentCreated = MutableLiveData<Boolean>()
    val commentCreated: LiveData<Boolean> get() = _commentCreated

    private val _commentDeleted = MutableLiveData<Boolean>()
    val commentDeleted: LiveData<Boolean> get() = _commentDeleted

    init {
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
            _error.value = "피드 정보를 불러올 수 없습니다."
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
                    _error.value = result.message ?: "피드를 불러오는데 실패했습니다."
                }
            }
            _isLoading.value = false
        }
    }

    private fun checkIsMyFeed(feedMemberId: String) {
        val currentId = _currentMemberId.value
        _isMyFeed.value = currentId != null && currentId == feedMemberId
    }

    fun deleteFeed() {
        viewModelScope.launch {
            when (val result = deleteFeedUseCase(feedId)) {
                is BaseResult.Success -> {
                    _deleteSuccess.value = true
                }
                is BaseResult.Error -> {
                    _error.value = result.message ?: "피드 삭제에 실패했습니다."
                    _deleteSuccess.value = false
                }
            }
        }
    }

    fun loadComments() {
        if (feedId == -1L) return

        _commentsLoading.value = true
        viewModelScope.launch {
            when (val result = getCommentsUseCase(feedId)) {
                is BaseResult.Success -> {
                    _comments.value = result.data
                }
                is BaseResult.Error -> {
                    _error.value = result.message ?: "댓글을 불러오는데 실패했습니다."
                }
            }
            _commentsLoading.value = false
        }
    }

    fun createComment(content: String) {
        if (content.isBlank() || feedId == -1L) return

        viewModelScope.launch {
            val parentCommentId = when (val target = _replyTarget.value) {
                is CommentItem.CommentData -> target.commentId
                is CommentItem.ReplyData -> target.parentCommentId
                null -> null
            }
            when (val result = createCommentUseCase(feedId, content, parentCommentId)) {
                is BaseResult.Success -> {
                    _commentCreated.value = true
                    _replyTarget.value = null
                    loadComments()
                    refreshFeedCommentsCount()
                }
                is BaseResult.Error -> {
                    _error.value = result.message ?: "댓글 작성에 실패했습니다."
                    _commentCreated.value = false
                }
            }
        }
    }

    fun setReplyTarget(target: CommentItem?) {
        _replyTarget.value = target
    }

    fun toggleCommentLike(item: CommentItem) {
        viewModelScope.launch {
            val result = if (item.isLiked) {
                unlikeCommentUseCase(feedId, item.commentId)
            } else {
                likeCommentUseCase(feedId, item.commentId)
            }
            when (result) {
                is BaseResult.Success -> loadComments()
                is BaseResult.Error -> {
                    _error.value = result.message ?: "좋아요 처리에 실패했습니다."
                }
            }
        }
    }

    fun deleteComment(commentId: Long) {
        viewModelScope.launch {
            when (val result = deleteCommentUseCase(commentId)) {
                is BaseResult.Success -> {
                    _commentDeleted.value = true
                    loadComments()
                    refreshFeedCommentsCount()
                }
                is BaseResult.Error -> {
                    _error.value = result.message ?: "댓글 삭제에 실패했습니다."
                    _commentDeleted.value = false
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

    fun toggleFeedLike() {
        val currentFeed = _feed.value ?: return
        viewModelScope.launch {
            val result = if (currentFeed.feedIsLiked) {
                unlikeFeedUseCase(feedId)
            } else {
                likeFeedUseCase(feedId)
            }
            when (result) {
                is BaseResult.Success -> {
                    _feed.value = currentFeed.copy(
                        feedIsLiked = !currentFeed.feedIsLiked,
                        feedLikesCount = if (currentFeed.feedIsLiked) {
                            currentFeed.feedLikesCount - 1
                        } else {
                            currentFeed.feedLikesCount + 1
                        }
                    )
                }
                is BaseResult.Error -> {
                    _error.value = result.message ?: "좋아요 처리에 실패했습니다."
                }
            }
        }
    }
}
