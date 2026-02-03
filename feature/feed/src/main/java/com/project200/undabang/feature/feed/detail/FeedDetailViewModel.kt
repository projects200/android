package com.project200.undabang.feature.feed.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.usecase.DeleteFeedUseCase
import com.project200.domain.usecase.GetFeedDetailUseCase
import com.project200.domain.usecase.GetMemberIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedDetailViewModel @Inject constructor(
    private val getFeedDetailUseCase: GetFeedDetailUseCase,
    private val getMemberIdUseCase: GetMemberIdUseCase,
    private val deleteFeedUseCase: DeleteFeedUseCase,
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

    init {
        loadCurrentMemberId()
        loadFeedDetail()
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
}
