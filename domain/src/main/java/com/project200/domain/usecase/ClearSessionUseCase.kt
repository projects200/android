package com.project200.domain.usecase

import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.repository.AuthRepository
import javax.inject.Inject

class ClearSessionUseCase
@Inject
constructor(
    private val authRepository: AuthRepository,
    private val fcmTokenSyncScheduler: FcmTokenSyncScheduler,
) {
    suspend operator fun invoke() {
        authRepository.clearSession()
        // 회원ID가 지워져 실행돼도 SKIPPED로 끝나지만 예약을 남겨둘 이유가 없다
        fcmTokenSyncScheduler.cancel()
    }
}
