package com.project200.undabang.profile.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationType
import com.project200.domain.usecase.GetNotificationStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel
    @Inject
    constructor(
        private val getNotiStateUseCase: GetNotificationStateUseCase
    ) : ViewModel() {
        private val _isExerciseOn = MutableStateFlow(false)
        val isExerciseOn: StateFlow<Boolean> = _isExerciseOn

        private val _isChatOn = MutableStateFlow(false)
        val isChatOn: StateFlow<Boolean> = _isChatOn

        fun initNotificationState(hasPermission: Boolean) {
            if (hasPermission) {
                // 권한이 있으면 서버에서 상태를 가져옵니다.
                getNotificationState()
            } else {
                // 권한이 없으면 모든 스위치를 끕니다.
                _isExerciseOn.value = false
                _isChatOn.value = false
            }
        }

        private fun getNotificationState() {
            viewModelScope.launch {
                when (val result = getNotiStateUseCase()) {
                    is BaseResult.Success -> {
                        _isExerciseOn.value = result.data.exerciseEncouragement
                        _isChatOn.value = result.data.chatAlarm
                    }
                    is BaseResult.Error -> {
                        //TODO: 에러 처리
                    }
                }
            }
        }
    }
