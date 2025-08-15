package com.project200.feature.timer.simple

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SimpleTimerViewModel @Inject constructor(

): ViewModel() {
    var totalTime: Long = 0

    private val _remainingTime = MutableLiveData<Long>()
    val remainingTime: LiveData<Long> = _remainingTime

    private val _isTimerRunning = MutableLiveData<Boolean>()
    val isTimerRunning: LiveData<Boolean>  = _isTimerRunning

    private var countDownTimer: CountDownTimer? = null

    init {
        _remainingTime.value = 0L
        _isTimerRunning.value = false
    }

    // 심플 타이머 아이템 클릭 시 타이머를 설정
    fun setTimer(timeInSeconds: Int) {
        // 기존 타이머가 있으면 취소
        countDownTimer?.cancel()

        // 새로운 타이머 시간으로 상태를 업데이트합니다.
        totalTime = timeInSeconds * 1000L
        _remainingTime.value = totalTime
        _isTimerRunning.value = false
    }

    // 타이머 시
    fun startTimer() {
        if (_isTimerRunning.value == true || _remainingTime.value == 0L) {
            return
        }

        val remainingTimeMillis = _remainingTime.value ?: 0L
        countDownTimer = object : CountDownTimer(remainingTimeMillis, COUNTDOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingTime.value = millisUntilFinished
            }

            override fun onFinish() {
                _isTimerRunning.value = false
                _remainingTime.value = 0L
            }
        }.start()

        _isTimerRunning.value = true
    }

    // 타이머 일시정지
    fun pauseTimer() {
        countDownTimer?.cancel()
        _isTimerRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
    }

    companion object {
        const val COUNTDOWN_INTERVAL = 50L // 50ms
    }
}