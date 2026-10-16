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
import javax.inject.Inject

enum class NotificationSyncState {
    Loading,
    Ready,
    Saving,
    Error,
}

@HiltViewModel
class NotificationViewModel
    @Inject
    constructor(
        private val getNotiStateUseCase: GetNotificationStateUseCase,
        private val updateNotiSettingUseCase: UpdateNotificationStateUseCase,
    ) : ViewModel() {
        private val _notificationStates = MutableStateFlow<List<NotificationState>>(emptyList())
        val notificationStates: StateFlow<List<NotificationState>> = _notificationStates

        private val _syncState = MutableStateFlow(NotificationSyncState.Loading)
        val syncState: StateFlow<NotificationSyncState> = _syncState

        private val _permissionRequestTrigger = MutableStateFlow(false)
        val permissionRequestTrigger: StateFlow<Boolean> = _permissionRequestTrigger

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage

        private var serverStates: List<NotificationState> = emptyList()
        private val _hasDevicePermission = MutableStateFlow(false)
        val hasDevicePermission: StateFlow<Boolean> = _hasDevicePermission
        private var pendingSettingType: NotificationType? = null
        private var busy = false

        fun initNotiState(isGranted: Boolean) {
            updateNotiStateByPermission(isGranted)
        }

        fun updateNotiStateByPermission(isGranted: Boolean) {
            _hasDevicePermission.value = isGranted
            publishStates()
            if (busy) return
            busy = true
            _syncState.value = NotificationSyncState.Loading
            viewModelScope.launch {
                try {
                    if (readStates()) {
                        val pending = pendingSettingType
                        if (_hasDevicePermission.value && pending != null) {
                            pendingSettingType = null
                            persist(pending, true)
                        } else {
                            _syncState.value = NotificationSyncState.Ready
                        }
                    }
                } finally {
                    busy = false
                }
            }
        }

        fun onSwitchToggled(
            type: NotificationType,
            isEnabled: Boolean,
        ) {
            if (busy || _syncState.value != NotificationSyncState.Ready || type == NotificationType.UNKNOWN) return
            if (!_hasDevicePermission.value) {
                if (isEnabled) {
                    pendingSettingType = type
                    _permissionRequestTrigger.value = true
                }
                return
            }
            busy = true
            _syncState.value = NotificationSyncState.Saving
            viewModelScope.launch {
                try {
                    persist(type, isEnabled)
                } finally {
                    busy = false
                }
            }
        }

        private suspend fun persist(
            type: NotificationType,
            enabled: Boolean,
        ) {
            _syncState.value = NotificationSyncState.Saving
            // 전체 목록의 UNKNOWN 재전송은 원래 서버 type을 잃습니다. 변경한 행만 PATCH합니다.
            when (val result = updateNotiSettingUseCase(listOf(NotificationState(type, enabled)))) {
                is BaseResult.Success -> {
                    if (readStates(NotificationState(type, enabled))) _syncState.value = NotificationSyncState.Ready
                }
                is BaseResult.Error -> fail(result.message)
            }
        }

        private suspend fun readStates(expected: NotificationState? = null): Boolean {
            return when (val result = getNotiStateUseCase()) {
                is BaseResult.Success -> {
                    val states = result.data
                    if (states.count { it.type == NotificationType.WORKOUT_REMINDER } != 1 ||
                        states.count { it.type == NotificationType.CHAT_MESSAGE } != 1 ||
                        (expected != null && states.none { it.type == expected.type && it.enabled == expected.enabled })
                    ) {
                        fail("알림 설정을 확인하지 못했습니다.")
                        false
                    } else {
                        serverStates = states
                        publishStates()
                        true
                    }
                }
                is BaseResult.Error -> {
                    fail(result.message)
                    false
                }
            }
        }

        private fun publishStates() {
            // OS 권한은 표시만 바꾸고, 확인된 서버 값은 별도로 보존합니다.
            _notificationStates.value = if (_hasDevicePermission.value) serverStates else serverStates.map { it.copy(enabled = false) }
        }

        private fun fail(message: String?) {
            _syncState.value = NotificationSyncState.Error
            _toastMessage.value = message ?: "알림 설정을 확인하지 못했습니다."
        }

        fun onPermissionRequestHandled() {
            _permissionRequestTrigger.value = false
        }
    }
