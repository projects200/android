package com.project200.feature.exercise.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.GetExerciseRecordListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    private val getExerciseRecordListUseCase: GetExerciseRecordListUseCase
) : ViewModel() {

    private val _currentDate = MutableLiveData<LocalDate>(LocalDate.now())
    val currentDate: LiveData<LocalDate> = _currentDate

    private val _exerciseList = MutableLiveData<List<ExerciseListItem>>()
    val exerciseList: LiveData<List<ExerciseListItem>> = _exerciseList

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private fun loadExercises(date: LocalDate) {
        viewModelScope.launch {
            Timber.tag(TAG).d("loadExercises")
            when (val result = getExerciseRecordListUseCase(date)) {
                is BaseResult.Success -> {
                    _exerciseList.value = result.data // 성공 시 데이터 업데이트
                }
                is BaseResult.Error -> {
                    _exerciseList.value = emptyList() // 실패 시 목록 비우기
                    _toastMessage.value = result.message ?: LOAD_FAIL
                }
            }
        }
    }

    fun changeDate(date: String) {
        val newDate = LocalDate.parse(date)
        _currentDate.value = newDate
        loadExercises(newDate)
    }

    fun loadCurrentDateExercises() {
        _currentDate.value?.let { currentSelectedDate ->
            Timber.tag(TAG).d("리스트 갱신: $currentSelectedDate")
            loadExercises(currentSelectedDate)
        }
    }

    companion object {
        const val LOAD_FAIL = "기록을 불러오는데 실패했습니다."
        const val TAG = "ExerciseListViewModel"
    }
}