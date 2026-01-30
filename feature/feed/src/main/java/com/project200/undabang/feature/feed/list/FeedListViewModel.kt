package com.project200.undabang.feature.feed.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.usecase.GetFeedsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val getFeedsUseCase: GetFeedsUseCase
) : ViewModel() {

    private val _feedList = MutableLiveData<List<Feed>>()
    val feedList: LiveData<List<Feed>> get() = _feedList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private var hasNext: Boolean = true
    private var lastFeedId: Long? = null
    private val currentFeeds = mutableListOf<Feed>()

    init {
        loadFeeds()
    }

    fun loadFeeds(isRefresh: Boolean = false) {
        if (isRefresh) {
            hasNext = true
            lastFeedId = null
            currentFeeds.clear()
        }

        if (!hasNext || (_isLoading.value == true && !isRefresh)) return

        _isLoading.value = true
        
        viewModelScope.launch {
            when (val result = getFeedsUseCase(lastFeedId)) {
                is BaseResult.Success -> {
                    val newFeeds = result.data.feeds
                    hasNext = result.data.hasNext
                    
                    if (newFeeds.isNotEmpty()) {
                        lastFeedId = newFeeds.last().feedId
                        currentFeeds.addAll(newFeeds)
                        _feedList.value = currentFeeds.toList()
                    } else if (isRefresh) {
                         _feedList.value = emptyList()
                    }
                }
                is BaseResult.Error -> {
                    _error.value = result.message ?: "알 수 없는 오류가 발생했습니다."
                }
            }
            _isLoading.value = false
        }
    }
}
