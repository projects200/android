package com.project200.undabang.feature.feed.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import com.project200.presentation.utils.UiText
import com.project200.undabang.feature.feed.R
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
) : ViewModel() {

    private var feedId: Long = -1L

    private val _feed = MutableLiveData<Feed>()
    val feed: LiveData<Feed> get() = _feed

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _toastEvent = MutableSharedFlow<UiText>()
    val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

    private val _feedDeleted = MutableSharedFlow<Unit>()
    val feedDeleted: SharedFlow<Unit> = _feedDeleted.asSharedFlow()

    private val _feedLoadError = MutableSharedFlow<Unit>()
    val feedLoadError: SharedFlow<Unit> = _feedLoadError.asSharedFlow()

    private val _currentMemberId = MutableLiveData<String>()
    val currentMemberId: LiveData<String> get() = _currentMemberId

    private val _isMyFeed = MutableLiveData<Boolean>(false)
    val isMyFeed: LiveData<Boolean> get() = _isMyFeed

    private val _comments = MutableLiveData<List<Comment>>()
    val comments: LiveData<List<Comment>> get() = _comments

    private val _commentsLoading = MutableLiveData<Boolean>(false)
    val commentsLoading: LiveData<Boolean> get() = _commentsLoading

    private val _replyTarget = MutableLiveData<CommentItem?>()
    val replyTarget: LiveData<CommentItem?> get() = _replyTarget

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

        _commentsLoading.value = true
        viewModelScope.launch {
            when (val result = getCommentsUseCase(feedId)) {
                is BaseResult.Success -> {
                    _comments.value = result.data
                }
                is BaseResult.Error -> {
                    _toastEvent.emit(UiText.StringResource(R.string.comment_load_error))
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
            when (createCommentUseCase(feedId, content, parentCommentId)) {
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
                    _toastEvent.emit(UiText.StringResource(R.string.like_error))
                }
            }
        }
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
                    _toastEvent.emit(UiText.StringResource(R.string.like_error))
                }
            }
        }
    }
}
