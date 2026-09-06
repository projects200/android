package com.project200.domain.usecase

import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.repository.AuthRepository
import javax.inject.Inject

class ClearSessionUseCase
@Inject
constructor(
    private val authRepository: AuthRepository,
    private val fcmTokenSyncScheduler: FcmTokenSyncScheduler,
    private val sessionDataCleaner: SessionDataCleaner,
) {
    /**
     * 로컬 세션을 정리합니다.
     *
     * 캐시를 회원ID보다 먼저 지웁니다. 계정 스코프 삭제로 좁히면 회원ID가 없는 시점에는
     * 지울 대상을 특정할 수 없습니다
     * 캐시 삭제가 실패해도 세션은 끊습니다. 세션이 남으면 다음 진입에서 자동 로그인됩니다
     */
    suspend operator fun invoke() {
        try {
            sessionDataCleaner.clearAll()
        } finally {
            fcmTokenSyncScheduler.cancel()
            authRepository.clearSession()
        }
    }
}
