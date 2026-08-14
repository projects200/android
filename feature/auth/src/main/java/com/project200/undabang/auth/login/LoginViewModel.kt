package com.project200.undabang.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val checkIsRegisteredUseCase: CheckIsRegisteredUseCase,
    ) : ViewModel() {
        private val _registrationResult = MutableLiveData<RegistrationStatus>()
        val registrationResult: LiveData<RegistrationStatus> = _registrationResult

        fun checkIsRegistered() {
            viewModelScope.launch {
                _registrationResult.value = checkIsRegisteredUseCase()
            }
        }
    }
