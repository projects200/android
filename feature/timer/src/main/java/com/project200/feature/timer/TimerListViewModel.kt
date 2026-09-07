package com.project200.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.usecase.GetCustomTimerListUseCase
import com.project200.domain.usecase.GetLocalCustomTimerListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

        init {
            loadCustomTimers()
        }

        /** 화면 최초 진입용입니다. 온라인이면 서버 목록을 반영한 뒤 읽습니다 */
        fun loadCustomTimers() {
            viewModelScope.launch {
                val result = getCustomTimerListUseCase()
                // 로컬 스냅샷이 이미 화면에 있어 조회 실패를 알릴 것이 없다
                if (result is BaseResult.Success) {
                    _customTimerList.value = result.data
                }
            }
        }

        /** savedStateHandle 새로고침 플래그로 복귀했을 때 씁니다. 서버를 보지 않고, 실패해도 기존 목록을 유지합니다 */
        fun refreshLocalCustomTimers() {
            viewModelScope.launch {
                val result = getLocalCustomTimerListUseCase()
                // 로컬 스냅샷이 이미 화면에 있어 조회 실패를 알릴 것이 없다
                if (result is BaseResult.Success) {
                    _customTimerList.value = result.data
                }
            }
        }
    }
