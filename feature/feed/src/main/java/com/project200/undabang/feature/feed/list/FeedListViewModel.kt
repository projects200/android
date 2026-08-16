package com.project200.undabang.feature.feed.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedListViewModel
    @Inject
    constructor(
        private val getFeedsUseCase: GetFeedsUseCase,
        private val getPreferredExerciseUseCase: GetPreferredExerciseUseCase,
        private val getPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase,
        private val getMemberIdUseCase: GetMemberIdUseCase,
        private val deleteFeedUseCase: DeleteFeedUseCase,
    ) : ViewModel() {
        private val _feedList = MutableStateFlow<List<Feed>>(emptyList())
        val feedList: StateFlow<List<Feed>> = _feedList.asStateFlow()

        private val _selectedType = MutableStateFlow<ExerciseType?>(null)
        val selectedType: StateFlow<ExerciseType?> = _selectedType.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _toastEvent = MutableSharedFlow<UiText>()
        val toastEvent: SharedFlow<UiText> = _toastEvent.asSharedFlow()

        private val _isEmpty = MutableStateFlow(false)
        val isEmpty: StateFlow<Boolean> = _isEmpty.asStateFlow()

        private val exerciseTypeListFlow = MutableStateFlow<List<ExerciseType>>(emptyList())

        private val _currentMemberId = MutableStateFlow<String?>(null)
        val currentMemberId: StateFlow<String?> = _currentMemberId.asStateFlow()

        private val _showCategoryBottomSheet = MutableSharedFlow<List<ExerciseType>>()
        val showCategoryBottomSheet: SharedFlow<List<ExerciseType>> = _showCategoryBottomSheet.asSharedFlow()

        // 페이징 상태. allFeeds는 원본, _feedList는 카테고리 필터링 결과
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

        fun selectType(type: ExerciseType?) {
            _selectedType.value = type
            updateFilteredList()
        }

        fun clearType() {
            _selectedType.value = null
            updateFilteredList()
        }

        // 카테고리 필터링 중에는 페이징을 멈춤 (현재 정책: 필터링은 로컬 데이터만 대상)
        fun canLoadMore(): Boolean {
            return _selectedType.value == null && !_isLoading.value && hasNext
        }

        fun requestShowCategoryBottomSheet() {
            val items = exerciseTypeListFlow.value
            if (items.isEmpty()) {
                loadExerciseTypes()
            } else {
                viewModelScope.launch {
                    _showCategoryBottomSheet.emit(items)
                }
            }
        }

        private fun updateFilteredList() {
            val selectedTypeId = _selectedType.value?.id
            if (selectedTypeId == null) {
                _feedList.value = allFeeds.toList()
            } else {
                _feedList.value = allFeeds.filter { it.feedTypeId == selectedTypeId }
            }
        }

        fun loadExerciseTypes() {
            if (exerciseTypeListFlow.value.isNotEmpty()) return
            viewModelScope.launch {
                val preferredResult = getPreferredExerciseUseCase()
                val preferredTypes =
                    if (preferredResult is BaseResult.Success) {
                        preferredResult.data.map { ExerciseType(it.exerciseTypeId, it.name, it.imageUrl) }
                    } else {
                        emptyList()
                    }

                val allTypesResult = getPreferredExerciseTypesUseCase()
                val allTypes =
                    if (allTypesResult is BaseResult.Success) {
                        allTypesResult.data.map { ExerciseType(it.exerciseTypeId, it.name, it.imageUrl) }
                    } else {
                        emptyList()
                    }

                // 선호 운동을 앞에 두고, 그 외 전체 운동을 뒤에 붙임 (중복 제거)
                val preferredIds = preferredTypes.map { it.id }.toSet()
                val combinedList =
                    mutableListOf<ExerciseType>().apply {
                        addAll(preferredTypes)
                        addAll(allTypes.filterNot { preferredIds.contains(it.id) })
                    }

                exerciseTypeListFlow.value = combinedList
            }
        }

        fun loadFeeds(isRefresh: Boolean = false) {
            if (isRefresh) {
                hasNext = true
                lastFeedId = null
                allFeeds.clear()
            }

            if (!hasNext || (_isLoading.value && !isRefresh)) return

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
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.unknown_error))
                        if (allFeeds.isEmpty()) {
                            _isEmpty.value = true
                            _feedList.value = emptyList()
                        }
                    }
                }
                _isLoading.value = false
            }
        }

        fun deleteFeed(feedId: Long) {
            viewModelScope.launch {
                when (deleteFeedUseCase(feedId)) {
                    is BaseResult.Success -> {
                        allFeeds.removeAll { it.feedId == feedId }
                        updateFilteredList()
                        _isEmpty.value = allFeeds.isEmpty()
                        _toastEvent.emit(UiText.StringResource(R.string.feed_deleted))
                    }
                    is BaseResult.Error -> {
                        _toastEvent.emit(UiText.StringResource(R.string.feed_delete_error))
                    }
                }
            }
        }
    }
