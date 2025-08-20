package com.project200.feature.timer.custom

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.project200.domain.model.Step
import com.project200.domain.manager.TimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CustomTimerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val timerManager: TimerManager
): ViewModel() {
    // 전체 타이머 시간 (밀리초 단위)
    var totalTime: Long = 0L
        private set

    // 현재 스텝의 전체 시간 (밀리초 단위)
    var totalStepTime: Long = 0L
        private set

    private val customTimerId: Long = savedStateHandle.get<Long>("customTimerId")
        ?: throw IllegalStateException("customTimerId is required for CustomTimerViewModel")

    private val _currentStepIndex = MutableLiveData<Int>()
    val currentStepIndex: LiveData<Int> = _currentStepIndex

    private val _isTimerFinished = MutableLiveData<Boolean>()
    val isTimerFinished: LiveData<Boolean> = _isTimerFinished

    private val _alarm = MutableLiveData<Boolean>(false)
    val alarm: LiveData<Boolean> = _alarm

    // Step의 time은 '초' 단위
    private val _steps = MutableLiveData<List<Step>>(listOf(
        Step(1, 1, 3 ,"준비 운동"),
        Step(2, 2, 5, "고강도 운동"),
        Step(3, 3, 3, "휴식"),
        Step(4, 4, 5, "마무리 운동"),
        Step(5, 5, 2, "마무리 운동1"),
        Step(6, 6, 2, "마무리 운동2"),
        Step(7, 7, 3, "마무리 운동3"),
        Step(8, 8, 4, "마무리 운동4"),
    ))
    val steps: LiveData<List<Step>> = _steps

    private val _remainingTime = MutableLiveData<Long>()
    val remainingTime: LiveData<Long> = _remainingTime

    private val _isTimerRunning = MutableLiveData<Boolean>()
    val isTimerRunning: LiveData<Boolean>  = _isTimerRunning

    init {
        setupTimerManager()
        resetTimer()
    }

    // TimerManager의 콜백을 설정하는 초기화 함수
    private fun setupTimerManager() {
        timerManager.setOnTickListener { millisUntilFinished ->
            _remainingTime.value = millisUntilFinished
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
        _alarm.value = true

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
            resetTimer() // 모든 스텝이 끝나면 초기 상태로 리셋
        }
    }

    // 타이머를 일시정지합니다.
    fun pauseTimer() {
        timerManager.pause()
        _isTimerRunning.value = false
    }

    // 사용자가 '종료' 버튼을 누르거나, 타이머가 끝났을 때 모든 상태를 초기화합니다.
    fun resetTimer() {
        timerManager.cancel()
        _isTimerRunning.value = false
        _isTimerFinished.value = true

        // 전체 시간 및 첫 스텝 시간 계산
        totalTime = _steps.value?.sumOf { it.time * 1000L } ?: 0L
        val firstStep = _steps.value?.firstOrNull()
        totalStepTime = firstStep?.time?.times(1000L) ?: 0L
        _remainingTime.value = totalStepTime
        _currentStepIndex.value = 0
    }

    fun onAlarmPlayed() {
        _alarm.value = false
    }

    override fun onCleared() {
        super.onCleared()
        timerManager.cancel()
    }
}