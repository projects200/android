package com.project200.undabang.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.NetworkMonitor
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val checkForUpdateUseCase: CheckForUpdateUseCase,
        private val loginUseCase: LoginUseCase,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
        val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult.asStateFlow()

        private val _showBottomNavigation = MutableStateFlow(false)
        val showBottomNavigation: StateFlow<Boolean> = _showBottomNavigation.asStateFlow()

        private val _forceUpdateAfterReconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val forceUpdateAfterReconnect: SharedFlow<Unit> = _forceUpdateAfterReconnect.asSharedFlow()

        private var isContentShown = false
        private var wasOffline = false
        private var serverLoginPending = false

        init {
            observeNetworkReconnection()
        }

        private fun observeNetworkReconnection() {
            viewModelScope.launch {
                networkMonitor.networkState.collect { isOnline ->
                    if (isOnline && wasOffline && isContentShown) {
                        if (serverLoginPending) {
                            loginInBackground()
                        }
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
            if (_updateCheckResult.value != null) return // 이미 체크했다면 스킵

            viewModelScope.launch {
                checkForUpdateUseCase()
                    .onSuccess { result ->
                        _updateCheckResult.value = result
                        when (result) {
                            is UpdateCheckResult.UpdateAvailable -> Timber.d("업데이트 가능 isForce: ${result.isForceUpdate}")
                            is UpdateCheckResult.NoUpdateNeeded -> Timber.d("업데이트 불필요")
                        }
                    }
                    .onFailure { error ->
                        Timber.e(error, "ViewModel: 업데이트 확인 실패 - NoUpdateNeeded로 진행")
                        _updateCheckResult.value = UpdateCheckResult.NoUpdateNeeded
                    }
            }
        }

        // POST /login — 진입 게이트와 무관하게 백그라운드에서 실행
        // 실패 시 serverLoginPending = true, 재연결 시 자동 재시도
        fun loginInBackground() {
            viewModelScope.launch {
                val result = loginUseCase()
                if (result is BaseResult.Success) {
                    serverLoginPending = false
                    Timber.d("서버 로그인 성공")
                } else {
                    serverLoginPending = true
                    Timber.w("서버 로그인 실패 - 재연결 시 재시도 예정")
                }
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
