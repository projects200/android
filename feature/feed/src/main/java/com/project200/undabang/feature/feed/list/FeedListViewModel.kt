package com.project200.undabang.feature.feed.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.usecase.DeleteFeedUseCase
import com.project200.domain.usecase.GetFeedsUseCase
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.presentation.utils.UiText
import com.project200.undabang.feature.feed.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val getFeedsUseCase: GetFeedsUseCase,
    private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
    private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
    private val getMemberIdUseCase: GetMemberIdUseCase,
    private val deleteFeedUseCase: DeleteFeedUseCase
) : ViewModel() {

    private val _feedList = MutableLiveData<List<Feed>>()
    val feedList: LiveData<List<Feed>> get() = _feedList

    private val _selectedType = MutableLiveData<String?>(null)
    val selectedType: LiveData<String?> = _selectedType

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _toastEvent = MutableSharedFlow<UiText>()
    val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

    private val _isEmpty = MutableLiveData<Boolean>(false)
    val isEmpty: LiveData<Boolean> get() = _isEmpty

    private val _exerciseTypeList = MutableLiveData<List<String>>()
    val exerciseTypeList: LiveData<List<String>> = _exerciseTypeList

    private val _currentMemberId = MutableLiveData<String>()
    val currentMemberId: LiveData<String> get() = _currentMemberId

    private val _showEmptyView = MutableLiveData<Boolean>(false)
    val showEmptyView: LiveData<Boolean> get() = _showEmptyView

    private val _showCategoryBottomSheet = MutableLiveData<List<String>?>()
    val showCategoryBottomSheet: LiveData<List<String>?> get() = _showCategoryBottomSheet

    private var hasNext: Boolean = true
    private var lastFeedId: Long? = null
    private val allFeeds = mutableListOf<Feed>()

    companion object {
        private const val DEFAULT_PAGE_SIZE = 10
    }

    init {
        loadFeeds()
        loadExerciseTypes()
        loadCurrentMemberId()
    }

    private fun loadCurrentMemberId() {
        viewModelScope.launch {
            _currentMemberId.value = getMemberIdUseCase()
        }
    }

    fun selectType(type: String?) {
        _selectedType.value = type
        updateFilteredList()
    }

    fun clearType() {
        _selectedType.value = null
        updateFilteredList()
    }

    fun canLoadMore(): Boolean {
        return _selectedType.value == null && _isLoading.value != true && hasNext
    }

    fun requestShowCategoryBottomSheet() {
        val items = _exerciseTypeList.value
        if (items.isNullOrEmpty()) {
            loadExerciseTypes()
        } else {
            _showCategoryBottomSheet.value = items
        }
    }

    fun onCategoryBottomSheetShown() {
        _showCategoryBottomSheet.value = null
    }

    private fun updateShowEmptyView() {
        _showEmptyView.value = _isEmpty.value == true && _isLoading.value != true
    }

    private fun updateFilteredList() {
        val type = _selectedType.value
        if (type == null) {
            _feedList.value = allFeeds.toList()
        } else {
            _feedList.value = allFeeds.filter { it.feedTypeName == type }
        }
    }

    fun loadExerciseTypes() {
        if (!_exerciseTypeList.value.isNullOrEmpty()) return
        viewModelScope.launch {
            val preferredResult = getPreferredExerciseUseCase()
            val preferredNames =
                if (preferredResult is BaseResult.Success) {
                    preferredResult.data.map { it.name }
                } else {
                    emptyList()
                }

            val allTypesResult = getPreferredExerciseTypesUseCase()
            val allNames =
                if (allTypesResult is BaseResult.Success) {
                    allTypesResult.data.map { it.name }
                } else {
                    emptyList()
                }

            val combinedList =
                mutableListOf<String>().apply {
                    addAll(preferredNames)
                    addAll(allNames.filterNot { preferredNames.contains(it) })
                }

            _exerciseTypeList.value = combinedList
        }
    }

    fun loadFeeds(isRefresh: Boolean = false) {
        if (isRefresh) {
            hasNext = true
            lastFeedId = null
            allFeeds.clear()
        }

        if (!hasNext || (_isLoading.value == true && !isRefresh)) return

        _isLoading.value = true
        
        viewModelScope.launch {
            when (val result = getFeedsUseCase(lastFeedId, DEFAULT_PAGE_SIZE)) {
                is BaseResult.Success -> {
                    val newFeeds = result.data.feeds
                    hasNext = result.data.hasNext
                    
                    if (newFeeds.isNotEmpty()) {
                        lastFeedId = newFeeds.last().feedId
                        allFeeds.addAll(newFeeds)
                    }
                    updateFilteredList()
                    _isEmpty.value = allFeeds.isEmpty()
                    updateShowEmptyView()
                }
                is BaseResult.Error -> {
                    _toastEvent.emit(UiText.StringResource(R.string.unknown_error))
                    if (allFeeds.isEmpty()) {
                        _isEmpty.value = true
                        _feedList.value = emptyList()
                        updateShowEmptyView()
                    }
                }
            }
            _isLoading.value = false
            updateShowEmptyView()
        }
    }

    fun deleteFeed(feedId: Long) {
        viewModelScope.launch {
            when (deleteFeedUseCase(feedId)) {
                is BaseResult.Success -> {
                    allFeeds.removeAll { it.feedId == feedId }
                    updateFilteredList()
                    _isEmpty.value = allFeeds.isEmpty()
                    updateShowEmptyView()
                    _toastEvent.emit(UiText.StringResource(R.string.feed_deleted))
                }
                is BaseResult.Error -> {
                    _toastEvent.emit(UiText.StringResource(R.string.feed_delete_error))
                }
            }
        }
    }
}
