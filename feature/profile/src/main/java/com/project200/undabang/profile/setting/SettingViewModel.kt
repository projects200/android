package com.project200.undabang.profile.setting

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SessionExitReason
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingViewModel
    @Inject
    constructor(
        private val logoutUseCase: LogoutUseCase,
        private val clearSessionUseCase: ClearSessionUseCase,
    ) : ViewModel() {
        private var logoutStarted = false
        private val _logoutBlocking = MutableStateFlow(false)
        /** 완료를 화면이 처리할 때까지 Settings의 모든 이탈을 차단합니다. */
        val logoutBlocking = _logoutBlocking.asStateFlow()
        private val _logoutCompleted = MutableLiveData<Boolean?>()
        /** 값은 원격/로컬 정리 오류 여부입니다. 오류가 있어도 세션 종료 뒤 Login으로 이동합니다. */
        val logoutCompleted: LiveData<Boolean?> = _logoutCompleted

        fun confirmLogout(beforeLocalClear: suspend () -> Unit) {
            if (logoutStarted) return
            logoutStarted = true
            _logoutBlocking.value = true
            viewModelScope.launch {
                var failed = false
                try {
                    try {
                        failed = logoutUseCase() is BaseResult.Error
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failed = true
                    }
                    beforeLocalClear()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed = true
                } finally {
                    withContext(NonCancellable) {
                        try {
                            clearSessionUseCase(SessionExitReason.USER_INITIATED)
                        } catch (_: Exception) {
                            failed = true
                        }
                        _logoutCompleted.value = failed
                    }
                }
            }
        }

        fun consumeLogoutCompletion() {
            if (_logoutCompleted.value == null) return
            _logoutCompleted.value = null
            _logoutBlocking.value = false
        }
    }
