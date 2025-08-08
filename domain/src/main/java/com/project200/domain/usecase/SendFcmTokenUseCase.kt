package com.project200.domain.usecase

import com.project200.domain.repository.FcmRepository
import javax.inject.Inject

class SendFcmTokenUseCase @Inject constructor(
    private val fcmRepository: FcmRepository
) {
    suspend operator fun invoke() = fcmRepository.sendFcmToken()
}
