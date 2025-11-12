package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.NotificationStateDTO
import com.project200.data.mapper.toDTO
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
import com.project200.domain.model.NotificationType
import com.project200.domain.repository.FcmRepository
import com.project200.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val fcmRepository: FcmRepository
): NotificationRepository {
    override suspend fun getNotiState(): BaseResult<List<NotificationState>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = {
                val fcmTokenResult = fcmRepository.getFcmTokenFromPrefs() ?: throw NoSuchElementException("FCM token is missing.")
                apiService.getNotiState(fcmTokenResult) },
            mapper = { dtoList ->
                dtoList?.map { it.toModel() } ?: throw NoSuchElementException("Notification state data is missing.")
            },
        )
    }

    override suspend fun updateNotiState(notiState: List<NotificationState>): BaseResult<Unit> {
/*        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = {
                val fcmTokenResult = fcmRepository.getFcmTokenFromPrefs() ?: throw NoSuchElementException("FCM token is missing.")
                apiService.patchNotiState(
                    fcmToken = fcmTokenResult,
                    notiRequest = notiState.map { it.toDTO() }
                )
            },
            mapper = { Unit },
        )*/
        return BaseResult.Success(Unit) /* 성공으로 더미 반환 */
    }
}