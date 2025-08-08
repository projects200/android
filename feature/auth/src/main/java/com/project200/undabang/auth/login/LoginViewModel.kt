package com.project200.undabang.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun checkIsRegistered() {
        viewModelScope.launch {
            _isRegistered.value = checkIsRegisteredUseCase() ?: false
        }
    }

    fun sendFcmToken() {
        viewModelScope.launch {
            sendFcmTokenUseCase()
        }
    }
}