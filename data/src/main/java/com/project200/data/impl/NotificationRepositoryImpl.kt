package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.mapper.toModel
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
import com.project200.domain.repository.FcmRepository
import com.project200.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val fcmRepository: FcmRepository
): NotificationRepository {
    override suspend fun getNotiState(): BaseResult<NotificationState> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = {
                val fcmTokenResult = fcmRepository.getFcmTokenFromPrefs() ?: throw NoSuchElementException("FCM token is missing.")
                apiService.getNotiState(fcmTokenResult) },
            mapper = { dto ->
                dto?.toModel() ?: throw NoSuchElementException("Notification state data is missing.")
            },
        )
    }
}