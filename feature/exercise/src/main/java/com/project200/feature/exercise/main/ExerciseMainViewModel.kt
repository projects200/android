package com.project200.feature.exercise.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.PolicyGroup
import com.project200.domain.model.Score
import com.project200.domain.usecase.GetExerciseCountInMonthUseCase
import com.project200.domain.usecase.GetExerciseRecordListUseCase
import com.project200.domain.usecase.GetExpectedScoreInfoUseCase
import com.project200.domain.usecase.GetScorePolicyUseCase
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
    private val getScorePolicyUseCase: GetScorePolicyUseCase,
    private val getExerciseRecordListUseCase: GetExerciseRecordListUseCase,
    private val getExpectedScoreInfoUseCase: GetExpectedScoreInfoUseCase,
    private val clockProvider: ClockProvider
) : ViewModel() {

    private val _selectedMonth = MutableLiveData<YearMonth>()
    val selectedMonth: LiveData<YearMonth> = _selectedMonth

    private val _exerciseDates = MutableLiveData<Set<LocalDate>>(emptySet())
    val exerciseDates: LiveData<Set<LocalDate>> = _exerciseDates

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val exerciseCache = mutableMapOf<YearMonth, Set<LocalDate>>()

    private val _score = MutableLiveData<Score>()
    val score: LiveData<Score> = _score

    private val _scorePolicy = MutableLiveData<PolicyGroup?>()
    val scorePolicy: LiveData<PolicyGroup?> = _scorePolicy

    private val _exerciseCount = MutableLiveData<Int>()
    val exerciseCount: LiveData<Int> = _exerciseCount

    // 선택된 날짜
    private val _selectedDate = MutableLiveData<LocalDate>()
    val selectedDate: LiveData<LocalDate> = _selectedDate

    // 운동 리스트
    private val _exerciseList = MutableLiveData<List<ExerciseListItem>>()
    val exerciseList: LiveData<List<ExerciseListItem>> = _exerciseList

    private val _expectedScoreInfo = MutableLiveData<ExpectedScoreInfo?>()

    private val _earnablePoints = MutableLiveData<Int>()
    val earnablePoints: LiveData<Int> = _earnablePoints

    init {
        if (_selectedMonth.value == null) {
            _selectedMonth.value = clockProvider.yearMonthNow()
        }

        val today = clockProvider.now()
        _selectedMonth.value = YearMonth.from(today)
        _selectedDate.value = today // 오늘 날짜를 기본 선택 날짜로 설정

        getExerciseCntThisMonth(clockProvider.yearMonthNow(), clockProvider.now())
        loadScorePolicy()
        loadExercisesForDate(today) // 앱 시작 시 오늘 날짜의 운동 목록 로드
    }

    fun onMonthChanged(newMonth: YearMonth) {
        // 중복 호출 방지
        if (_selectedMonth.value == newMonth) return

        _selectedMonth.value = newMonth

        if (!exerciseCache.containsKey(newMonth)) { // 캐시에 데이터가 있으면 캐시 데이터를 사용하고 api 호출 x
            getExerciseCounts(newMonth, clockProvider.now())
        }
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
        loadExercisesForDate(date)
        updateEarnablePoints()
    }

    // --- 특정 날짜의 운동 목록을 가져오는 함수 ---
    private fun loadExercisesForDate(date: LocalDate) {
        viewModelScope.launch {
            when (val result = getExerciseRecordListUseCase(date)) {
                is BaseResult.Success -> {
                    _exerciseList.value = result.data
                }
                is BaseResult.Error -> {
                    _exerciseList.value = emptyList() // 실패 시 빈 리스트
                    _toastMessage.value = result.message ?: "기록을 불러오는데 실패했습니다."
                }
            }
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

    // 점수 정책 조회
    private fun loadScorePolicy() {
        viewModelScope.launch {
            when (val result = getScorePolicyUseCase()) {
                is BaseResult.Success -> {
                    _scorePolicy.value = result.data
                }
                is BaseResult.Error -> {
                    _toastMessage.value = result.message
                }
            }
        }
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
                    _score.value = result.data
                }
                is BaseResult.Error -> {
                    _toastMessage.value = result.message
                }
            }
        }
    }

    fun loadExpectedScoreInfo() {
        viewModelScope.launch {
            when (val result = getExpectedScoreInfoUseCase()) {
                is BaseResult.Success -> { _expectedScoreInfo.value = result.data }
                is BaseResult.Error -> { _expectedScoreInfo.value = null }
            }
            updateEarnablePoints()
        }
    }

    private fun updateEarnablePoints() {
        val selectedDate = _selectedDate.value
        val scoreInfo = _expectedScoreInfo.value

        // 정보가 없으면 0점으로 설정
        if (selectedDate == null || scoreInfo == null) {
            _earnablePoints.value = 0
            return
        }

        // 점수 획득이 가능한 모든 조건을 만족하는지 확인
        // 1. 현재 점수가 최대 점수 미만인가?
        // 2. 오늘 날짜가 유효 기간(start ~ end) 내에 포함되는가?
        // 3. 사용자가 선택한 날짜가 점수 획득 가능일인가?
        val isWithinValidWindow =
            !selectedDate.atStartOfDay().isBefore(scoreInfo.validWindow.startDateTime) &&
                    !(selectedDate.atStartOfDay().isAfter(scoreInfo.validWindow.endDateTime))

        val canEarnScore = scoreInfo.currentUserScore < scoreInfo.maxScore &&
                isWithinValidWindow &&
                scoreInfo.earnableScoreDays.contains(selectedDate)

        // 조건에 따라 점수 값을 설정. 불가능하면 0
        val points = if (canEarnScore) scoreInfo.pointsPerExercise else 0

        // LiveData의 값이 변경되었을 때만 업데이트
        if (_earnablePoints.value != points) {
            _earnablePoints.value = points
        }
    }



    fun refreshData() {
        exerciseCache.clear()
        _selectedMonth.value?.let {
            getExerciseCounts(it, clockProvider.now())
        }
        _selectedDate.value?.let {
            loadExercisesForDate(it)
        }
        getExerciseCntThisMonth(clockProvider.yearMonthNow(), clockProvider.now())
        getScore()
    }
}