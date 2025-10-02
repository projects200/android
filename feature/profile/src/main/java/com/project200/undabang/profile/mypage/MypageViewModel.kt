package com.project200.undabang.profile.mypage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.OpenUrl
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.GetExerciseCountInMonthUseCase
import com.project200.domain.usecase.GetOpenUrlUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class MypageViewModel
    @Inject
    constructor(
        private val getExerciseCountInMonthUseCase: GetExerciseCountInMonthUseCase,
        private val getUserProfileUseCase: GetUserProfileUseCase,
        private val getOpenUrlUseCase: GetOpenUrlUseCase,
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _profile = MutableLiveData<UserProfile>()
        val profile: LiveData<UserProfile> = _profile

        private val _selectedMonth = MutableLiveData<YearMonth>()
        val selectedMonth: LiveData<YearMonth> = _selectedMonth

        private val exerciseCache = mutableMapOf<YearMonth, Set<LocalDate>>()

        private val _exerciseDates = MutableLiveData<Set<LocalDate>>(emptySet())
        val exerciseDates: LiveData<Set<LocalDate>> = _exerciseDates

        private val _openUrl = MutableLiveData<OpenUrl>()
        val openUrl: LiveData<OpenUrl> = _openUrl

        private val _toast = MutableSharedFlow<Boolean>()
        val toast: SharedFlow<Boolean> = _toast

        init {
            getProfile()
            getOpenUrl()
            val initialMonth = clockProvider.yearMonthNow()
            onMonthChanged(initialMonth)
        }

        fun onMonthChanged(newMonth: YearMonth) {
            _selectedMonth.value = newMonth

            if (exerciseCache.containsKey(newMonth)) {
                return
            }
            getExerciseCounts(newMonth, clockProvider.now())
        }

        fun getProfile() {
            viewModelScope.launch {
                when (val result = getUserProfileUseCase()) {
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
            yearMonth: YearMonth,
            today: LocalDate,
        ) {
            viewModelScope.launch {
                val startDate = yearMonth.atDay(1)
                // 이번 달인 경우, 끝 날짜를 오늘로 설정
                val endDate =
                    if (yearMonth == YearMonth.from(today)) today else yearMonth.atEndOfMonth()

                when (val result = getExerciseCountInMonthUseCase(startDate, endDate)) {
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

        fun getOpenUrl() {
            viewModelScope.launch {
                when (val result = getOpenUrlUseCase()) {
                    is BaseResult.Success -> {
                        _openUrl.value = result.data
                    }
                    is BaseResult.Error -> {
                        _openUrl.value = OpenUrl(-1L, "")
                    }
                }
            }
        }
    }
