package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.NotificationStateDTO
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.FcmRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MaestroProfileNotificationReadinessTest {
    private val api = mockk<ApiService>()
    private val fcm = mockk<FcmRepository>()
    private val readiness = MaestroProfileNotificationReadiness(api, fcm)
    private val token = "unit-test-device-identity"
    private val notifications =
        listOf(
            NotificationStateDTO("WORKOUT_REMINDER", true),
            NotificationStateDTO("CHAT_MESSAGE", false),
            NotificationStateDTO("NEW_SERVER_TYPE", true),
        )

    @Test
    fun `FCM callback 저장을 기다린 뒤 실제 등록 성공과 같은 기기의 원본 행을 요구한다`() =
        runTest {
            baseline()
            coEvery { fcm.getSavedToken() } returnsMany listOf(null, "", " ", token)
            val snapshot = requireNotNull(readiness.prepare("profile-notification"))
            assertThat(testScheduler.currentTime).isEqualTo(750L)
            assertThat(snapshot.token).isEqualTo(token)
            assertThat(snapshot.settings).containsExactlyElementsIn(notifications)
            coVerifySequence {
                fcm.getSavedToken()
                fcm.getSavedToken()
                fcm.getSavedToken()
                fcm.getSavedToken()
                fcm.registerToken()
                fcm.getSavedToken()
                api.getNotiState(token)
                fcm.getSavedToken()
            }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
        }

    @Test
    fun `다른 profile flow는 FCM 토큰과 알림 API를 요구하지 않는다`() =
        runTest {
            for (flow in listOf("profile-edit", "profile-edit-validation", "profile-preferred-exercise")) {
                assertThat(readiness.prepare(flow)).isNull()
            }
            coVerify(exactly = 0) { fcm.getSavedToken() }
            coVerify(exactly = 0) { fcm.registerToken() }
            coVerify(exactly = 0) { api.getNotiState(any()) }
        }

    @Test
    fun `저장 토큰이 없으면 제한 시간에 실패하고 등록이나 기본 행을 만들지 않는다`() =
        runTest {
            coEvery { fcm.getSavedToken() } returns null
            expectFailure("profile_fcm_token_timeout") { readiness.prepare("profile-notification") }
            assertThat(testScheduler.currentTime).isEqualTo(60_000L)
            coVerify(exactly = 0) { fcm.registerToken() }
            coVerify(exactly = 0) { api.getNotiState(any()) }
        }

    @Test
    fun `마지막 polling 구간에 저장된 토큰도 제한 시간 전에 등록할 수 있다`() =
        runTest {
            baseline()
            coEvery { fcm.getSavedToken() } coAnswers {
                if (testScheduler.currentTime >= 59_750L) token else null
            }
            assertThat(readiness.prepare("profile-notification")?.token).isEqualTo(token)
            assertThat(testScheduler.currentTime).isEqualTo(59_750L)
            coVerify(exactly = 1) { fcm.registerToken() }
            coVerify(exactly = 1) { api.getNotiState(token) }
        }

    @Test
    fun `토큰 저장소 오류를 누락 토큰으로 재시도하지 않는다`() =
        runTest {
            coEvery { fcm.getSavedToken() } throws IllegalStateException(token)
            expectFailure("profile_fcm_storage_failed") { readiness.prepare("profile-notification") }
            assertThat(testScheduler.currentTime).isEqualTo(0L)
            coVerify(exactly = 1) { fcm.getSavedToken() }
            coVerify(exactly = 0) { fcm.registerToken() }
        }

    @Test
    fun `백엔드 등록 Error는 재시도나 설정 조회 없이 고정 오류로 실패한다`() =
        runTest {
            baseline()
            coEvery { fcm.registerToken() } returns BaseResult.Error("PRIVATE", token, IllegalStateException(token))
            expectFailure("profile_fcm_registration_failed") { readiness.prepare("profile-notification") }
            coVerify(exactly = 1) { fcm.registerToken() }
            coVerify(exactly = 0) { api.getNotiState(any()) }
        }

    @Test
    fun `등록 예외에 포함된 원문은 결과 오류에 전달하지 않는다`() =
        runTest {
            baseline()
            coEvery { fcm.registerToken() } throws IllegalStateException(token)
            expectFailure("profile_fcm_registration_failed") { readiness.prepare("profile-notification") }
            coVerify(exactly = 1) { fcm.registerToken() }
            coVerify(exactly = 0) { api.getNotiState(any()) }
        }

    @Test
    fun `등록 응답이 끝나지 않으면 제한 시간에 실패한다`() =
        runTest {
            baseline()
            coEvery { fcm.registerToken() } coAnswers {
                delay(30_001L)
                BaseResult.Success(Unit)
            }
            expectFailure("profile_fcm_registration_timeout") { readiness.prepare("profile-notification") }
            assertThat(testScheduler.currentTime).isEqualTo(30_000L)
            coVerify(exactly = 1) { fcm.registerToken() }
            coVerify(exactly = 0) { api.getNotiState(any()) }
        }

    @Test
    fun `등록 중 토큰이 바뀌면 어떤 기기의 설정도 읽지 않는다`() =
        runTest {
            baseline()
            coEvery { fcm.getSavedToken() } returnsMany listOf(token, "rotated-device")
            expectFailure("profile_fcm_identity_changed") { readiness.prepare("profile-notification") }
            coVerify(exactly = 1) { fcm.registerToken() }
            coVerify(exactly = 0) { api.getNotiState(any()) }
        }

    @Test
    fun `설정 조회 중 토큰이 바뀌면 원본 snapshot을 반환하지 않는다`() =
        runTest {
            baseline()
            coEvery { fcm.getSavedToken() } returnsMany listOf(token, token, "rotated-device")
            expectFailure("profile_fcm_identity_changed") { readiness.prepare("profile-notification") }
            coVerify(exactly = 1) { api.getNotiState(token) }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
        }

    @Test
    fun `실패한 설정 응답에 행이 있어도 원본으로 인정하지 않는다`() =
        runTest {
            baseline()
            coEvery { api.getNotiState(token) } returns BaseResponse(false, "PRIVATE", token, notifications)
            expectFailure("profile_fixture_read_failed") { readiness.prepare("profile-notification") }
            coVerify(exactly = 1) { api.getNotiState(token) }
        }

    @Test
    fun `등록 성공이어도 필수 알림 행이 없으면 기본값으로 대체하지 않는다`() =
        runTest {
            baseline()
            coEvery { api.getNotiState(token) } returns BaseResponse(true, "OK", "", notifications.drop(1))
            expectFailure("profile_notifications_invalid") { readiness.prepare("profile-notification") }
            coVerify(exactly = 0) { api.patchNotiState(any(), any()) }
        }

    @Test
    fun `설정 조회가 끝나지 않으면 제한 시간에 실패한다`() =
        runTest {
            baseline()
            coEvery { api.getNotiState(token) } coAnswers {
                delay(30_001L)
                BaseResponse(true, "OK", "", notifications)
            }
            expectFailure("profile_fcm_settings_timeout") { readiness.prepare("profile-notification") }
            assertThat(testScheduler.currentTime).isEqualTo(30_000L)
            coVerify(exactly = 1) { api.getNotiState(token) }
        }

    @Test
    fun `상위 작업 취소는 readiness 실패로 삼키지 않는다`() =
        runTest {
            baseline()
            coEvery { fcm.registerToken() } throws CancellationException("cancelled")
            try {
                readiness.prepare("profile-notification")
                throw AssertionError("취소된 등록이 성공으로 처리되었습니다")
            } catch (_: CancellationException) {
                coVerify(exactly = 0) { api.getNotiState(any()) }
            }
        }

    private fun baseline() {
        coEvery { fcm.getSavedToken() } returns token
        coEvery { fcm.registerToken() } returns BaseResult.Success(Unit)
        coEvery { api.getNotiState(token) } returns BaseResponse(true, "OK", "", notifications)
    }

    private suspend fun expectFailure(
        reason: String,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
            throw AssertionError("준비 실패가 성공으로 처리되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
            assertThat(failure.cause).isNull()
        }
    }
}
