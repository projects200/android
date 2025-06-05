package com.project200.feature.exercise.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.DeleteExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase,
    private val deleteExerciseRecordUseCase: DeleteExerciseRecordUseCase
) : ViewModel() {
    val recordId: Long? = savedStateHandle.get<Long>("recordId")

    private val _exerciseRecord = MutableLiveData<BaseResult<ExerciseRecord>>()
    val exerciseRecord: LiveData<BaseResult<ExerciseRecord>> = _exerciseRecord

    private val _deleteResult = MutableLiveData<BaseResult<Unit>>()
    val deleteResult: LiveData<BaseResult<Unit>> = _deleteResult

    fun getExerciseRecord() {
        viewModelScope.launch {
            _exerciseRecord.value = recordId?.let { exerciseRecordDetailUseCase(it) }
        }
    }

    fun deleteExerciseRecord() {
        viewModelScope.launch {
            _deleteResult.value = recordId?.let { deleteExerciseRecordUseCase(it) }
        }
    }
}