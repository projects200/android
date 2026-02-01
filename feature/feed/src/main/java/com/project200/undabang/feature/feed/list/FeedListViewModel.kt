package com.project200.undabang.feature.feed.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Feed
import com.project200.domain.usecase.GetFeedsUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedListViewModel @Inject constructor(
    private val getFeedsUseCase: GetFeedsUseCase,
    private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
    private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase
) : ViewModel() {

    private val _feedList = MutableLiveData<List<Feed>>()
    val feedList: LiveData<List<Feed>> get() = _feedList

    private val _selectedType = MutableLiveData<String?>(null)
    val selectedType: LiveData<String?> = _selectedType

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    private val _exerciseTypeList = MutableLiveData<List<String>>()
    val exerciseTypeList: LiveData<List<String>> = _exerciseTypeList

    private var hasNext: Boolean = true
    private var lastFeedId: Long? = null
    private val allFeeds = mutableListOf<Feed>()

    init {
        loadFeeds()
        loadExerciseTypes()
    }

    fun selectType(type: String?) {
        _selectedType.value = type
        updateFilteredList()
    }

    fun clearType() {
        _selectedType.value = null
        updateFilteredList()
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
            // 유저의 선호 운동 리스트
            val preferredResult = getPreferredExerciseUseCase()
            val preferredNames =
                if (preferredResult is BaseResult.Success) {
                    preferredResult.data.map { it.name }
                } else {
                    emptyList()
                }

            // 전체 운동 종류 리스트
            val allTypesResult = getPreferredExerciseTypesUseCase()
            val allNames =
                if (allTypesResult is BaseResult.Success) {
                    allTypesResult.data.map { it.name }
                } else {
                    emptyList()
                }

            // 선호 운동 + 그 외 전체 운동(중복 제거) - 직접 입력 제외
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
            when (val result = getFeedsUseCase(lastFeedId)) {
                is BaseResult.Success -> {
                    val newFeeds = result.data.feeds
                    hasNext = result.data.hasNext
                    
                    if (newFeeds.isNotEmpty()) {
                        lastFeedId = newFeeds.last().feedId
                        allFeeds.addAll(newFeeds)
                        updateFilteredList()
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
