package com.project200.feature.timer.custom

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.usecase.DeleteCustomTimerUseCase
import com.project200.domain.usecase.GetCustomTimerUseCase
import com.project200.feature.timer.utils.CustomTimerServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
        private val service = MutableLiveData<CustomTimerService?>()

        // service가 연결되면, service 내부 LiveData를 관찰
        val isTimerRunning: LiveData<Boolean> = service.switchMap { it?.isTimerRunning ?: MutableLiveData(false) }
        val remainingTime: LiveData<Long> = service.switchMap { it?.remainingTime ?: MutableLiveData(0L) }
        val currentStepIndex: LiveData<Int> = service.switchMap { it?.currentStepIndex ?: MutableLiveData(0) }
        val isTimerFinished: LiveData<Boolean> = service.switchMap { it?.isTimerFinished ?: MutableLiveData(false) }
        val isRepeatEnabled: LiveData<Boolean> = service.switchMap { it?.isRepeatEnabled ?: MutableLiveData(false) }

        val totalStepTime: Long
            get() = service.value?.totalStepTime ?: 0L

        private val _title = MutableLiveData<String>()
        val title: LiveData<String> = _title

        private val _steps = MutableLiveData<List<Step>>()
        val steps: LiveData<List<Step>> = _steps

        private val _deleteResult = MutableLiveData<BaseResult<Unit>>()
        val deleteResult: LiveData<BaseResult<Unit>> = _deleteResult

        private val _errorEvent = MutableSharedFlow<Unit>()
        val errorEvent = _errorEvent.asSharedFlow()

        init {
            timerServiceManager.bindService()

            viewModelScope.launch {
                timerServiceManager.service.combine(_steps.asFlow()) { service, steps ->
                    Pair(service, steps)
                }.collect { (service, steps) ->
                    if (this@CustomTimerViewModel.service.value != service) {
                        this@CustomTimerViewModel.service.postValue(service)
                    }
                    // (서비스 연결, 스텝 조회)이 모두 완료되면 Service로 데이터를 전달
                    if (service != null && steps.isNotEmpty()) {
                        Timber.tag("타이머").d("Service and Steps are ready. Passing data to service.")
                        service.loadTimerData(steps)
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
                _deleteResult.value = deleteCustomTimerUseCase(timerId)
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
            super.onCleared()
            timerServiceManager.unbindService()
        }
    }
