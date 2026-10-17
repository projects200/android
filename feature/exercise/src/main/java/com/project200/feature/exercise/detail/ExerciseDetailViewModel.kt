package com.project200.feature.exercise.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.DeleteExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.presentation.utils.UiState
import com.project200.presentation.utils.mapCodeToFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel
    @Inject
    constructor(
        private val exerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase,
        private val deleteExerciseRecordUseCase: DeleteExerciseRecordUseCase,
    ) : ViewModel() {
        private val _exerciseRecord = MutableStateFlow<UiState<ExerciseRecord>>(UiState.Loading)
        val exerciseRecord: StateFlow<UiState<ExerciseRecord>> = _exerciseRecord

        // 1회성 이벤트. emit 직후 popBackStack/토스트로 화면 사라짐
        private val _deleteResult = MutableSharedFlow<BaseResult<Unit>>(replay = 1)
        val deleteResult: SharedFlow<BaseResult<Unit>> = _deleteResult.asSharedFlow()
        private val notifications = Channel<String>(Channel.BUFFERED)
        val refreshMessages = notifications.receiveAsFlow()
        private var initializedRecordId: String? = null
        private var requestId = 0L
        private var hasStarted = false
        private var screenStarted = false
        private var localReadSinceStop = false

        fun getExerciseRecord(
            recordId: String,
            refresh: Boolean = true,
            force: Boolean = false,
            localOnly: Boolean = false,
        ) {
            if (!force && initializedRecordId == recordId) return
            initializedRecordId = recordId
            val currentRequest = ++requestId
            viewModelScope.launch {
                delay(LOADING_DELAY)
                val result = exerciseRecordDetailUseCase(recordId, refresh, localOnly)
                if (currentRequest != requestId) return@launch
                when (result) {
                    is BaseResult.Success -> {
                        _exerciseRecord.value = UiState.Success(result.data)
                        result.refreshError?.let { notifications.send(it.message ?: "기록을 새로고침하지 못했습니다.") }
                    }
                    is BaseResult.Error -> {
                        if (refresh && _exerciseRecord.value is UiState.Success) {
                            notifications.send(result.message ?: "기록을 새로고침하지 못했습니다.")
                        } else {
                            val failure = mapCodeToFailure(result.errorCode, result.message)
                            _exerciseRecord.value = UiState.Error(failure)
                            notifications.send(result.message ?: "기록을 불러오는데 실패했습니다.")
                        }
                    }
                }
            }
        }

        fun onScreenStarted(recordId: String) {
            if (screenStarted) return
            screenStarted = true
            if (hasStarted && !localReadSinceStop) {
                getExerciseRecord(recordId, refresh = false, force = true, localOnly = true)
            }
            hasStarted = true
        }

        fun onScreenStopped() {
            screenStarted = false
            localReadSinceStop = false
        }

        fun onOwnWrite(recordId: String) {
            localReadSinceStop = true
            getExerciseRecord(recordId, refresh = false, force = true)
        }

        fun deleteExerciseRecord(recordId: String) {
            viewModelScope.launch {
                _deleteResult.emit(deleteExerciseRecordUseCase(recordId))
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun consumeDeleteResult() {
            _deleteResult.resetReplayCache()
        }

        companion object {
            private const val LOADING_DELAY = 300L
        }
    }
