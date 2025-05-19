package com.project200.feature.exercise

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class ExerciseViewModel @Inject constructor(
    private val exerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase
): ViewModel() {
    var recordId: Int = 0

    private val _exerciseRecord = MutableLiveData<BaseResult<ExerciseRecord>>()
    val exerciseRecord: LiveData<BaseResult<ExerciseRecord>> = _exerciseRecord

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun getExerciseRecord() {
        viewModelScope.launch {
            _exerciseRecord.value = exerciseRecordDetailUseCase(recordId)
        }
    }
}