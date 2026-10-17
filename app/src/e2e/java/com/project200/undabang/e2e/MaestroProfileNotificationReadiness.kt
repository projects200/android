package com.project200.undabang.e2e

import com.project200.data.api.ApiService
import com.project200.data.dto.NotificationStateDTO
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FcmRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

// data class의 자동 toString에 실제 기기 토큰이 포함되지 않도록 일반 클래스로 보관합니다.
internal class MaestroProfileNotificationSnapshot(
    val token: String,
    val settings: List<NotificationStateDTO>,
)

internal class MaestroProfileNotificationReadiness
    @Inject
    constructor(
        private val api: ApiService,
        private val fcmRepository: FcmRepository,
    ) {
        suspend fun prepare(flow: String): MaestroProfileNotificationSnapshot? {
            if (flow != "profile-notification") return null
            // Firebase 자동 초기화 → FcmService.onNewToken → 암호화 저장소의 실제 경로만 기다립니다.
            val token =
                withTimeoutOrNull(TOKEN_TIMEOUT_MS) {
                    var saved = savedToken()
                    while (saved.isNullOrBlank()) {
                        delay(TOKEN_POLL_MS)
                        saved = savedToken()
                    }
                    saved
                } ?: throw MaestroSetupFailure("profile_fcm_token_timeout")
            // SetupActivity에는 MainViewModel의 예약이 없으므로 인증된 저장 토큰을 직접 등록합니다.
            val registration =
                try {
                    withTimeoutOrNull(REGISTRATION_TIMEOUT_MS) { fcmRepository.registerToken() }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    throw MaestroSetupFailure("profile_fcm_registration_failed")
                } ?: throw MaestroSetupFailure("profile_fcm_registration_timeout")
            if (registration !is BaseResult.Success) throw MaestroSetupFailure("profile_fcm_registration_failed")
            requireIdentity(token)
            val response =
                try {
                    withTimeoutOrNull(SETTINGS_TIMEOUT_MS) { api.getNotiState(token) }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    throw MaestroSetupFailure("profile_fixture_read_failed")
                } ?: throw MaestroSetupFailure("profile_fcm_settings_timeout")
            if (!response.succeed) throw MaestroSetupFailure("profile_fixture_read_failed")
            val settings = response.data ?: throw MaestroSetupFailure("profile_fixture_read_failed")
            MaestroProfilePolicy.validateNotifications(settings)
            requireIdentity(token)
            return MaestroProfileNotificationSnapshot(token, settings)
        }

        private suspend fun savedToken(): String? =
            try {
                fcmRepository.getSavedToken()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                throw MaestroSetupFailure("profile_fcm_storage_failed")
            }

        private suspend fun requireIdentity(token: String) {
            if (savedToken() != token) throw MaestroSetupFailure("profile_fcm_identity_changed")
        }

        private companion object {
            const val TOKEN_TIMEOUT_MS = 60_000L
            const val TOKEN_POLL_MS = 250L
            const val REGISTRATION_TIMEOUT_MS = 30_000L
            const val SETTINGS_TIMEOUT_MS = 30_000L
        }
    }
