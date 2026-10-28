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
        private var listRequestId = 0L

        init {
            loadCustomTimers()
        }

        /** 최초 스냅샷을 표시한 뒤 별도 갱신은 Room에만 반영합니다 */
        fun loadCustomTimers(refresh: Boolean = false) {
            val requestId = ++listRequestId
            viewModelScope.launch {
                val result = getCustomTimerListUseCase(refresh)
                if (requestId != listRequestId) return@launch
                if (result is BaseResult.Success) {
                    _customTimerList.value = result.data
                    if (!refresh && result.isCachedSnapshot) {
                        viewModelScope.launch { getCustomTimerListUseCase(refresh = true) }
                    }
                }
            }
        }

        /** savedStateHandle 새로고침 플래그로 복귀했을 때 씁니다. 서버를 보지 않고, 실패해도 기존 목록을 유지합니다 */
        fun refreshLocalCustomTimers() {
            val requestId = ++listRequestId
            viewModelScope.launch {
                val result = getLocalCustomTimerListUseCase()
                if (requestId != listRequestId) return@launch
                // 로컬 스냅샷이 이미 화면에 있어 조회 실패를 알릴 것이 없다
                if (result is BaseResult.Success) {
                    _customTimerList.value = result.data
                }
            }
        }
    }
