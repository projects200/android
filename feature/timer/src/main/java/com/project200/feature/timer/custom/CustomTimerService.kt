package com.project200.feature.timer.custom

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
import com.project200.domain.model.Step
import com.project200.undabang.feature.timer.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.ceil

/**
 * 커스텀 타이머의 백그라운드 동작을 보장하기 위한 Foreground Service.
 * 이 서비스는 타이머의 상태(시간, 단계, 실행 여부)를 소유합니다.
 */
@AndroidEntryPoint
class CustomTimerService : LifecycleService() {
    @Inject
    lateinit var timerManager: TimerManager

    private val binder = TimerBinder()
    private lateinit var notificationManager: NotificationManager

    // 미디어 플레이어
    private var stepFinishPlayer: MediaPlayer? = null

    // 경고음 재생 상태
    private var isSoundPlayed = false

    // 타이머 데이터
    private var steps: List<Step> = emptyList()
    var totalStepTime: Long = 0L
        private set

    private var isForeground = false

    // 마지막으로 표시된 시간(초 단위)
    private var lastPostedSecond: Long = -1L

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
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
        setupTimerManager()
        observeTimerState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            // '일시정지'/'재생' 버튼 클릭 처리
            ACTION_TOGGLE_PAUSE_RESUME -> {
                if (_isTimerRunning.value == true) {
                    pauseTimer()
                } else {
                    startTimer()
                }
            }
            // '종료' 버튼 클릭 처리
            ACTION_STOP -> {
                stopTimerService(isUserAction = true)
            }
        }
        return START_STICKY
    }

    /**
     * TimerManager의 콜백을 설정합니다.
     * onTick: 타이머가 갱신될 때마다 호출됩니다.
     * onFinish: 현재 스텝의 타이머가 종료되었을 때 호출됩니다.
     */
    private fun setupTimerManager() {
        timerManager.setOnTickListener { millisUntilFinished ->
            _remainingTime.postValue(millisUntilFinished)
            // 3초 이하로 남았고, 아직 이번 스텝에서 경고음을 울리지 않았다면
            if (millisUntilFinished <= 3000L && !isSoundPlayed) {
                playSound()
                isSoundPlayed = true
            }

            // 알림은 1초에 한 번만 업데이트
            val seconds = millisUntilFinished.toSeconds()
            Timber.tag("타이머").d("Tick - Second: ${seconds}, Real: $millisUntilFinished last: $lastPostedSecond")
            if (seconds != lastPostedSecond) {
                lastPostedSecond = seconds
                if (seconds >= 0) {
                    updateNotification(seconds)
                }
            }
        }
        timerManager.setOnFinishListener {
            moveToNextStep()
        }
    }

    /**
     * 현재 스텝이 끝나면 다음 스텝으로 이동하거나, 반복하거나, 타이머를 종료합니다.
     */
    private fun moveToNextStep() {
        val nextStepIndex = _currentStepIndex.value!! + 1
        if (nextStepIndex < steps.size) {
            // 다음 단계로 이동
            _currentStepIndex.postValue(nextStepIndex)
            startTimerForCurrentStep()
            timerManager.start(totalStepTime)
        } else {
            // 모든 단계 완료
            if (_isRepeatEnabled.value == true) {
                // 반복이 활성화된 경우 처음부터 다시 시작
                _currentStepIndex.postValue(0)
                startTimerForCurrentStep()
                timerManager.start(totalStepTime)
            } else {
                // 타이머 최종 종료
                stopTimerService(isUserAction = false)
                _isTimerFinished.postValue(true)
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

    private fun observeTimerState() {
        isTimerRunning.observe(this) { updateNotification(_remainingTime.value?.toSeconds()) }
        currentStepIndex.observe(this) { updateNotification(_remainingTime.value?.toSeconds()) }
    }

    /**
     * 타이머를 시작하거나 일시정지 상태에서 재개합니다.
     */
    fun startTimer() {
        if (_isTimerRunning.value == true || steps.isEmpty()) return

        _isTimerRunning.postValue(true)

        if ((_remainingTime.value ?: 0L) < totalStepTime) { // 재개하는 경우
            resumeSound()
        } else { // 최초 시작하는 경우
            startTimerForCurrentStep()
        }

        timerManager.start(_remainingTime.value ?: 0L)

        if (!isForeground) {
            startForeground(NOTIFICATION_ID, buildNotification())
            isForeground = true
        }
    }

    /**
     * 새로운 스텝이 시작될 때마다 타이머 상태를 초기화합니다.
     */
    private fun startTimerForCurrentStep() {
        val currentStep = steps.getOrNull(_currentStepIndex.value ?: 0) ?: return
        totalStepTime = currentStep.time.toLong() * 1000
        isSoundPlayed = false
        _isTimerFinished.postValue(false)
        _remainingTime.postValue(totalStepTime)
        lastPostedSecond = -1L
    }

    fun pauseTimer() {
        if (_isTimerRunning.value == false) return
        timerManager.pause()
        _isTimerRunning.postValue(false)
        pauseSound()
    }

    /**
     * 타이머를 초기 상태로 리셋합니다.
     * @param isUserAction 사용자가 직접 버튼을 눌러 리셋했는지 여부. true일 경우 포그라운드 상태를 해제합니다.
     */
    fun resetTimer(isUserAction: Boolean) {
        timerManager.cancel()
        _isTimerRunning.postValue(false)
        _currentStepIndex.postValue(0)
        _isTimerFinished.postValue(false)
        totalStepTime = steps.firstOrNull()?.time?.toLong()?.times(1000) ?: 0L
        _remainingTime.postValue(totalStepTime)
        lastPostedSecond = -1L

        if (isUserAction) {
            stopSound()
            isForeground = false
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun stopTimerService(isUserAction: Boolean) {
        resetTimer(isUserAction = isUserAction)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            CustomTimerService::class.java.simpleName,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 알림 UI를 갱신합니다. 포그라운드 상태일 때만 호출됩니다.
     * @param seconds onTick에서 직접 전달받은 '초' 값
     */
    private fun updateNotification(seconds: Long? = null) {
        if (isForeground && steps.isNotEmpty()) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(seconds))
        }
    }

    private fun buildNotification(seconds: Long? = null): Notification {
        val currentStepIndexValue = _currentStepIndex.value ?: 0
        val currentStep = steps.getOrNull(currentStepIndexValue)
        val remainingSeconds = seconds ?: (_remainingTime.value ?: 0L).toSeconds()

        val toggleIntent = Intent(this, CustomTimerService::class.java).apply { action = ACTION_TOGGLE_PAUSE_RESUME }
        val stopIntent = Intent(this, CustomTimerService::class.java).apply { action = ACTION_STOP }

        // 앱의 기본 실행 인텐트
        val appOpenIntent: Intent? = packageManager.getLaunchIntentForPackage(packageName)

        val appOpenPendingIntent = if (appOpenIntent != null) {
            PendingIntent.getActivity(this, 0, appOpenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            // 런처 인텐트를 찾지 못하는 경우
            null
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(
                getString(
                    R.string.custom_timer_notify_title_format,
                    currentStepIndexValue + 1,
                    steps.size,
                    currentStep?.name ?: getString(R.string.custom_timer_default)
                )
            )
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

    // 올림된 초 단위 시간으로 변환
    private fun Long.toSeconds() = ceil(this / 1000.0).toLong()


    private fun playSound() {
        if (stepFinishPlayer == null) {
            stepFinishPlayer = MediaPlayer.create(this, R.raw.sound_custom_timer)
        }

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



    companion object {
        const val NOTIFICATION_CHANNEL_ID = "custom_timer_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_TOGGLE_PAUSE_RESUME = "ACTION_TOGGLE_PAUSE_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
    }
}
