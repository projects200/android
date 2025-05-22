package com.project200.feature.exercise.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase
): ViewModel() {
    val recordId: Int? = savedStateHandle.get<Int>("recordId")

    private val _exerciseRecord = MutableLiveData<BaseResult<ExerciseRecord>>()
    val exerciseRecord: LiveData<BaseResult<ExerciseRecord>> = _exerciseRecord

    fun getExerciseRecord() {
        viewModelScope.launch {
            _exerciseRecord.value = recordId?.let { exerciseRecordDetailUseCase(it) }
        }
    }
}