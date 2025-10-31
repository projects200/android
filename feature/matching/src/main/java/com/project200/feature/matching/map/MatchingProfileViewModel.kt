package com.project200.feature.matching.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.MatchingMemberProfile
import com.project200.domain.usecase.CreateChatRoomUseCase
import com.project200.domain.usecase.GetMatchingMemberExerciseUseCase
import com.project200.domain.usecase.GetMatchingProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class MatchingProfileViewModel
    @Inject
    constructor(
        private val getMatchingProfileUseCase: GetMatchingProfileUseCase,
        private val getMemberExerciseUseCase: GetMatchingMemberExerciseUseCase,
        private val createChatRoomUseCase: CreateChatRoomUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private var memberId: String = ""

        private val _profile = MutableLiveData<MatchingMemberProfile>()
        val profile: LiveData<MatchingMemberProfile> = _profile

        private val _selectedMonth = MutableLiveData<YearMonth>()
        val selectedMonth: LiveData<YearMonth> = _selectedMonth

        private val exerciseCache = mutableMapOf<YearMonth, Set<LocalDate>>()

        private val _exerciseDates = MutableLiveData<Set<LocalDate>>(emptySet())
        val exerciseDates: LiveData<Set<LocalDate>> = _exerciseDates

        private val _toast = MutableSharedFlow<Boolean>()
        val toast: SharedFlow<Boolean> = _toast

        private val _createChatRoomResult = MutableSharedFlow<BaseResult<Long>>()
        val createChatRoomResult: SharedFlow<BaseResult<Long>> = _createChatRoomResult

        fun setMemberId(id: String) {
            memberId = id
            getProfile(memberId)

            val initialMonth = clockProvider.yearMonthNow()
            onMonthChanged(initialMonth)
        }

        fun onMonthChanged(newMonth: YearMonth) {
            _selectedMonth.value = newMonth

            if (exerciseCache.containsKey(newMonth)) {
                return
            }
            getExerciseCounts(memberId, newMonth, clockProvider.now())
        }

        fun getProfile(memberId: String) {
            viewModelScope.launch {
                when (val result = getMatchingProfileUseCase(memberId)) {
                    is BaseResult.Success -> {
                        _profile.value = result.data
                    }

                    is BaseResult.Error -> {
                        _toast.emit(true)
                    }
                }
            }
        }

        // 캘린더 한달 운동 조회
        private fun getExerciseCounts(
            memberId: String,
            yearMonth: YearMonth,
            today: LocalDate,
        ) {
            viewModelScope.launch {
                val startDate = yearMonth.atDay(1)
                // 이번 달인 경우, 끝 날짜를 오늘로 설정
                val endDate =
                    if (yearMonth == YearMonth.from(today)) today else yearMonth.atEndOfMonth()

                when (val result = getMemberExerciseUseCase(memberId, startDate, endDate)) {
                    is BaseResult.Success -> {
                        // 운동 기록 횟수 set 으로 변환
                        val datesWithExercise =
                            result.data
                                .filter { it.count > 0 }
                                .map { it.date }
                                .toSet()

                        exerciseCache[yearMonth] = datesWithExercise
                        _exerciseDates.value = exerciseCache.values.flatten().toSet()
                    }

                    is BaseResult.Error -> {
                        _toast.emit(true)
                    }
                }
            }
        }

        fun onPreviousMonthClicked() {
            val currentMonth = _selectedMonth.value ?: YearMonth.now()
            val newMonth = currentMonth.minusMonths(1)

            _selectedMonth.value = newMonth
        }

        fun onNextMonthClicked() {
            val currentMonth = _selectedMonth.value ?: YearMonth.now()
            val newMonth = currentMonth.plusMonths(1)

            if (newMonth.isAfter(YearMonth.now())) {
                return
            }

            _selectedMonth.value = newMonth
        }

        fun createChatRoom() {
            viewModelScope.launch {
                _createChatRoomResult.emit(createChatRoomUseCase(memberId))
            }
        }

        fun blockMember() {
            viewModelScope.launch {
            }
        }
    }
