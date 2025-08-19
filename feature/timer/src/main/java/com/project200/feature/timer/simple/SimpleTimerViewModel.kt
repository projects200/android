package com.project200.feature.timer.simple

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.usecase.EditSimpleTimerUseCase
import com.project200.domain.usecase.GetSimpleTimersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class TimerEvent {
    object NavigateToErrorScreen : TimerEvent()
    data class ShowToast(val code: String) : TimerEvent()
}

@HiltViewModel
class SimpleTimerViewModel @Inject constructor(
    private val getSimpleTimersUseCase: GetSimpleTimersUseCase,
    private val editSimpleTimerUseCase: EditSimpleTimerUseCase
): ViewModel() {
    var totalTime: Long = 0

    // 타이머 아이템 리스트
    private val _timerItems = MutableLiveData<MutableList<SimpleTimer>>()
    val timerItems: LiveData<MutableList<SimpleTimer>> = _timerItems

    private val _remainingTime = MutableLiveData<Long>()
    val remainingTime: LiveData<Long> = _remainingTime

    private val _isTimerRunning = MutableLiveData<Boolean>()
    val isTimerRunning: LiveData<Boolean>  = _isTimerRunning

    // 이벤트를 전달할 SharedFlow 생성
    private val _eventFlow = MutableSharedFlow<TimerEvent>()
    val eventFlow: SharedFlow<TimerEvent> = _eventFlow.asSharedFlow()

    private var countDownTimer: CountDownTimer? = null

    init {
        _remainingTime.value = 0L
        _isTimerRunning.value = false
        loadTimerItems()
    }

    fun loadTimerItems() {
        viewModelScope.launch {
            when (val result = getSimpleTimersUseCase()) {
                is BaseResult.Success -> {
                    _timerItems.value = result.data.toMutableList()
                }
                is BaseResult.Error -> {
                    _eventFlow.emit(TimerEvent.NavigateToErrorScreen)
                }
            }
        }
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

    // 타이머 아이템을 수정하는 함수
    fun updateTimerItem(updatedTimer: SimpleTimer) {
        val currentItems = _timerItems.value ?: return
        val index = currentItems.indexOfFirst { it.id == updatedTimer.id }
        if (index != -1) {
            currentItems[index] = updatedTimer
            _timerItems.value = currentItems

            viewModelScope.launch {
                val result = editSimpleTimerUseCase(updatedTimer)
                if (result is BaseResult.Error) {
                    Timber.e("타이머 수정 실패: ${result.message}")
                    _eventFlow.emit(TimerEvent.ShowToast(result.errorCode.toString()))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
    }

    companion object {
        const val COUNTDOWN_INTERVAL = 50L // 50ms
    }
}