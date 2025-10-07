package com.project200.undabang.profile.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel
    @Inject
    constructor() : ViewModel() {
        private val _isNotiActive = MutableStateFlow(false) // 초기값은 false로 설정
        val isNotiActive: StateFlow<Boolean> = _isNotiActive

        private val _isSwitchEnabled = MutableStateFlow(true)
        val isSwitchEnabled: StateFlow<Boolean> = _isSwitchEnabled

        fun setNotificationState(isActive: Boolean) {
            _isNotiActive.value = isActive
        }

        fun onSwitchToggled() {
            viewModelScope.launch {
                if (_isSwitchEnabled.value) {
                    _isSwitchEnabled.value = false
                    // UI가 즉시 바뀌도록 상태 변경
                    _isNotiActive.value = !_isNotiActive.value
                    delay(1000L) // 중복 클릭 방지를 위한 딜레이
                    _isSwitchEnabled.value = true
                }
            }
        }
    }
