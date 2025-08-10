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
    var totalTime: Int = 0
        private set

    private val _remainingTime = MutableLiveData<Int>()
    val remainingTime: LiveData<Int> get() = _remainingTime

    private val _isTimerRunning = MutableLiveData<Boolean>()
    val isTimerRunning: LiveData<Boolean> get() = _isTimerRunning

    private var countDownTimer: CountDownTimer? = null

    init {
        _remainingTime.value = 0
        _isTimerRunning.value = false
    }

    // 심플 타이머 아이템 클릭 시 타이머를 설정
    fun setTimer(time: Int) {
        // 기존 타이머가 있으면 취소
        countDownTimer?.cancel()

        // 새로운 타이머 시간으로 상태를 업데이트합니다.
        totalTime = time
        _remainingTime.value = time
        _isTimerRunning.value = false
    }

    // 타이머 시
    fun startTimer() {
        if (_isTimerRunning.value == true || _remainingTime.value == 0) {
            return
        }

        val remainingTimeMillis = (_remainingTime.value ?: 0) * 1000L
        countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingTime.value = (millisUntilFinished / 1000).toInt()
                Timber.tag("SimpleTimerViewModel").d("Remaining time: ${_remainingTime.value} seconds")
            }

            override fun onFinish() {
                _isTimerRunning.value = false
                _remainingTime.value = 0
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
}