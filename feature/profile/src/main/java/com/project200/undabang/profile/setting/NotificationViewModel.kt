package com.project200.undabang.profile.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
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
        private val _notificationStates = MutableStateFlow<List<NotificationState>>(emptyList())
        val notificationStates: StateFlow<List<NotificationState>> = _notificationStates

        private val _permissionRequestTrigger = MutableStateFlow(false)
        val permissionRequestTrigger: StateFlow<Boolean> = _permissionRequestTrigger

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage

        // 디바이스 권한 상태
        private var hasDevicePermission: Boolean = false

        // 대기 중인 알림 설정 타입
        private var pendingSettingType: NotificationType? = null

        private var isInitialized = false

        fun initNotiState(isGranted: Boolean) {
            this.hasDevicePermission = isGranted
            if (isGranted) {
                getNotificationState()
            } else {
                _notificationStates.value =
                    listOf(
                        NotificationState(NotificationType.WORKOUT_REMINDER, false),
                        NotificationState(NotificationType.CHAT_MESSAGE, false),
                    )
            }
            isInitialized = true
        }

        fun updateNotiStateByPermission(isGranted: Boolean) {
            if (!isInitialized) {
                initNotiState(isGranted)
                return
            }

            val wasGranted = this.hasDevicePermission
            this.hasDevicePermission = isGranted

            // 권한 거부
            if (!isGranted) {
                // 모든 알림 설정을 false로 조정
                Timber.tag("NotificationViewModel").e("권한 거부로 모든 알림 설정 비활성화")
                _notificationStates.value = _notificationStates.value.map { it.copy(enabled = false) }
            } else if (!wasGranted) { // 권한 거부에서 허용으로 변경된 경우
                Timber.tag("NotificationViewModel").e("권한 허용으로 변경")
                pendingSettingType?.let {
                    updateSetting(it, true)
                    pendingSettingType = null
                }
            }
        }

        private fun getNotificationState() {
            viewModelScope.launch {
                when (val result = getNotiStateUseCase()) {
                    is BaseResult.Success -> {
                        _notificationStates.value = result.data
                    }
                    is BaseResult.Error -> {
                        _toastMessage.value = result.message
                    }
                }
            }
        }

        fun onSwitchToggled(
            type: NotificationType,
            isEnabled: Boolean,
        ) {
            // 권한이 없는 경우
            if (isEnabled && !hasDevicePermission) {
                // 권한 허용 후에 적용할 설정을 저장
                pendingSettingType = type
                _permissionRequestTrigger.value = true
                return
            }
            updateSetting(type, isEnabled)
        }

        private fun updateSetting(
            type: NotificationType,
            enabled: Boolean,
        ) {
            Timber.tag("NotificationViewModel").e("알림 설정 변경 요청: $type, enabled: $enabled")
            viewModelScope.launch {
                // UI를 먼저 변경
                val originalStates = _notificationStates.value
                val newStates =
                    originalStates.map {
                        // 전달받은 type에 해당하는 아이템의 enabled 값만 변경
                        if (it.type == type) it.copy(enabled = enabled) else it
                    }
                _notificationStates.value = newStates

                when (val result = updateNotiSettingUseCase(newStates)) {
                    is BaseResult.Success -> { /* 성공 시 별도 처리 없음 */ }
                    is BaseResult.Error -> {
                        Timber.tag("NotificationViewModel").e("알림 설정 변경 실패: $type, enabled: $enabled")
                        // 실패 시 스위치 원상복구
                        _notificationStates.value = originalStates
                        _toastMessage.value = result.message
                    }
                }
            }
        }

        fun onPermissionRequestHandled() {
            _permissionRequestTrigger.value = false
        }
    }
