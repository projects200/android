package com.project200.undabang.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.NetworkMonitor
import com.project200.domain.model.FcmTokenSyncResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.GetMemberIdUseCase
import com.project200.domain.usecase.SyncFcmTokenUseCase
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        private val syncFcmTokenUseCase: SyncFcmTokenUseCase,
        private val networkMonitor: NetworkMonitor,
        private val authManager: AuthManager,
        private val authStateManager: AuthStateManager,
        private val clearSessionUseCase: ClearSessionUseCase,
        private val getMemberIdUseCase: GetMemberIdUseCase,
    ) : ViewModel() {
        private val _entryState = MutableStateFlow<EntryState>(EntryState.Loading)
        val entryState: StateFlow<EntryState> = _entryState.asStateFlow()

        // 선택(비강제) 업데이트 다이얼로그용
        private val _optionalUpdate = MutableStateFlow(false)
        val optionalUpdate: StateFlow<Boolean> = _optionalUpdate.asStateFlow()

        private var wasOffline = !networkMonitor.isCurrentlyConnected()

        private var registrationJob: Job? = null
        private var registrationPending = false

        init {
            observeNetworkReconnection()
            observeForceLogout()
            startEntry()
        }

        /**
         * 진입 시 업데이트 확인 -> 토큰 확인
         * 이후 진입 상태를 업데이트하여 라우팅
         */
        private fun startEntry() {
            viewModelScope.launch {
                try {
                    val update =
                        checkForUpdateUseCase().getOrElse {
                            Timber.e(it, "업데이트 확인 실패 - NoUpdateNeeded로 진행")
                            UpdateCheckResult.NoUpdateNeeded
                        }
                    if (update is UpdateCheckResult.UpdateAvailable) {
                        if (update.isForceUpdate) {
                            _entryState.compareAndSet(EntryState.Loading, EntryState.ForceUpdate(fromReconnect = false))
                            return@launch
                        }
                        _optionalUpdate.value = true
                    }
                    routeByAuth()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "진입 플로우 실패 - 로그인 화면으로 폴백")
                    transitionToLogin()
                }
            }
        }

        private suspend fun routeByAuth() {
            val state = authStateManager.getCurrent()
            // 토큰 만료 시 refresh 시도, 실패 시 로그인 화면으로
            if (!state.isAuthorized) {
                transitionToLogin()
                return
            }
            if (getMemberIdUseCase().isNullOrBlank()) {
                transitionToLogin()
                return
            }

            if (state.needsTokenRefresh) {
                when (val result = authManager.refreshAccessToken()) {
                    is TokenRefreshResult.Success -> Unit
                    is TokenRefreshResult.Error -> {
                        val ex = result.exception
                        val invalidGrant =
                            ex?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR &&
                                ex.error == "invalid_grant"
                        if (invalidGrant) {
                            transitionToLogin()
                            return
                        }
                        Timber.w("Token refresh 일시 실패 - 오프라인 진입")
                    }

                    is TokenRefreshResult.NoRefreshToken,
                    is TokenRefreshResult.ConfigError,
                    -> {
                        transitionToLogin()
                        return
                    }
                }
            }
            ensureFcmRegistration()
            _entryState.compareAndSet(EntryState.Loading, EntryState.Content)
        }

        /** 사용 중 invalid_grant */
        private fun observeForceLogout() {
            viewModelScope.launch {
                authManager.forceLogoutFlow.collect {
                    Timber.w("강제 로그아웃 이벤트 - Login 전이")
                    transitionToLogin()
                }
            }
        }

        /** 선택 업데이트 다이얼로그 표시 완료 */
        fun onOptionalUpdateShown() {
            _optionalUpdate.value = false
        }

        /** 진입 시점 오프라인 여부 — 시작 탭 결정용 */
        fun isOfflineEntry(): Boolean = !networkMonitor.isCurrentlyConnected()

        private fun observeNetworkReconnection() {
            viewModelScope.launch {
                networkMonitor.networkState.collect { isOnline ->
                    if (isOnline && wasOffline && _entryState.value is EntryState.Content) {
                        if (registrationPending) {
                            ensureFcmRegistration() // 실패했던 FCM 등록 재시도
                        }
                        recheckForUpdateOnReconnect()
                    }
                    wasOffline = !isOnline
                }
            }
        }

        private suspend fun recheckForUpdateOnReconnect() {
            // onAvailable 직후 일시적 불안정 대비 딜레이
            delay(RECONNECT_UPDATE_CHECK_DELAY_MS.milliseconds)
            checkForUpdateUseCase()
                .onSuccess { result ->
                    if (result is UpdateCheckResult.UpdateAvailable && result.isForceUpdate) {
                        _entryState.compareAndSet(
                            EntryState.Content,
                            EntryState.ForceUpdate(fromReconnect = true),
                        )
                    }
                }
                .onFailure { Timber.w(it, "재연결 후 업데이트 확인 실패 - 무시") }
        }

        // FCM 토큰 등록
        private fun ensureFcmRegistration() {
            // 재시도 중 재연결이 겹치면 등록 요청이 중복 발행되는 것을 방지
            if (registrationJob?.isActive == true) return
            registrationPending = true
            registrationJob =
                viewModelScope.launch {
                    // SKIPPED는 보낼 토큰이 없는 상태 - 대기를 닫지 않고 재연결 때 재시도
                    if (syncFcmTokenUseCase() == FcmTokenSyncResult.SUCCESS) {
                        registrationPending = false
                        Timber.d("FCM 토큰 등록 성공")
                    } else {
                        Timber.w("FCM 토큰 등록 실패 - 재연결 시 재시도 예정")
                    }
                }
        }

        private suspend fun transitionToLogin() {
            runCatching { clearSessionUseCase() }
                .onFailure { Timber.e(it, "세션 정리 실패 - 전이는 계속") }
            _entryState.value = EntryState.Login
        }

        companion object {
            private const val RECONNECT_UPDATE_CHECK_DELAY_MS = 1500L
        }
    }
