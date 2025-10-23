package com.project200.feature.timer.simple

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media.app.NotificationCompat.MediaStyle
import com.project200.domain.manager.TimerManager
import com.project200.undabang.feature.timer.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToLong

@AndroidEntryPoint
class SimpleTimerService : LifecycleService() {
    @Inject
    lateinit var timerManager: TimerManager

    private val binder = TimerBinder()
    private lateinit var notificationManager: NotificationManager
    private var mediaPlayer: MediaPlayer? = null

    private var isForeground = false

    // 마지막으로 표시된 시간(초 단위)
    private var lastPostedSecond: Long = -1L

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
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        setupTimerManager()
        observeTimerState()
    }

    private fun observeTimerState() {
        // 타이머 실행 상태가 변경되면 알림의 아이콘(재생/정지)을 업데이트
        isTimerRunning.observe(this) { updateNotification() }
    }

    private fun setupTimerManager() {
        timerManager.setOnTickListener { millisUntilFinished ->
            _remainingTime.postValue(millisUntilFinished)
            // 1초에 한 번만 알림 업데이트
            val seconds = millisUntilFinished.toSeconds()
            if (seconds != lastPostedSecond) {
                lastPostedSecond = seconds
                if (seconds >= 0) {
                    updateNotification(seconds)
                }
            }
        }
        timerManager.setOnFinishListener {
            _isTimerRunning.postValue(false)
            _remainingTime.postValue(0L)
            playSound()
            stopForeground(Service.STOP_FOREGROUND_DETACH)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE_RESUME -> if (_isTimerRunning.value == true) pauseTimer() else startTimer()
            ACTION_STOP -> stopTimerService()
        }
        return START_STICKY
    }

    fun setAndStartTimer(timeInSeconds: Int) {
        setTimer(timeInSeconds)
        if (totalTime > 0L) {
            startTimer()
        }
    }

    fun setTimer(timeInSeconds: Int) {
        timerManager.cancel()
        stopSound()
        totalTime = timeInSeconds * 1000L
        _remainingTime.postValue(totalTime)
        _isTimerRunning.postValue(false)
        lastPostedSecond = -1L
    }

    fun startTimer() {
        if (_isTimerRunning.value == true || (_remainingTime.value ?: 0L) <= 0L) return
        stopSound()
        _isTimerRunning.postValue(true)

        timerManager.start(_remainingTime.value ?: 0L)

        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            isForeground = true
        }
    }

    fun pauseTimer() {
        if (_isTimerRunning.value == false) return
        timerManager.pause()
        _isTimerRunning.postValue(false)
        updateNotification()
    }

    private fun stopTimerService() {
        timerManager.cancel()
        stopSound()
        _isTimerRunning.postValue(false)
        _remainingTime.postValue(0L)
        lastPostedSecond = -1L
        isForeground = false
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }


    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.simple_timer),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(seconds: Long? = null) {
        if (isForeground) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(seconds))
        }
    }

    private fun buildNotification(seconds: Long? = null): Notification {
        val remainingSeconds = seconds ?: (_remainingTime.value ?: 0L).toSeconds()

        val toggleIntent = Intent(this, SimpleTimerService::class.java).apply { action = ACTION_TOGGLE_PAUSE_RESUME }
        val stopIntent = Intent(this, SimpleTimerService::class.java).apply { action = ACTION_STOP }

        val appOpenIntent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
        val appOpenPendingIntent = if (appOpenIntent != null) {
            PendingIntent.getActivity(this, 0, appOpenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            null
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.simple_timer))
            .setContentText(
                getString(
                    R.string.timer_notify_time_format,
                    remainingSeconds / 60,
                    remainingSeconds % 60
                )
            )
            .setSmallIcon(R.drawable.ic_clock)
            .setContentIntent(appOpenPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                if (_isTimerRunning.value == true) R.drawable.ic_stop else R.drawable.ic_play,
                getString(if (_isTimerRunning.value == true) R.string.timer_stop else R.string.timer_start),
                PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(
                com.project200.undabang.presentation.R.drawable.ic_close,
                getString(R.string.timer_end),
                PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun playSound() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.simple_alarm)
        }
        mediaPlayer?.start()
    }

    private fun stopSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    // 올림된 초 단위 시간으로 변환
    private fun Long.toSeconds() = ceil(this / 1000.0).toLong()

    override fun onDestroy() {
        super.onDestroy()
        timerManager.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "simple_timer_channel"
        const val NOTIFICATION_ID = 102
        const val ACTION_TOGGLE_PAUSE_RESUME = "ACTION_TOGGLE_PAUSE_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }
}