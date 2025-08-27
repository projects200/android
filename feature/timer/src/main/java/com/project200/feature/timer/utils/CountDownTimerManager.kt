package com.project200.feature.timer.utils

import android.os.CountDownTimer
import com.project200.domain.manager.TimerManager
import javax.inject.Inject

/** * CountDownTimerManager는 CountDownTimer를 관리하는 클래스입니다.
 * 타이머의 시작, 일시정지, 취소 기능을 제공하며,
 * 타이머가 진행 중일 때 남은 시간을 저장하여 일시정지 후 재개할 수 있습니다.
 */
class CountDownTimerManager @Inject constructor() : TimerManager {

    private var countDownTimer: CountDownTimer? = null
    private var onTickListener: ((Long) -> Unit)? = null
    private var onFinishListener: (() -> Unit)? = null

    // 일시정지 후 재개를 위해 남은 시간을 저장
    private var remainingTime: Long = 0L

    override fun setOnTickListener(listener: (millisUntilFinished: Long) -> Unit) {
        this.onTickListener = listener
    }

    override fun setOnFinishListener(listener: () -> Unit) {
        this.onFinishListener = listener
    }

    override fun start(durationInMillis: Long) {
        // 이전에 멈춘 시간이 있다면 그 시간부터, 없다면 전달받은 전체 시간부터 시작
        val startTime = if (remainingTime > 0) remainingTime else durationInMillis
        if (startTime <= 0) return

        countDownTimer = object : CountDownTimer(startTime, COUNTDOWN_INTERVAL) { // 50L은 ViewModel의 COUNTDOWN_INTERVAL
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = millisUntilFinished
                onTickListener?.invoke(millisUntilFinished)
            }

            override fun onFinish() {
                remainingTime = 0L
                onFinishListener?.invoke()
            }
        }.start()
    }

    override fun pause() {
        countDownTimer?.cancel()
    }

    override fun cancel() {
        countDownTimer?.cancel()
        remainingTime = 0L
    }

    companion object {
        const val COUNTDOWN_INTERVAL = 50L // 50ms
    }
}