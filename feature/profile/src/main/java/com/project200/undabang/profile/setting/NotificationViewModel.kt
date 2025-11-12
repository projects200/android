package com.project200.undabang.profile.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationType
import com.project200.domain.usecase.GetNotificationStateUseCase
import com.project200.domain.usecase.UpdateNotificationStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel
    @Inject
    constructor(
        private val getNotiStateUseCase: GetNotificationStateUseCase,
        private val updateNotiSettingUseCase: UpdateNotificationStateUseCase,
    ) : ViewModel() {
        private val _isExerciseOn = MutableStateFlow(false)
        val isExerciseOn: StateFlow<Boolean> = _isExerciseOn

        private val _isChatOn = MutableStateFlow(false)
        val isChatOn: StateFlow<Boolean> = _isChatOn

        private val _permissionRequestTrigger = MutableStateFlow(false)
        val permissionRequestTrigger: StateFlow<Boolean> = _permissionRequestTrigger

        // 디바이스 권한 상태
        private var hasDevicePermission: Boolean = false

        // 대기 중인 알림 설정 타입
        private var pendingSettingType: NotificationType? = null

        private var isInitialized = false

        fun onPermissionStateChecked(isGranted: Boolean) {
            val wasGranted = this.hasDevicePermission
            this.hasDevicePermission = isGranted

            // 권한 허용으로 변경된 경우
            if (!wasGranted && isGranted) {
                pendingSettingType?.let {
                    updateSetting(it, true)
                    pendingSettingType = null
                }
            }

            // 한 번만 실행
            if (!isInitialized) {
                if (isGranted) {
                    getNotificationState()
                } else {
                    _isExerciseOn.value = false
                    _isChatOn.value = false
                }
                isInitialized = true
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

        fun onSwitchToggled(type: NotificationType, isEnabled: Boolean) {
            // 권한이 없는 경우
            if (isEnabled && !hasDevicePermission) {
                // 권한 허용 후에 적용할 설정을 저장
                pendingSettingType = type
                _permissionRequestTrigger.value = true

                // 스위치 원상복구
                _isExerciseOn.value = false
                _isChatOn.value = false

                return
            }
            updateSetting(type, isEnabled)
        }

        private fun updateSetting(type: NotificationType, enabled: Boolean) {
            viewModelScope.launch {
                // UI를 먼저 변경
                when(type) {
                    NotificationType.WORKOUT_REMINDER -> { _isExerciseOn.value = enabled }
                    NotificationType.CHAT_MESSAGE -> { _isChatOn.value = enabled }
                }

                when (updateNotiSettingUseCase(type, enabled)) {
                    is BaseResult.Success -> { /* 성공 시 별도 처리 없음 */ }
                    is BaseResult.Error -> {
                        Timber.tag("NotificationViewModel").e("알림 설정 변경 실패: ${type}, enabled: ${enabled}")
                        // 실패 시 스위치 원상복구
                        when(type) {
                            NotificationType.WORKOUT_REMINDER -> _isExerciseOn.value = !enabled
                            NotificationType.CHAT_MESSAGE -> _isChatOn.value = !enabled
                        }
                    }
                }
            }
        }

        fun onPermissionRequestHandled() {
            _permissionRequestTrigger.value = false
        }
    }
