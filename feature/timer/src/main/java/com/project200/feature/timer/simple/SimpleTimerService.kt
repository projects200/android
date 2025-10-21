package com.project200.feature.timer.simple

import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.project200.domain.manager.TimerManager
import com.project200.undabang.feature.timer.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SimpleTimerService : LifecycleService() {
    @Inject
    lateinit var timerManager: TimerManager

    private val binder = TimerBinder()
    private var mediaPlayer: MediaPlayer? = null

    var totalTime: Long = 0L
        private set

    private val _remainingTime = MutableLiveData(0L)
    val remainingTime: LiveData<Long> = _remainingTime

    private val _isTimerRunning = MutableLiveData(false)
    val isTimerRunning: LiveData<Boolean> = _isTimerRunning

    inner class TimerBinder : Binder() {
        fun getService(): SimpleTimerService = this@SimpleTimerService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer.create(this, R.raw.simple_alarm)
        setupTimerManager()
    }

    private fun setupTimerManager() {
        timerManager.setOnTickListener { millisUntilFinished ->
            _remainingTime.postValue(millisUntilFinished)
        }
        timerManager.setOnFinishListener {
            _isTimerRunning.postValue(false)
            _remainingTime.postValue(0L)
            mediaPlayer?.start()
            stopForeground(false)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    fun setAndStartTimer(timeInSeconds: Int) {
        timerManager.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }

        totalTime = timeInSeconds * 1000L
        _remainingTime.postValue(totalTime)

        if (totalTime <= 0L) return

        _isTimerRunning.postValue(true)
        timerManager.start(totalTime)
    }

    fun setTimer(timeInSeconds: Int) {
        timerManager.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }
        totalTime = timeInSeconds * 1000L
        _remainingTime.postValue(totalTime)
        _isTimerRunning.postValue(false)
    }

    fun startTimer() {
        if (_isTimerRunning.value == true || (_remainingTime.value ?: 0L) <= 0L) return

        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }

        _isTimerRunning.postValue(true)
        timerManager.start(_remainingTime.value ?: 0L)
    }

    fun pauseTimer() {
        timerManager.pause()
        _isTimerRunning.postValue(false)
        stopForeground(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerManager.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
