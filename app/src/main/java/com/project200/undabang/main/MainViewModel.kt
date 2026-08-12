package com.project200.undabang.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.NetworkMonitor
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.LoginUseCase
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val checkForUpdateUseCase: CheckForUpdateUseCase,
        private val loginUseCase: LoginUseCase,
        private val networkMonitor: NetworkMonitor,
        private val authManager: AuthManager,
        private val authStateManager: AuthStateManager,
    ) : ViewModel() {
        private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
        val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult.asStateFlow()

        private val _showBottomNavigation = MutableStateFlow(false)
        val showBottomNavigation: StateFlow<Boolean> = _showBottomNavigation.asStateFlow()

        private val _forceUpdateAfterReconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val forceUpdateAfterReconnect: SharedFlow<Unit> = _forceUpdateAfterReconnect.asSharedFlow()

        private var wasOffline = !networkMonitor.isCurrentlyConnected()

        private val _entryState = MutableStateFlow<EntryState>(EntryState.Loading)
        val entryState: StateFlow<EntryState> = _entryState.asStateFlow()

        // 선택(비강제) 업데이트 다이얼로그용
        private val _optionalUpdate = MutableStateFlow(false)
        val optionalUpdate: StateFlow<Boolean> = _optionalUpdate.asStateFlow()

        private var serverLoginPending = false

        init {
            observeNetworkReconnection()
        }

        /** onCreate에서 1회. 회전 재생성 시 StateFlow 값이 살아 있어 자동 멱등 */
        fun startEntry() {
            if (_entryState.value != EntryState.Loading) return
            viewModelScope.launch {
                val update = checkForUpdateUseCase().getOrElse {
                    // 업데이트 확인 실패. 오프라인이라고 간주하고 진행
                    UpdateCheckResult.NoUpdateNeeded
                }
                if (update is UpdateCheckResult.UpdateAvailable) {
                    if (update.isForceUpdate) {
                        _entryState.value = EntryState.ForceUpdate(fromReconnect = false)
                        return@launch
                    }
                    _optionalUpdate.value = true // 선택 업데이트는 라우팅과 병행 (기존 동작 유지)
                }
                routeByAuth()
            }
        }
        private suspend fun routeByAuth() {
            val state = authStateManager.getCurrent()
            if (!state.isAuthorized) {
                _entryState.value = EntryState.Login
                return
            }
            if (state.needsTokenRefresh) {
                when (val r = authManager.refreshAccessToken()) {
                    is TokenRefreshResult.Success -> Unit
                    is TokenRefreshResult.Error -> {
                        val ex = r.exception
                        val invalidGrant = ex?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR &&
                                ex.error == "invalid_grant"
                        if (invalidGrant) {
                            _entryState.value = EntryState.Login
                            return
                        }
                        Timber.w("Token refresh 일시 실패 - 오프라인 진입")
                    }
                    is TokenRefreshResult.NoRefreshToken,
                    is TokenRefreshResult.ConfigError,
                        -> {
                        _entryState.value = EntryState.Login
                        return
                    }
                }
            }
            ensureFcmRegistration()
            _entryState.value = EntryState.Content
        }

        /** 선택 업데이트 다이얼로그 표시 완료 */
        fun onOptionalUpdateShown() {
            _optionalUpdate.value = false
        }

        private fun observeNetworkReconnection() {
            viewModelScope.launch {
                networkMonitor.networkState.collect { isOnline ->
                    if (isOnline && wasOffline && _entryState.value is EntryState.Content) {
                        recheckForUpdateOnReconnect()
                    }
                    wasOffline = !isOnline
                }
            }
        }

        private suspend fun recheckForUpdateOnReconnect() {
            delay(RECONNECT_UPDATE_CHECK_DELAY_MS.milliseconds)
            checkForUpdateUseCase()
                .onSuccess { result ->
                    if (result is UpdateCheckResult.UpdateAvailable && result.isForceUpdate) {
                        _entryState.value = EntryState.ForceUpdate(fromReconnect = true)
                    }
                }
                .onFailure { Timber.w(it, "재연결 후 업데이트 확인 실패 - 무시") }
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

        private fun ensureFcmRegistration() {
            viewModelScope.launch {
                val result = loginUseCase()
                serverLoginPending = result !is BaseResult.Success  // 실패 시 재연결 때 재시도
            }
        }


        companion object {
            private const val RECONNECT_UPDATE_CHECK_DELAY_MS = 1500L
        }
    }
