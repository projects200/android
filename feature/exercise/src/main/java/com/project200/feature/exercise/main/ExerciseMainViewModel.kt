package com.project200.feature.exercise.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PolicyType
import com.project200.domain.model.ScorePolicy
import com.project200.domain.usecase.GetExerciseCountInMonthUseCase
import com.project200.domain.usecase.GetScoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ExerciseMainViewModel @Inject constructor(
    private val getExerciseCountInMonthUseCase: GetExerciseCountInMonthUseCase,
    private val getScoreUseCase: GetScoreUseCase,
    private val clockProvider: ClockProvider
) : ViewModel() {

    private val _selectedMonth = MutableLiveData<YearMonth>()
    val selectedMonth: LiveData<YearMonth> = _selectedMonth

    private val _exerciseDates = MutableLiveData<Set<LocalDate>>()
    val exerciseDates: LiveData<Set<LocalDate>> = _exerciseDates

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val exerciseCache = mutableMapOf<YearMonth, Set<LocalDate>>()

    private val _score = MutableLiveData<Int>()
    val score: LiveData<Int> = _score

    private val _policyData = MutableLiveData<List<ScorePolicy>>()
    val policyData: LiveData<List<ScorePolicy>> = _policyData

    private val _exerciseCount = MutableLiveData<Int>()
    val exerciseCount: LiveData<Int> = _exerciseCount

    init {
        if (_selectedMonth.value == null) {
            _selectedMonth.value = clockProvider.yearMonthNow()
        }
        getExerciseCntThisMonth(clockProvider.yearMonthNow(), clockProvider.now())
        loadPolicyData()
    }

    fun onMonthChanged(newMonth: YearMonth) {
        // 중복 호출 방지
        if (_selectedMonth.value == newMonth) return

        _selectedMonth.value = newMonth

        if (!exerciseCache.containsKey(newMonth)) { // 캐시에 데이터가 있으면 캐시 데이터를 사용하고 api 호출 x
            getExerciseCounts(newMonth, clockProvider.now())
        }
    }

    // 캘린더 한달 운동 조회
    private fun getExerciseCounts(yearMonth: YearMonth, today: LocalDate) {
        viewModelScope.launch {
            val startDate = yearMonth.atDay(1)
            // 이번 달인 경우, 끝 날짜를 오늘로 설정
            val endDate = if (yearMonth == YearMonth.from(today)) today else yearMonth.atEndOfMonth()

            when (val result = getExerciseCountInMonthUseCase(startDate, endDate)) {
                is BaseResult.Success -> {
                    // 운동 기록 횟수 set 으로 변환
                    val datesWithExercise = result.data
                        .filter { it.count > 0 }
                        .map { it.date }
                        .toSet()

                    exerciseCache[yearMonth] = datesWithExercise
                    _exerciseDates.value = exerciseCache.values.flatten().toSet()
                }
                is BaseResult.Error -> {
                    _toastMessage.value = result.message
                }
            }
        }
    }

    private fun loadPolicyData() {
        // 임시 정책 데이터
        val tempPolicies = listOf(
            ScorePolicy(PolicyType.EXERCISE_SCORE_MAX_POINTS.key, 100, "POINTS"),
            ScorePolicy(PolicyType.EXERCISE_SCORE_MIN_POINTS.key, 0, "POINTS"),
            ScorePolicy(PolicyType.SIGNUP_INITIAL_POINTS.key, 35, "POINTS"),
            ScorePolicy(PolicyType.POINTS_PER_EXERCISE.key, 3, "POINTS"),
            ScorePolicy(PolicyType.EXERCISE_RECORD_VALIDITY_PERIOD.key, 2, "DAYS"),
            ScorePolicy(PolicyType.PENALTY_INACTIVITY_THRESHOLD_DAYS.key, 7, "DAYS"),
            ScorePolicy(PolicyType.PENALTY_SCORE_DECREMENT_POINTS.key, 1, "POINTS")
        )
        _policyData.value = tempPolicies
    }

    // 이번 달 운동 횟수 조회
    private fun getExerciseCntThisMonth(yearMonth: YearMonth, today: LocalDate) {
        viewModelScope.launch {
            when(val result = getExerciseCountInMonthUseCase(yearMonth.atDay(1), today)) {
                is BaseResult.Success -> {
                    val totalCount = result.data.sumOf { it.count }
                    _exerciseCount.value = totalCount
                }
                is BaseResult.Error -> {
                    _toastMessage.value = result.message
                    _exerciseCount.value = 0 // 에러 발생 시 횟수를 0으로 설정
                }
            }
        }
    }

    private fun getScore() {
        viewModelScope.launch {
            when (val result = getScoreUseCase()) {
                is BaseResult.Success -> {
                    _score.value = result.data.score
                }
                is BaseResult.Error -> {
                    _toastMessage.value = result.message
                }
            }
        }
    }

    fun refreshData() {
        exerciseCache.clear()
        _selectedMonth.value?.let {
            getExerciseCounts(it, clockProvider.now())
        }
        getExerciseCntThisMonth(clockProvider.yearMonthNow(), clockProvider.now())
        getScore()
    }
}