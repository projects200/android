package com.project200.domain.usecase

import com.project200.domain.model.BaseResult
import com.project200.domain.model.FcmTokenSyncResult
import com.project200.domain.repository.AuthRepository
import com.project200.domain.repository.FcmRepository
import javax.inject.Inject

/**
 * 저장된 FCM 토큰을 서버에 반영합니다
 * 토큰은 호출 시점에 저장소에서 읽습니다. 예약과 실행 사이에 토큰이 다시 갱신될 수 있습니다
 */
class SyncFcmTokenUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val fcmRepository: FcmRepository
) {
    suspend operator fun invoke(): FcmTokenSyncResult {
        if (authRepository.getMemberId().isNullOrBlank()) {
            return FcmTokenSyncResult.SKIPPED
        }

        if (fcmRepository.getSavedToken().isNullOrBlank()) {
            return FcmTokenSyncResult.SKIPPED
        }

        return when (fcmRepository.registerToken()) {
            is BaseResult.Success -> FcmTokenSyncResult.SUCCESS
            is BaseResult.Error -> FcmTokenSyncResult.FAILURE
        }
    }
}
