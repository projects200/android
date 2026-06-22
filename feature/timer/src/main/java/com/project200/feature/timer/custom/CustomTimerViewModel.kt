package com.project200.feature.timer.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.usecase.DeleteCustomTimerUseCase
import com.project200.domain.usecase.GetCustomTimerUseCase
import com.project200.feature.timer.utils.CustomTimerServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomTimerViewModel
    @Inject
    constructor(
        private val timerServiceManager: CustomTimerServiceManager,
        private val getCustomTimerUseCase: GetCustomTimerUseCase,
        private val deleteCustomTimerUseCase: DeleteCustomTimerUseCase,
    ) : ViewModel() {
        private var timerId: Long = -1

        // Service와 통신하기 위한 설정
        private val service = MutableStateFlow<CustomTimerService?>(null)

        // service가 연결되면, service 내부 LiveData를 관찰
        val isTimerRunning: StateFlow<Boolean> =
            service.flatMapLatest { it?.isTimerRunning?.asFlow() ?: flowOf(false) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        val remainingTime: StateFlow<Long> =
            service.flatMapLatest { it?.remainingTime?.asFlow() ?: flowOf(0L) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

        val currentStepIndex: StateFlow<Int> =
            service.flatMapLatest { it?.currentStepIndex?.asFlow() ?: flowOf(0) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

        val isTimerFinished: StateFlow<Boolean> =
            service.flatMapLatest { it?.isTimerFinished?.asFlow() ?: flowOf(false) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        val isRepeatEnabled: StateFlow<Boolean> =
            service.flatMapLatest { it?.isRepeatEnabled?.asFlow() ?: flowOf(false) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        val totalStepTime: Long
            get() = service.value?.totalStepTime ?: 0L

        private val _title = MutableStateFlow("")
        val title: StateFlow<String> = _title.asStateFlow()

        private val _steps = MutableStateFlow<List<Step>>(emptyList())
        val steps: StateFlow<List<Step>> = _steps.asStateFlow()

        private val _deleteResult = MutableSharedFlow<BaseResult<Unit>>(replay = 1)
        val deleteResult: SharedFlow<BaseResult<Unit>> = _deleteResult.asSharedFlow()

        private val _errorEvent = MutableSharedFlow<Unit>()
        val errorEvent: SharedFlow<Unit> = _errorEvent.asSharedFlow()

        init {
            timerServiceManager.bindService()

            viewModelScope.launch {
                timerServiceManager.service.combine(_steps) { service, steps ->
                    Pair(service, steps)
                }.collect { (newService, steps) ->
                    if (this@CustomTimerViewModel.service.value != newService) {
                        this@CustomTimerViewModel.service.value = newService
                    }
                    // (서비스 연결, 스텝 조회)이 모두 완료되면 Service로 데이터를 전달
                    if (newService != null && steps.isNotEmpty()) {
                        Timber.tag("타이머").d("Service and Steps are ready. Passing data to service.")
                        newService.loadTimerData(steps)
                    }
                }
            }
        }

        fun setTimerId(id: Long) {
            timerId = id
        }

        fun loadTimerData() =
            viewModelScope.launch {
                when (val result = getCustomTimerUseCase(timerId)) {
                    is BaseResult.Success -> {
                        _title.value = result.data.name
                        _steps.value = result.data.steps

                        // 서비스가 연결되었는지 확인
                        if (service.value == null) {
                            Timber.tag("타이머").d("loadTimerData: 데이터 로딩은 성공했지만, 아직 Service에 연결되지 않았습니다.")
                        }

                        service.value?.loadTimerData(result.data.steps)
                    }
                    is BaseResult.Error -> {
                        _errorEvent.emit(Unit)
                    }
                }
            }

        fun deleteTimer() =
            viewModelScope.launch {
                _deleteResult.emit(deleteCustomTimerUseCase(timerId))
            }

        fun startTimer() {
            if (service.value == null) {
                return
            }
            service.value?.startTimer()
        }

        fun pauseTimer() {
            service.value?.pauseTimer()
        }

        fun resetTimer(isUserAction: Boolean) {
            service.value?.resetTimer(isUserAction)
        }

        fun jumpToStep(position: Int) {
            service.value?.jumpToStep(position)
        }

        fun toggleRepeat() {
            service.value?.toggleRepeat()
        }

        override fun onCleared() {
            timerServiceManager.unbindService()
            super.onCleared()
        }
    }
