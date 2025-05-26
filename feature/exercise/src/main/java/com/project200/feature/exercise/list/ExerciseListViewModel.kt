package com.project200.feature.exercise.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ExerciseListViewModel @Inject constructor(
) : ViewModel() {

    private val _currentDate = MutableLiveData<LocalDate>(LocalDate.now())
    val currentDate: LiveData<LocalDate> = _currentDate

    private val _exerciseList = MutableLiveData<List<ExerciseListItem>>(
        listOf(
        ExerciseListItem(1L, "가볍게 공원 산책하기", "유산소", LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1), null),
        ExerciseListItem(2L, "집에서 하는 홈트레이닝", "근력", LocalDateTime.now().minusDays(1).minusHours(1), LocalDateTime.now().minusDays(1), null),
        ExerciseListItem(3L, "한강 따라 자전거 타기", "유산소", LocalDateTime.now().minusDays(2).minusHours(3), LocalDateTime.now().minusDays(2), null)
    ))
    val exerciseList: LiveData<List<ExerciseListItem>> = _exerciseList

    private fun loadExercises(date: LocalDate) {
        viewModelScope.launch {
        }
    }

    fun changeDate(date: String) {
        val newDate = LocalDate.parse(date)
        if (newDate != _currentDate.value) {
            _currentDate.value = newDate
            loadExercises(newDate)
        }
    }
}