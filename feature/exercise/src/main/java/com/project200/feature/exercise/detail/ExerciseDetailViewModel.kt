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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

        fun getExerciseRecord(recordId: Long) {
            viewModelScope.launch {
                delay(LOADING_DELAY)
                when (val result = exerciseRecordDetailUseCase(recordId)) {
                    is BaseResult.Success -> {
                        _exerciseRecord.value = UiState.Success(result.data)
                    }
                    is BaseResult.Error -> {
                        val failure = mapCodeToFailure(result.errorCode, result.message)
                        _exerciseRecord.value = UiState.Error(failure)
                    }
                }
            }
        }

        fun deleteExerciseRecord(recordId: Long) {
            viewModelScope.launch {
                _deleteResult.emit(deleteExerciseRecordUseCase(recordId))
            }
        }

        companion object {
            private const val LOADING_DELAY = 300L
        }
    }
