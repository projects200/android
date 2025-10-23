package com.project200.feature.timer.simple

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.usecase.AddSimpleTimerUseCase
import com.project200.domain.usecase.DeleteSimpleTimerUseCase
import com.project200.domain.usecase.EditSimpleTimerUseCase
import com.project200.domain.usecase.GetSimpleTimersUseCase
import com.project200.feature.timer.utils.SimpleTimerServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SimpleTimerViewModel
    @Inject
    constructor(
        private val simpleTimerServiceManager: SimpleTimerServiceManager,
        private val getSimpleTimersUseCase: GetSimpleTimersUseCase,
        private val addSimpleTimerUseCase: AddSimpleTimerUseCase,
        private val editSimpleTimerUseCase: EditSimpleTimerUseCase,
        private val deleteSimpleTimerUseCase: DeleteSimpleTimerUseCase,
    ) : ViewModel() {
        private val service = MutableLiveData<SimpleTimerService?>()

        val remainingTime: LiveData<Long> = service.switchMap { it?.remainingTime ?: MutableLiveData(0L) }
        val isTimerRunning: LiveData<Boolean> = service.switchMap { it?.isTimerRunning ?: MutableLiveData(false) }
        val totalTime: Long
            get() = service.value?.totalTime ?: 0L

        // 타이머 아이템 리스트
        private val _timerItems = MutableLiveData<List<SimpleTimer>>()
        val timerItems: LiveData<List<SimpleTimer>> = _timerItems

        private var isAscending = true

        init {
            simpleTimerServiceManager.bindService()
            viewModelScope.launch {
                simpleTimerServiceManager.service.collect { serviceInstance ->
                    service.postValue(serviceInstance)
                }
            }
            loadTimerItems()
        }

        // 이벤트를 전달할 SharedFlow 생성
        private val _toastMessage = MutableSharedFlow<SimpleTimerToastMessage>()
        val toastMessage: SharedFlow<SimpleTimerToastMessage> = _toastMessage

        fun setAndStartTimer(timeInSeconds: Int) {
            service.value?.setAndStartTimer(timeInSeconds)
        }

        fun loadTimerItems() {
            viewModelScope.launch {
                when (val result = getSimpleTimersUseCase()) {
                    is BaseResult.Success -> {
                        _timerItems.value = result.data.toMutableList()
                    }
                    is BaseResult.Error -> {
                        _toastMessage.emit(SimpleTimerToastMessage.GET_ERROR)
                    }
                }
            }
        }

        fun changeSortOrder() {
            isAscending = !isAscending
            _timerItems.value?.let { currentList ->
                _timerItems.value = sortTimers(currentList, isAscending)
            }
        }

        private fun sortTimers(
            timers: List<SimpleTimer>,
            ascending: Boolean,
        ): List<SimpleTimer> {
            return if (ascending) {
                timers.sortedBy { it.time }
            } else {
                timers.sortedByDescending { it.time }
            }
        }

        fun addTimerItem(time: Int) {
            val currentItems = _timerItems.value ?: emptyList()
            if (currentItems.size >= MAX_TIMER_COUNT) return

            viewModelScope.launch {
                when (val result = addSimpleTimerUseCase(time)) {
                    is BaseResult.Success -> {
                        val newTimer = SimpleTimer(id = result.data, time = time)
                        _timerItems.value = currentItems + newTimer
                    }
                    is BaseResult.Error -> _toastMessage.emit(SimpleTimerToastMessage.ADD_ERROR)
                }
            }
        }

        fun deleteTimerItem(timerId: Long) {
            viewModelScope.launch {
                when (deleteSimpleTimerUseCase(timerId)) {
                    is BaseResult.Success -> {
                        val currentItems = _timerItems.value ?: return@launch
                        _timerItems.value = currentItems.filterNot { it.id == timerId }
                    }
                    is BaseResult.Error -> _toastMessage.emit(SimpleTimerToastMessage.DELETE_ERROR)
                }
            }
        }

        // 타이머 시작
        fun startTimer() {
            service.value?.startTimer()
        }

        // 타이머 일시정지
        fun pauseTimer() {
            service.value?.pauseTimer()
        }

        // 타이머 아이템을 수정하는 함수
        fun updateTimerItem(updatedTimer: SimpleTimer) {
            val currentItems = _timerItems.value?.toMutableList() ?: return
            val index = currentItems.indexOfFirst { it.id == updatedTimer.id }

            if (index != -1) {
                currentItems[index] = updatedTimer
                _timerItems.value = currentItems // 새 리스트 할당

                viewModelScope.launch {
                    val result = editSimpleTimerUseCase(updatedTimer)
                    if (result is BaseResult.Error) {
                        _toastMessage.emit(SimpleTimerToastMessage.EDIT_ERROR)
                    }
                }
            }
        }

        override fun onCleared() {
            simpleTimerServiceManager.unbindService()
            super.onCleared()
        }

        companion object {
            const val MAX_TIMER_COUNT = 6
            const val DEFAULT_ADD_TIME_SEC = 60
        }
    }
