package com.project200.feature.timer.custom

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.Step
import com.project200.domain.manager.TimerManager
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
import com.project200.domain.usecase.DeleteCustomTimerUseCase
import com.project200.domain.usecase.GetCustomTimerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomTimerViewModel @Inject constructor(
    private val timerManager: TimerManager,
    private val getCustomTimerUseCase: GetCustomTimerUseCase,
    private val deleteCustomTimerUseCase: DeleteCustomTimerUseCase,
): ViewModel() {
    // 전체 타이머 시간 (밀리초 단위)
    var totalTime: Long = 0L
        private set

    // 현재 스텝의 전체 시간 (밀리초 단위)
    var totalStepTime: Long = 0L
        private set

    private var customTimerId: Long = DUMMY_TIMER_ID

    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

    private val _currentStepIndex = MutableLiveData<Int>()
    val currentStepIndex: LiveData<Int> = _currentStepIndex

    private val _isTimerFinished = MutableLiveData<Boolean>()
    val isTimerFinished: LiveData<Boolean> = _isTimerFinished

    // 스텝이 끝났을 때 알림
    private val _stepFinishedAlarm = MutableLiveData<Boolean>(false)
    val stepFinishedAlarm: LiveData<Boolean> = _stepFinishedAlarm

    // 3, 2, 1초 카운트다운 알림
    private val _playTickSound = MutableLiveData<Boolean>(false)
    val playTickSound: LiveData<Boolean> = _playTickSound
    private val _isRepeatEnabled = MutableLiveData<Boolean>(false)
    val isRepeatEnabled: LiveData<Boolean> = _isRepeatEnabled

    private var lastTickedSecond = -1

    // Step의 time은 '초' 단위
    private val _steps = MutableLiveData<List<Step>>()
    val steps: LiveData<List<Step>> = _steps

    private val _remainingTime = MutableLiveData<Long>()
    val remainingTime: LiveData<Long> = _remainingTime

    private val _isTimerRunning = MutableLiveData<Boolean>()
    val isTimerRunning: LiveData<Boolean>  = _isTimerRunning

    private val _errorEvent = MutableSharedFlow<Boolean>()
    val errorEvent: SharedFlow<Boolean> = _errorEvent

    private val _deleteResult = MutableLiveData<BaseResult<Unit>>()
    val deleteResult: LiveData<BaseResult<Unit>> = _deleteResult

    init {
        setupTimerManager()
        resetTimer()
    }

    fun setTimerId(id: Long) {
        if(id == DUMMY_TIMER_ID) return
        this.customTimerId = id
    }

    fun loadTimerData() {
        viewModelScope.launch {
            when (val result = getCustomTimerUseCase(customTimerId)) {
                is BaseResult.Success -> {
                    val customTimer = result.data
                    _title.value = customTimer.name
                    _steps.value = customTimer.steps.sortedBy { it.order }
                    resetTimer()
                }
                is BaseResult.Error -> {
                    _errorEvent.emit(true)
                }
            }
        }
    }

    fun deleteTimer() {
        viewModelScope.launch {
            if (customTimerId == -1L) return@launch
            _deleteResult.value = deleteCustomTimerUseCase(customTimerId)
        }
    }

    // TimerManager의 콜백을 설정하는 초기화 함수
    private fun setupTimerManager() {
        timerManager.setOnTickListener { millisUntilFinished ->
            _remainingTime.value = millisUntilFinished

            // 3, 2, 1초 카운트다운 알림 재생
            val currentSecond = (millisUntilFinished / 1000).toInt() + 1
            if (currentSecond in 1..3 && currentSecond != lastTickedSecond) {
                _playTickSound.value = true
                lastTickedSecond = currentSecond
            }
        }
        timerManager.setOnFinishListener {
            _remainingTime.value = 0L
            moveToNextStep()
        }
    }


    // 타이머를 시작하거나, 일시정지 상태에서 재개합니다.
    fun startTimer() {
        if (_isTimerRunning.value == true || (_remainingTime.value ?: 0L) <= 0) {
            return
        }

        _isTimerRunning.value = true
        _isTimerFinished.value = false
        startCurrentStepTimer()
    }

    // 현재 스텝의 남은 시간을 기준으로 CountDownTimer를 생성하고 시작합니다.
    private fun startCurrentStepTimer() {
        val remainingTimeMillis = _remainingTime.value ?: 0L
        if (remainingTimeMillis <= 0) return

        timerManager.start(remainingTimeMillis)
    }

    // 현재 스텝을 종료하고 다음 스텝으로 전환하거나, 모든 스텝이 끝났을 경우 타이머를 종료합니다.
    private fun moveToNextStep() {
        // 알림 재생
        _stepFinishedAlarm.value = true

        val nextIndex = (_currentStepIndex.value ?: -1) + 1
        if (nextIndex < (_steps.value?.size ?: 0)) {
            // 상태 값들을 먼저 모두 갱신
            val nextStep = _steps.value!![nextIndex]
            totalStepTime = nextStep.time * 1000L
            _remainingTime.value = totalStepTime

            // 마지막에 LiveData를 변경하여 UI에 알림
            _currentStepIndex.value = nextIndex

            // isTimerRunning 상태에 따라 다음 타이머를 자동으로 시작할지 결정
            if (_isTimerRunning.value == true) {
                startCurrentStepTimer()
            }
        } else {
            // 모든 스텝 완료
            _isTimerRunning.value = false
            _isTimerFinished.value = true
        }
    }

    // 타이머를 일시정지합니다.
    fun pauseTimer() {
        timerManager.pause()
        _isTimerRunning.value = false
    }

    // 선택된 스텝으로 이동합니다.
    fun jumpToStep(index: Int) {
        // 유효하지 않은 인덱스에 대한 방어 코드
        if (index < 0 || index >= (_steps.value?.size ?: 0)) return

        // 현재 실행 중인 타이머가 있다면 정지
        timerManager.cancel()
        _isTimerRunning.value = false
        _isTimerFinished.value = false

        // 선택된 스텝의 정보로 상태를 갱신
        val targetStep = _steps.value!![index]
        totalStepTime = targetStep.time * 1000L
        _remainingTime.value = totalStepTime
        _currentStepIndex.value = index

        // 타이머를 자동으로 시작
        startTimer()
    }

    // 사용자가 '종료' 버튼을 누르거나, 타이머가 끝났을 때 모든 상태를 초기화합니다.
    fun resetTimer() {
        timerManager.cancel()
        _isTimerRunning.value = false

        // 전체 시간 및 첫 스텝 시간 계산
        totalTime = _steps.value?.sumOf { it.time * 1000L } ?: 0L
        val firstStep = _steps.value?.firstOrNull()
        totalStepTime = firstStep?.time?.times(1000L) ?: 0L
        _remainingTime.value = totalStepTime
        _currentStepIndex.value = 0
    }

    fun onStepFinishedAlarmPlayed() {
        _stepFinishedAlarm.value = false
    }

    fun onTickSoundPlayed() {
        _playTickSound.value = false
    }

    fun toggleRepeat() {
        _isRepeatEnabled.value = _isRepeatEnabled.value != true
    }

    fun restartTimer() {
        resetTimer() // 타이머 상태를 초기 상태로 되돌립니다.
        startTimer() // 타이머를 다시 시작합니다.
    }

    override fun onCleared() {
        super.onCleared()
        timerManager.cancel()
    }

    companion object {
        private const val DUMMY_TIMER_ID = -1L
    }
}