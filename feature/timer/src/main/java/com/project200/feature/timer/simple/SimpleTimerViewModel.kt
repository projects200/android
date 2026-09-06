package com.project200.feature.timer.simple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.usecase.AddSimpleTimerUseCase
import com.project200.domain.usecase.DeleteSimpleTimerUseCase
import com.project200.domain.usecase.EditSimpleTimerUseCase
import com.project200.domain.usecase.GetLocalSimpleTimersUseCase
import com.project200.domain.usecase.GetSimpleTimersUseCase
import com.project200.feature.timer.utils.SimpleTimerServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SimpleTimerViewModel
    @Inject
    constructor(
        private val simpleTimerServiceManager: SimpleTimerServiceManager,
        private val getSimpleTimersUseCase: GetSimpleTimersUseCase,
        private val getLocalSimpleTimersUseCase: GetLocalSimpleTimersUseCase,
        private val addSimpleTimerUseCase: AddSimpleTimerUseCase,
        private val editSimpleTimerUseCase: EditSimpleTimerUseCase,
        private val deleteSimpleTimerUseCase: DeleteSimpleTimerUseCase,
    ) : ViewModel() {
        // Service 인스턴스는 StateFlow로 유지 (Service 자체는 LiveData 노출 그대로)
        private val service = MutableStateFlow<SimpleTimerService?>(null)

        val remainingTime: StateFlow<Long> =
            service
                .flatMapLatest { it?.remainingTime?.asFlow() ?: flowOf(0L) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

        val isTimerRunning: StateFlow<Boolean> =
            service
                .flatMapLatest { it?.isTimerRunning?.asFlow() ?: flowOf(false) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        val totalTime: Long
            get() = service.value?.totalTime ?: 0L

        // 타이머 아이템 리스트
        private val _timerItems = MutableStateFlow<List<SimpleTimer>>(emptyList())
        val timerItems: StateFlow<List<SimpleTimer>> = _timerItems.asStateFlow()

        private var isAscending = true

        // 이벤트를 전달할 SharedFlow 생성
        private val _toastMessage = MutableSharedFlow<SimpleTimerToastMessage>()
        val toastMessage: SharedFlow<SimpleTimerToastMessage> = _toastMessage.asSharedFlow()

        init {
            simpleTimerServiceManager.bindService()
            viewModelScope.launch {
                simpleTimerServiceManager.service.collect { serviceInstance ->
                    service.value = serviceInstance
                }
            }
            loadTimerItems()
        }

        fun setAndStartTimer(timeInSeconds: Int) {
            service.value?.setAndStartTimer(timeInSeconds)
        }

        /** 화면 최초 진입용입니다. 온라인이면 서버 목록을 반영한 뒤 읽습니다 */
        fun loadTimerItems() {
            viewModelScope.launch {
                when (val result = getSimpleTimersUseCase()) {
                    is BaseResult.Success -> {
                        _timerItems.value = sortTimers(result.data, isAscending)
                    }
                    is BaseResult.Error -> {
                        _toastMessage.emit(SimpleTimerToastMessage.GET_ERROR)
                    }
                }
            }
        }

        /** 내 쓰기 직후 재조회용입니다. 서버를 보지 않습니다 */
        private fun reloadLocalTimerItems() {
            viewModelScope.launch {
                when (val result = getLocalSimpleTimersUseCase()) {
                    is BaseResult.Success -> {
                        _timerItems.value = sortTimers(result.data, isAscending)
                    }
                    is BaseResult.Error -> {
                        _toastMessage.emit(SimpleTimerToastMessage.GET_ERROR)
                    }
                }
            }
        }

        fun changeSortOrder() {
            isAscending = !isAscending
            _timerItems.value = sortTimers(_timerItems.value, isAscending)
        }

        private fun sortTimers(
            timers: List<SimpleTimer>,
            ascending: Boolean,
        ): List<SimpleTimer> {
            return if (ascending) {
                timers.sortedBy { it.time }
            } else {
                timers.sortedByDescending { it.time }
            }
        }

        // 전송 대기 행도 목록에 포함되므로 크기 판정에 함께 셉니다. 삭제 대기 행은 로컬 조회에서 이미 제외됩니다
        fun addTimerItem(time: Int) {
            if (_timerItems.value.size >= MAX_TIMER_COUNT) return

            viewModelScope.launch {
                when (addSimpleTimerUseCase(time)) {
                    is BaseResult.Success -> reloadLocalTimerItems()
                    is BaseResult.Error -> _toastMessage.emit(SimpleTimerToastMessage.ADD_ERROR)
                }
            }
        }

        fun deleteTimerItem(localId: String) {
            viewModelScope.launch {
                when (deleteSimpleTimerUseCase(localId)) {
                    is BaseResult.Success -> reloadLocalTimerItems()
                    is BaseResult.Error -> _toastMessage.emit(SimpleTimerToastMessage.DELETE_ERROR)
                }
            }
        }

        // 타이머 시작
        fun startTimer() {
            service.value?.startTimer()
        }

        // 타이머 일시정지
        fun pauseTimer() {
            service.value?.pauseTimer()
        }

        // 타이머 아이템을 수정합니다. 로컬이 원본이라 선반영 없이 재조회로 화면을 갱신합니다
        fun updateTimerItem(
            localId: String,
            time: Int,
        ) {
            viewModelScope.launch {
                when (editSimpleTimerUseCase(localId, time)) {
                    is BaseResult.Success -> reloadLocalTimerItems()
                    is BaseResult.Error -> _toastMessage.emit(SimpleTimerToastMessage.EDIT_ERROR)
                }
            }
        }

        override fun onCleared() {
            simpleTimerServiceManager.unbindService()
            super.onCleared()
        }

        companion object {
            const val MAX_TIMER_COUNT = 6
            const val DEFAULT_ADD_TIME_SEC = 60
        }
    }
