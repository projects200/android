package com.project200.feature.timer.custom

import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.project200.domain.manager.TimerManager
import com.project200.domain.model.Step
import com.project200.undabang.feature.timer.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 커스텀 타이머의 백그라운드 동작을 보장하기 위한 Foreground Service.
 * 이 서비스는 타이머의 상태(시간, 단계, 실행 여부)를 소유합니다.
 */
@AndroidEntryPoint
class CustomTimerService : LifecycleService() {

    @Inject
    lateinit var timerManager: TimerManager

    private val binder = TimerBinder()

    // 미디어 플레이어
    private var stepFinishPlayer: MediaPlayer? = null

    // 경고음 재생 상태
    private var isSoundPlayed = false

    // 타이머 데이터
    private var steps: List<Step> = emptyList()
    var totalStepTime: Long = 0L
        private set

    private val _isTimerRunning = MutableLiveData(false)
    val isTimerRunning: LiveData<Boolean> = _isTimerRunning

    private val _remainingTime = MutableLiveData(0L)
    val remainingTime: LiveData<Long> = _remainingTime

    private val _currentStepIndex = MutableLiveData(0)
    val currentStepIndex: LiveData<Int> = _currentStepIndex

    private val _isTimerFinished = MutableLiveData(false)
    val isTimerFinished: LiveData<Boolean> = _isTimerFinished

    private val _isRepeatEnabled = MutableLiveData(false)
    val isRepeatEnabled: LiveData<Boolean> = _isRepeatEnabled

    inner class TimerBinder : Binder() {
        fun getService(): CustomTimerService = this@CustomTimerService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        stepFinishPlayer = MediaPlayer.create(this, R.raw.sound_custom_timer)
        setupTimerManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY // 시스템에 의해 강제 종료 시, 서비스를 재시작
    }

    private fun setupTimerManager() {
        timerManager.setOnTickListener { millisUntilFinished ->
            _remainingTime.postValue(millisUntilFinished)

            // 3초 이하로 남았고, 아직 이번 스텝에서 경고음을 울리지 않았다면
            if (millisUntilFinished <= 3000L && !isSoundPlayed) {
                playSound()
                isSoundPlayed = true
            }
        }
        timerManager.setOnFinishListener {
            moveToNextStep()
        }
    }

    private fun moveToNextStep() {
        val nextStepIndex = _currentStepIndex.value!! + 1
        if (nextStepIndex < steps.size) {
            // 다음 단계로 이동
            _currentStepIndex.postValue(nextStepIndex)
            startTimerForCurrentStep()
        } else {
            // 모든 단계 완료
            if (_isRepeatEnabled.value == true) {
                // 반복이 활성화된 경우 처음부터 다시 시작
                _currentStepIndex.postValue(0)
                startTimerForCurrentStep()
            } else {
                // 타이머 최종 종료
                _isTimerFinished.postValue(true)
                _isTimerRunning.postValue(false)
                _remainingTime.postValue(0L)
                stopForeground(true) // 서비스 종료 및 알림 제거
            }
        }
    }

    fun loadTimerData(newSteps: List<Step>) {
        // 타이머가 실행 중이 아닐 때만 데이터를 로드하여 상태 꼬임 방지
        if (_isTimerRunning.value == false) {
            steps = newSteps
            resetTimer(isUserAction = false)
        }
    }

    fun startTimer() {
        if (_isTimerRunning.value == true || steps.isEmpty()) return

        // remainingTime이 totalStepTime보다 작다 == 일시정지 후 재개
        val isResuming = (_remainingTime.value ?: 0L) < totalStepTime

        if (isResuming) {
            _isTimerRunning.postValue(true)
            resumeSound()
            timerManager.start(totalStepTime)
        } else {
            startTimerForCurrentStep()
        }
    }

    private fun startTimerForCurrentStep() {
        val currentStep = steps.getOrNull(_currentStepIndex.value ?: 0) ?: return
        totalStepTime = currentStep.time.toLong() * 1000
        isSoundPlayed = false

        _isTimerRunning.postValue(true)
        _isTimerFinished.postValue(false)
        timerManager.start(totalStepTime)
    }

    fun pauseTimer() {
        if (_isTimerRunning.value == false) return
        timerManager.pause()
        _isTimerRunning.postValue(false)
        pauseSound()
    }

    fun resetTimer(isUserAction: Boolean) {
        timerManager.cancel()
        _isTimerRunning.postValue(false)
        _currentStepIndex.postValue(0)
        _isTimerFinished.postValue(false)
        totalStepTime = steps.firstOrNull()?.time?.toLong()?.times(1000) ?: 0L
        _remainingTime.postValue(totalStepTime)

        if(isUserAction) {
            stopSound()
        }
    }

    fun jumpToStep(position: Int) {
        stopSound()
        if (position < 0 || position >= steps.size) return
        timerManager.cancel()
        _currentStepIndex.postValue(position)
        // 점프 후 즉시 시작
        startTimerForCurrentStep()
    }

    fun toggleRepeat() {
        _isRepeatEnabled.postValue(!(_isRepeatEnabled.value ?: false))
    }

    private fun playSound() {
        stepFinishPlayer?.seekTo(0)
        stepFinishPlayer?.start()
    }

    private fun pauseSound() {
        if (stepFinishPlayer?.isPlaying == true) {
            stepFinishPlayer?.pause()
        }
    }

    private fun resumeSound() {
        // 재생 중이 아니고, 멈춘 위치가 있다면 -> 이어서 재생
        if (stepFinishPlayer?.isPlaying == false && (stepFinishPlayer?.currentPosition ?: 0) > 0) {
            stepFinishPlayer?.start()
        }
    }

    fun stopSound() {
        if (stepFinishPlayer?.isPlaying == true) {
            stepFinishPlayer?.pause()
        }
        stepFinishPlayer?.seekTo(0)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stepFinishPlayer?.release()
        stepFinishPlayer = null

        timerManager.cancel()
    }
}