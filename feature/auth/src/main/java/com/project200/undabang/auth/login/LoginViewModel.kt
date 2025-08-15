package com.project200.undabang.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.domain.usecase.SendFcmTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val checkIsRegisteredUseCase: CheckIsRegisteredUseCase,
    private val sendFcmTokenUseCase: SendFcmTokenUseCase
): ViewModel() {
    private val _isRegistered = MutableLiveData<Boolean>()
    val isRegistered: LiveData<Boolean> = _isRegistered

    private val _fcmTokenEvent = MutableLiveData<BaseResult<Unit>>()
    val fcmTokenEvent: LiveData<BaseResult<Unit>> = _fcmTokenEvent

    fun checkIsRegistered() {
        viewModelScope.launch {
            _isRegistered.value = checkIsRegisteredUseCase() ?: false
        }
    }

    // fcm 토큰 전송
    fun sendFcmToken() {
        viewModelScope.launch {
            val result = sendFcmTokenUseCase()
            _fcmTokenEvent.value = result
        }
    }
}