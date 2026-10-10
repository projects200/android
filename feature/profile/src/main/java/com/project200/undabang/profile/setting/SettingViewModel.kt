package com.project200.undabang.profile.setting

import androidx.lifecycle.ViewModel
import com.project200.domain.model.SessionExitReason
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingViewModel
    @Inject
    constructor(
        private val logoutUseCase: LogoutUseCase,
        private val clearSessionUseCase: ClearSessionUseCase,
    ) : ViewModel() {
        suspend fun logout() = logoutUseCase()

        // 사용자가 로그아웃을 눌렀으므로 전송 대기 행까지 지웁니다
        suspend fun clearLocalSession() = clearSessionUseCase(SessionExitReason.USER_INITIATED)
    }
