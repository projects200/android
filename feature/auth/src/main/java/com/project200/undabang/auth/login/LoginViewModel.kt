package com.project200.undabang.auth.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.domain.usecase.LoginSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val checkIsRegisteredUseCase: CheckIsRegisteredUseCase,
        private val loginSessionUseCase: LoginSessionUseCase,
    ) : ViewModel() {
        private val _registrationResult = MutableLiveData<RegistrationStatus?>()
        val registrationResult: LiveData<RegistrationStatus?> = _registrationResult
        private var actionJob: Job? = null
        private var preparingLogin = false
        private var awaitingCredentials = false
        private var cancellingLogin = false
        private var generation = 0L
        private var outgoingAuthorizationState: String? = null
        private var authorizationLaunched = false

        val hasLoginToCancel: Boolean
            get() = preparingLogin || awaitingCredentials || cancellingLogin || actionJob?.isActive == true ||
                _registrationResult.value is RegistrationStatus.ConfirmationRequired

        fun isLoginActive(loginGeneration: Long): Boolean =
            generation == loginGeneration && awaitingCredentials

        fun loginGeneration(): Long = generation

        fun bindAuthorizationRequest(loginGeneration: Long, authorizationState: String): Boolean {
            if (!isLoginActive(loginGeneration) || authorizationLaunched || authorizationState.isBlank()) return false
            outgoingAuthorizationState = authorizationState
            authorizationLaunched = true
            return true
        }

        fun consumeAuthorizationResponse(authorizationState: String?): Long? {
            if (preparingLogin || cancellingLogin || !awaitingCredentials ||
                authorizationState.isNullOrBlank() || outgoingAuthorizationState != authorizationState
            ) return null
            // 수신 응답은 발신 시도의 권한을 한 번만 소비하며 새 로그인 시도를 만들지 않습니다.
            outgoingAuthorizationState = null
            return generation
        }

        fun cancelAuthorizationInitiation(loginGeneration: Long) {
            if (generation != loginGeneration || authorizationLaunched || (!preparingLogin && !awaitingCredentials)) return
            cancelLogin()
        }

        init {
            runAction { loginSessionUseCase.resumePendingLogin() }
        }

        suspend fun beginLogin(): Boolean {
            if (actionJob?.isActive == true || preparingLogin || awaitingCredentials || cancellingLogin) return false
            generation++
            val loginGeneration = generation
            preparingLogin = true
            outgoingAuthorizationState = null
            authorizationLaunched = false
            _registrationResult.value = null
            return try {
                loginSessionUseCase.beginLogin()
                if (generation != loginGeneration) return false
                awaitingCredentials = true
                true
            } catch (e: CancellationException) {
                cancelAuthorizationInitiation(loginGeneration)
                throw e
            } catch (_: Exception) {
                _registrationResult.value = RegistrationStatus.Indeterminate
                false
            } finally {
                preparingLogin = false
            }
        }

        fun checkIsRegistered() {
            awaitingCredentials = false
            runAction { checkIsRegisteredUseCase() }
        }

        fun confirmAccountAdoption(confirmationId: String) {
            val pending = _registrationResult.value as? RegistrationStatus.ConfirmationRequired ?: return
            if (pending.confirmationId != confirmationId) return
            runAction { loginSessionUseCase.confirmAccountAdoption(confirmationId) }
        }

        fun cancelLogin(confirmationId: String? = null) {
            if (cancellingLogin) return
            if (confirmationId != null &&
                (_registrationResult.value as? RegistrationStatus.ConfirmationRequired)?.confirmationId != confirmationId
            ) return
            generation++
            actionJob?.cancel()
            awaitingCredentials = false
            outgoingAuthorizationState = null
            authorizationLaunched = false
            _registrationResult.value = null
            cancellingLogin = true
            actionJob = viewModelScope.launch {
                try {
                    loginSessionUseCase.cancelLogin()
                    _registrationResult.value = RegistrationStatus.Cancelled
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _registrationResult.value = RegistrationStatus.Indeterminate
                } finally {
                    cancellingLogin = false
                }
            }
        }

        fun consumeResult() {
            _registrationResult.value = null
        }

        private fun runAction(action: suspend () -> RegistrationStatus?) {
            if (actionJob?.isActive == true || preparingLogin) return
            val currentGeneration = generation
            _registrationResult.value = null
            actionJob = viewModelScope.launch {
                val result = try {
                    action()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    RegistrationStatus.Indeterminate
                }
                if (generation == currentGeneration) _registrationResult.value = result
            }
        }
    }
