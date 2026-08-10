package com.project200.undabang.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.NetworkMonitor
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val checkForUpdateUseCase: CheckForUpdateUseCase,
        private val checkIsRegisteredUseCase: CheckIsRegisteredUseCase,
        private val loginUseCase: LoginUseCase,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _updateCheckResult = MutableLiveData<UpdateCheckResult>()
        val updateCheckResult: LiveData<UpdateCheckResult> = _updateCheckResult

        private val _loginResult = MutableLiveData<BaseResult<Unit>>()
        val loginResult: LiveData<BaseResult<Unit>> = _loginResult

        private val _showBottomNavigation = MutableLiveData<Boolean>()
        val showBottomNavigation: LiveData<Boolean> = _showBottomNavigation

        private val _forceUpdateAfterReconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val forceUpdateAfterReconnect: SharedFlow<Unit> = _forceUpdateAfterReconnect.asSharedFlow()

        private var isContentShown = false
        private var wasOffline = false

        init {
            observeNetworkReconnection()
        }

        private fun observeNetworkReconnection() {
            viewModelScope.launch {
                networkMonitor.networkState.collect { isOnline ->
                    if (isOnline && wasOffline && isContentShown) {
                        recheckForUpdateOnReconnect()
                    }
                    wasOffline = !isOnline
                }
            }
        }

        private suspend fun recheckForUpdateOnReconnect() {
            // onAvailable 직후 일시적 불안정 대비 딜레이
            delay(RECONNECT_UPDATE_CHECK_DELAY_MS)
            checkForUpdateUseCase()
                .onSuccess { result ->
                    if (result is UpdateCheckResult.UpdateAvailable && result.isForceUpdate) {
                        _forceUpdateAfterReconnect.tryEmit(Unit)
                    }
                }
                .onFailure { e ->
                    Timber.w(e, "재연결 후 업데이트 확인 실패 - 무시")
                }
        }

        fun onContentShown() {
            isContentShown = true
        }

        // 업데이트 확인
        fun checkForUpdate() {
            if (_updateCheckResult.value != null) {
                return
            } // 이미 체크했다면 스킵

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
                        Timber.e(error, "ViewModel: 업데이트 확인 실패 - NoUpdateNeeded로 진행")
                        _updateCheckResult.value = UpdateCheckResult.NoUpdateNeeded
                    }
            }
        }

        // 로그인
        fun login() {
            viewModelScope.launch {
                val result = loginUseCase()
                checkIsRegisteredUseCase()
                _loginResult.value = result
            }
        }

        fun showBottomNavigation() {
            _showBottomNavigation.value = true
        }

        fun hideBottomNavigation() {
            _showBottomNavigation.value = false
        }

        companion object {
            private const val RECONNECT_UPDATE_CHECK_DELAY_MS = 1500L
        }
    }
