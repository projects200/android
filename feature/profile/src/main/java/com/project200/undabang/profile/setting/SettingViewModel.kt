package com.project200.undabang.profile.setting

import androidx.lifecycle.ViewModel
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
        suspend fun clearLocalSession() = clearSessionUseCase()
    }
