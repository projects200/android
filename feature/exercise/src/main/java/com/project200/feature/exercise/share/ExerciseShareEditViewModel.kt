package com.project200.feature.exercise.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.feature.exercise.utils.ShareEventData
import com.project200.feature.exercise.utils.StickerState
import com.project200.feature.exercise.utils.StickerTransformInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseShareEditViewModel
    @Inject
    constructor(
        private val getExerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase,
    ) : ViewModel() {
        private val _selectedTheme = MutableStateFlow(StickerTheme.DARK)
        val selectedTheme: StateFlow<StickerTheme> = _selectedTheme.asStateFlow()

        private val _exerciseRecord = MutableStateFlow<ExerciseRecord?>(null)
        val exerciseRecord: StateFlow<ExerciseRecord?> = _exerciseRecord.asStateFlow()

        private val _backgroundImageUrl = MutableStateFlow<String?>(null)
        val backgroundImageUrl: StateFlow<String?> = _backgroundImageUrl.asStateFlow()

        private val _isLoading = MutableStateFlow(true)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _shareEvent = MutableSharedFlow<ShareEventData>()
        val shareEvent: SharedFlow<ShareEventData> = _shareEvent.asSharedFlow()
        private var initializedRecordId: String? = null
        private var recordRequestId = 0L
        private val notifications = Channel<String>(Channel.BUFFERED)
        val refreshMessages = notifications.receiveAsFlow()

        val stickerState: Flow<StickerState> =
            _exerciseRecord
                .filterNotNull()
                .combine(_selectedTheme) { record, theme -> StickerState(record, theme) }

        fun loadExerciseRecord(recordId: String) {
            if (initializedRecordId == recordId) return
            initializedRecordId = recordId
            val requestId = ++recordRequestId
            viewModelScope.launch {
                _isLoading.value = true
                val result = getExerciseRecordDetailUseCase(recordId, refresh = false)
                if (requestId != recordRequestId) return@launch
                when (result) {
                    is BaseResult.Success -> {
                        _exerciseRecord.value = result.data
                        _backgroundImageUrl.value = result.data.pictures?.firstOrNull()?.source
                        result.refreshError?.let { notifications.send(it.message ?: "기록을 새로고침하지 못했습니다.") }
                        if (result.isCachedSnapshot) {
                            viewModelScope.launch { getExerciseRecordDetailUseCase(recordId, refresh = true) }
                        }
                    }
                    is BaseResult.Error -> {
                        notifications.send(result.message ?: "기록을 불러오는데 실패했습니다.")
                    }
                }
                _isLoading.value = false
            }
        }

        fun selectTheme(theme: StickerTheme) {
            _selectedTheme.value = theme
        }

        fun requestShare(transformInfo: StickerTransformInfo) {
            val record = _exerciseRecord.value ?: return
            viewModelScope.launch {
                _isLoading.value = true
                _shareEvent.emit(ShareEventData(record, _selectedTheme.value, transformInfo))
            }
        }

        fun onShareCompleted() {
            _isLoading.value = false
        }
    }
