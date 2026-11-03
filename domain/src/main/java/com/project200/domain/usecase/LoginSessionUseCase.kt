package com.project200.domain.usecase

import com.project200.domain.model.RegistrationStatus
import com.project200.domain.model.SessionExitReason
import com.project200.domain.repository.AuthRepository
import javax.inject.Inject

class LoginSessionUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val clearSessionUseCase: ClearSessionUseCase,
    ) {
        suspend fun beginLogin() {
            clearSessionUseCase(SessionExitReason.FORCED)
            authRepository.beginLogin()
        }

        suspend fun resumePendingLogin(): RegistrationStatus? = authRepository.resumePendingLogin()

        suspend fun confirmAccountAdoption(confirmationId: String): RegistrationStatus =
            authRepository.confirmAccountAdoption(confirmationId)

        suspend fun cancelLogin() = clearSessionUseCase(SessionExitReason.FORCED)
    }
