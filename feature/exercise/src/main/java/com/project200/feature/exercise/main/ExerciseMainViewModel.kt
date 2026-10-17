package com.project200.feature.exercise.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.common.utils.NetworkMonitor
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
class ExerciseMainViewModel
    @Inject
    constructor(
        private val getExerciseCountInMonthUseCase: GetExerciseCountInMonthUseCase,
        private val getScoreUseCase: GetScoreUseCase,
        private val getScorePolicyUseCase: GetScorePolicyUseCase,
        private val getExerciseRecordListUseCase: GetExerciseRecordListUseCase,
        private val getExpectedScoreInfoUseCase: GetExpectedScoreInfoUseCase,
        private val clockProvider: ClockProvider,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _selectedMonth = MutableLiveData<YearMonth>()
        val selectedMonth: LiveData<YearMonth> = _selectedMonth

        private val _exerciseDates = MutableLiveData<Set<LocalDate>>(emptySet())
        val exerciseDates: LiveData<Set<LocalDate>> = _exerciseDates
        private val _pendingDates = MutableLiveData<Set<LocalDate>>(emptySet())
        val pendingDates: LiveData<Set<LocalDate>> = _pendingDates
        private val pendingDateCache = mutableMapOf<YearMonth, Set<LocalDate>>()
        private val monthRequestIds = mutableMapOf<YearMonth, Long>()
        private var refreshNotificationShown = false
        var scrollPosition = 0
        private var hasStarted = false
        private var screenStarted = false
        private var localReadSinceStop = false

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

        // 초기값과 조회 실패가 삭제 검증의 빈 목록 선택자로 노출되지 않게 합니다.
        private val _isExerciseListLoaded = MutableLiveData(false)
        val isExerciseListLoaded: LiveData<Boolean> = _isExerciseListLoaded
        private val _exerciseListError = MutableLiveData<BaseResult.Error?>()
        val exerciseListError: LiveData<BaseResult.Error?> = _exerciseListError
        private var exerciseListRequestId = 0L

        private val expectedScoreInfo = MutableLiveData<ExpectedScoreInfo?>()

        private val _earnablePoints = MutableLiveData<Int>()
        val earnablePoints: LiveData<Int> = _earnablePoints

        init {
            val today = clockProvider.now()
            _selectedMonth.value = YearMonth.from(today)
            _selectedDate.value = today
            getExerciseCounts(YearMonth.from(today), today)
            loadScorePolicy()
            getScore()
            loadExpectedScoreInfo()
            loadExercisesForDate(today)
        }

        fun onMonthChanged(newMonth: YearMonth) {
            if (_selectedMonth.value == newMonth) return
            _selectedMonth.value = newMonth
            refreshNotificationShown = false
            if (!exerciseCache.containsKey(newMonth)) getExerciseCounts(newMonth, clockProvider.now())
        }

        fun onDateSelected(date: LocalDate) {
            if (_selectedDate.value == date) return
            refreshNotificationShown = false
            _isExerciseListLoaded.value = false
            _selectedDate.value = date
            _exerciseList.value = emptyList()
            loadExercisesForDate(date)
            updateEarnablePoints()
        }

        private fun loadExercisesForDate(
            date: LocalDate,
            refresh: Boolean = true,
            localOnly: Boolean = false,
        ) {
            val requestId = ++exerciseListRequestId
            _isExerciseListLoaded.value = false
            _exerciseListError.value = null
            viewModelScope.launch {
                val result = getExerciseRecordListUseCase(date, refresh, localOnly)
                // 이전 날짜 또는 이전 갱신의 응답은 행·오류·완료 상태 어느 것도 바꾸지 않습니다.
                if (requestId != exerciseListRequestId || date != _selectedDate.value) return@launch
                when (result) {
                    is BaseResult.Success -> {
                        _exerciseList.value = result.data
                        _isExerciseListLoaded.value = true
                        result.refreshError?.let { notifyRefreshError(it) }
                    }
                    is BaseResult.Error -> {
                        _exerciseListError.value = result
                        notifyRefreshError(result)
                    }
                }
            }
        }

        private fun getExerciseCounts(
            yearMonth: YearMonth,
            today: LocalDate,
            refresh: Boolean = true,
            localOnly: Boolean = false,
        ) {
            val requestId = (monthRequestIds[yearMonth] ?: 0) + 1
            monthRequestIds[yearMonth] = requestId
            viewModelScope.launch {
                val endDate = if (yearMonth == YearMonth.from(today)) today else yearMonth.atEndOfMonth()
                val result = getExerciseCountInMonthUseCase(yearMonth.atDay(1), endDate, refresh, localOnly)
                if (monthRequestIds[yearMonth] != requestId) return@launch
                when (result) {
                    is BaseResult.Success -> {
                        exerciseCache[yearMonth] = result.data.filter { it.count > 0 }.map { it.date }.toSet()
                        pendingDateCache[yearMonth] = result.data.filter { it.syncPending }.map { it.date }.toSet()
                        _exerciseDates.value = exerciseCache.values.flatten().toSet()
                        _pendingDates.value = pendingDateCache.values.flatten().toSet()
                        if (yearMonth == clockProvider.yearMonthNow()) _exerciseCount.value = result.data.sumOf { it.count }
                        result.refreshError?.let { notifyRefreshError(it) }
                    }
                    is BaseResult.Error -> notifyRefreshError(result)
                }
            }
        }

        private fun notifyRefreshError(error: BaseResult.Error) {
            if (error.errorCode == "NETWORK_ERROR" && !networkMonitor.isCurrentlyConnected()) return
            if (refreshNotificationShown) return
            refreshNotificationShown = true
            _toastMessage.value = error.message ?: "기록을 새로고침하지 못했습니다."
        }

        fun consumeToastMessage() {
            _toastMessage.value = null
        }

        // 점수 정책 조회
        private fun loadScorePolicy() {
            viewModelScope.launch {
                when (val result = getScorePolicyUseCase()) {
                    is BaseResult.Success -> {
                        _scorePolicy.value = result.data
                    }
                    is BaseResult.Error -> {
                        notifyRefreshError(result)
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
                        notifyRefreshError(result)
                    }
                }
            }
        }

        fun loadExpectedScoreInfo() {
            viewModelScope.launch {
                when (val result = getExpectedScoreInfoUseCase()) {
                    is BaseResult.Success -> {
                        expectedScoreInfo.value = result.data
                    }
                    is BaseResult.Error -> {
                        expectedScoreInfo.value = null
                    }
                }
                updateEarnablePoints()
            }
        }

        private fun updateEarnablePoints() {
            val selectedDate = _selectedDate.value
            val scoreInfo = expectedScoreInfo.value

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

            val canEarnScore =
                scoreInfo.currentUserScore < scoreInfo.maxScore &&
                    isWithinValidWindow &&
                    scoreInfo.earnableScoreDays.contains(selectedDate)

            // 조건에 따라 점수 값을 설정. 불가능하면 0
            val points = if (canEarnScore) scoreInfo.pointsPerExercise else 0

            // LiveData의 값이 변경되었을 때만 업데이트
            if (_earnablePoints.value != points) {
                _earnablePoints.value = points
            }
        }

        /** 명시적 새로고침만 서버에 요청합니다. 선택 날짜·월·스크롤은 유지합니다. */
        fun refreshData() {
            refreshNotificationShown = false
            reloadExerciseQueries(refresh = true)
            getScore()
        }

        /** 자신의 생성·수정·삭제 후에는 캐시 우선으로 읽고, 온라인 미적중만 갱신합니다. */
        fun onOwnWrite() {
            localReadSinceStop = true
            refreshNotificationShown = false
            reloadExerciseQueries(refresh = false)
            getScore()
            loadExpectedScoreInfo()
        }

        /** 첫 시작은 init 조회를 유지하고, 복귀는 네트워크 없이 한 번만 다시 읽습니다. */
        fun onScreenStarted() {
            if (screenStarted) return
            screenStarted = true
            if (hasStarted && !localReadSinceStop) {
                reloadExerciseQueries(refresh = false, localOnly = true)
            }
            hasStarted = true
        }

        fun onScreenStopped() {
            screenStarted = false
            localReadSinceStop = false
        }

        private fun reloadExerciseQueries(
            refresh: Boolean,
            localOnly: Boolean = false,
        ) {
            val today = clockProvider.now()
            val visibleMonths = listOfNotNull(_selectedMonth.value, clockProvider.yearMonthNow()).toSet()
            val months = if (refresh) visibleMonths else exerciseCache.keys + visibleMonths
            months.forEach { getExerciseCounts(it, today, refresh, localOnly) }
            _selectedDate.value?.let { loadExercisesForDate(it, refresh, localOnly) }
        }
    }
