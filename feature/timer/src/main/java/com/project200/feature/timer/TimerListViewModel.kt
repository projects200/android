package com.project200.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.usecase.GetCustomTimerListUseCase
import com.project200.domain.usecase.GetLocalCustomTimerListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerListViewModel
    @Inject
    constructor(
        private val getCustomTimerListUseCase: GetCustomTimerListUseCase,
        private val getLocalCustomTimerListUseCase: GetLocalCustomTimerListUseCase,
    ) : ViewModel() {
        private val _customTimerList = MutableStateFlow<List<CustomTimer>>(emptyList())
        val customTimerList: StateFlow<List<CustomTimer>> = _customTimerList.asStateFlow()

        private val _errorToast = MutableSharedFlow<BaseResult.Error>()
        val errorToast: SharedFlow<BaseResult.Error> = _errorToast.asSharedFlow()

        init {
            loadCustomTimers()
        }

        /** 화면 최초 진입용입니다. 온라인이면 서버 목록을 반영한 뒤 읽습니다 */
        fun loadCustomTimers() {
            viewModelScope.launch {
                when (val result = getCustomTimerListUseCase()) {
                    is BaseResult.Success -> {
                        _customTimerList.value = result.data
                    }
                    is BaseResult.Error -> {
                        _errorToast.emit(result)
                    }
                }
            }
        }

        /** savedStateHandle 새로고침 플래그로 복귀했을 때 씁니다. 서버를 보지 않고, 실패해도 기존 목록을 유지합니다 */
        fun refreshLocalCustomTimers() {
            viewModelScope.launch {
                when (val result = getLocalCustomTimerListUseCase()) {
                    is BaseResult.Success -> {
                        _customTimerList.value = result.data
                    }
                    is BaseResult.Error -> {
                        _errorToast.emit(result)
                    }
                }
            }
        }
    }
