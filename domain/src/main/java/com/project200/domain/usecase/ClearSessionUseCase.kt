package com.project200.domain.usecase

import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.SessionExitReason
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
     * 사용자가 의도한 이탈에서만 캐시를 지웁니다. 강제 이탈은 캐시와 회원ID를 남깁니다.
     * 토큰 만료로 로그인 화면에 밀려난 사용자가 오프라인에서 만든 데이터를 잃지 않게 합니다
     *
     * 캐시를 회원ID보다 먼저 지웁니다. 계정 스코프 삭제로 좁히면 회원ID가 없는 시점에는
     * 지울 대상을 특정할 수 없습니다
     * 캐시 삭제가 실패해도 세션은 끊습니다. 세션이 남으면 다음 진입에서 자동 로그인됩니다
     *
     * 예약 작업은 이유와 무관하게 취소합니다. 세션이 없으면 전송할 수 없습니다.
     * 강제 이탈에서 남긴 대기 행은 재로그인 때 다시 예약해 올립니다
     */
    suspend operator fun invoke(reason: SessionExitReason) {
        try {
            if (reason == SessionExitReason.USER_INITIATED) {
                sessionDataCleaner.clearAll()
            }
        } finally {
            fcmTokenSyncScheduler.cancel()
            when (reason) {
                SessionExitReason.USER_INITIATED -> authRepository.clearSession()
                SessionExitReason.FORCED -> authRepository.clearTokens()
            }
        }
    }
}
