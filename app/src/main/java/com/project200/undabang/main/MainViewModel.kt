package com.project200.undabang.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.domain.usecase.LoginUseCase
import com.project200.domain.usecase.SendFcmTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val checkIsRegisteredUseCase: CheckIsRegisteredUseCase,
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _updateCheckResult = MutableLiveData<UpdateCheckResult>()
    val updateCheckResult: LiveData<UpdateCheckResult> = _updateCheckResult

    private val _loginResult = MutableLiveData<BaseResult<Unit>>()
    val loginResult: LiveData<BaseResult<Unit>> = _loginResult

    private val _showBottomNavigation = MutableLiveData<Boolean>()
    val showBottomNavigation: LiveData<Boolean> = _showBottomNavigation

    // 업데이트 확인
    fun checkForUpdate() {
        if (_updateCheckResult.value != null) { return } // 이미 체크했다면 스킵

        viewModelScope.launch {
            checkForUpdateUseCase()
                .onSuccess { result ->
                    _updateCheckResult.value = result
                    when (result) {
                        is UpdateCheckResult.UpdateAvailable -> Timber.d("업데이트 가능 isForce: ${result.isForceUpdate}")
                        is UpdateCheckResult.NoUpdateNeeded -> Timber.d("업데이트 불가능")
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "ViewModel: 업데이트 확인 실패")
                }
        }
    }

    // 로그인
    fun login() {
        viewModelScope.launch {
            val result = loginUseCase()
            _loginResult.value = result
        }
    }

    fun showBottomNavigation() {
        _showBottomNavigation.value = true
    }

    fun hideBottomNavigation() {
        _showBottomNavigation.value = false
    }
}