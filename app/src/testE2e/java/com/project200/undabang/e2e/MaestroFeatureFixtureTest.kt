package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class MaestroFeatureFixtureTest {
    private val protocol = mockk<MaestroSetupProtocol>(relaxed = true)
    private val session = mockk<MaestroSessionBootstrap>(relaxed = true)
    private val cleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
    private val fixture = mockk<MaestroFeatureFixture>(relaxed = true)
    private val offlineFixtures = mockk<MaestroOfflineFixtures>(relaxed = true)
    private val feature = MaestroFeatureRequest("profile", "profile-edit", "0123456789abcdef", "member", mockk())
    private val offlineFeature = MaestroFeatureRequest("offline", "offline-exercise-create", "0123456789abcdef", "member", mockk())

    private fun request(operation: String) =
        MaestroSetupRequest.FixtureOperation(operation, mockk(), "member", LocalDate.of(2026, 1, 1), feature)

    @Test
    fun `handler가 없으면 인증 뒤 서버 정리 전에 실패한다`() =
        runTest {
            every { protocol.consumeRequest() } returns request("prepare")
            MaestroSetupCoordinator(protocol, session, cleaner, emptyMap(), offlineFixtures).consumeRequest()
            coVerify(exactly = 1) { session.authenticate(any(), "member") }
            coVerify(exactly = 0) { cleaner.clean(any(), any()) }
            verify { protocol.writeFailure("feature_fixture_missing") }
        }

    @Test
    fun `offline prepare 실패는 회원ID와 로컬 데이터를 지키고 token만 지운다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.FixtureOperation(
                    "prepare",
                    mockk(),
                    "member",
                    LocalDate.of(2026, 1, 1),
                    offlineFeature,
                )
            coEvery { offlineFixtures.prepare(offlineFeature) } throws MaestroSetupFailure("offline_cache_flag_disabled")

            MaestroSetupCoordinator(
                protocol,
                session,
                cleaner,
                mapOf("offline" to offlineFixtures),
                offlineFixtures,
            ).consumeRequest()

            coVerify(exactly = 1) { session.authenticate(any(), "member") }
            coVerify(exactly = 1) { session.clearTokens() }
            coVerify(exactly = 0) { session.clearSession() }
            verify(exactly = 1) { protocol.markFeatureRecovery(offlineFeature) }
            verify { protocol.writeFailure("offline_cache_flag_disabled") }
        }

    @Test
    fun `준비는 일반 정리와 ownership 저장 뒤 선택한 handler만 실행한다`() =
        runTest {
            every { protocol.consumeRequest() } returns request("prepare")
            coEvery { fixture.prepare(feature) } returns mapOf("PROFILE_NAME" to "test-name")
            MaestroSetupCoordinator(protocol, session, cleaner, mapOf("profile" to fixture), offlineFixtures).consumeRequest()
            coVerifyOrder {
                session.authenticate(any(), "member")
                cleaner.clean(any(), any())
                protocol.markFeatureRecovery(feature)
                fixture.prepare(feature)
                protocol.writeStatus("ready", mapOf("PROFILE_NAME" to "test-name"))
            }
        }

    @Test
    fun `기능 복구 실패는 일반 정리나 snapshot 완료로 가리지 않는다`() =
        runTest {
            every { protocol.consumeRequest() } returns request("cleanup")
            coEvery { fixture.cleanup(feature) } throws MaestroSetupFailure("profile_restore_failed")
            MaestroSetupCoordinator(protocol, session, cleaner, mapOf("profile" to fixture), offlineFixtures).consumeRequest()
            coVerify(exactly = 0) { cleaner.clean(any(), any()) }
            verify(exactly = 0) { protocol.completeFeatureRecovery() }
            verify { protocol.writeFailure("profile_restore_failed") }
        }

    @Test
    fun `기능 정리 뒤 운동 정리가 실패하면 같은 ownership으로 다시 정리한다`() =
        runTest {
            every { protocol.consumeRequest() } returns request("cleanup")
            var attempts = 0
            coEvery { cleaner.clean(any(), any()) } coAnswers {
                if (attempts++ == 0) throw MaestroSetupFailure("fixture_read_failed")
            }
            val coordinator = MaestroSetupCoordinator(protocol, session, cleaner, mapOf("profile" to fixture), offlineFixtures)

            coordinator.consumeRequest()

            coVerify(exactly = 1) { fixture.cleanup(feature) }
            verify(exactly = 0) { protocol.completeFeatureRecovery() }
            verify(exactly = 0) { protocol.writeStatus("cleaned", any()) }
            verify { protocol.writeFailure("fixture_read_failed") }

            coordinator.consumeRequest()

            coVerify(exactly = 2) { fixture.cleanup(feature) }
            coVerify(exactly = 2) { session.authenticate(any(), "member") }
            verify(exactly = 2) { protocol.validateFeatureRecovery(feature) }
            verify(exactly = 1) { protocol.completeFeatureRecovery() }
            verify(exactly = 1) { protocol.writeStatus("cleaned", emptyMap()) }
            coVerifyOrder {
                fixture.cleanup(feature)
                cleaner.clean(any(), any())
                protocol.writeFailure("fixture_read_failed")
                session.authenticate(any(), "member")
                protocol.validateFeatureRecovery(feature)
                fixture.cleanup(feature)
                cleaner.clean(any(), any())
                session.clearSession()
                protocol.completeFeatureRecovery()
                protocol.writeStatus("cleaned", emptyMap())
            }
        }

    @Test
    fun `기능 정리 뒤 세션 제거가 실패하면 같은 ownership으로 다시 정리한다`() =
        runTest {
            every { protocol.consumeRequest() } returns request("cleanup")
            var attempts = 0
            coEvery { session.clearSession() } coAnswers {
                if (attempts++ == 0) throw MaestroSetupFailure("local_session_clear_failed")
            }
            val coordinator = MaestroSetupCoordinator(protocol, session, cleaner, mapOf("profile" to fixture), offlineFixtures)

            coordinator.consumeRequest()

            coVerify(exactly = 1) { fixture.cleanup(feature) }
            verify(exactly = 0) { protocol.completeFeatureRecovery() }
            verify(exactly = 0) { protocol.writeStatus("cleaned", any()) }
            verify { protocol.writeFailure("local_session_clear_failed") }

            coordinator.consumeRequest()

            coVerify(exactly = 2) { fixture.cleanup(feature) }
            coVerify(exactly = 2) { session.authenticate(any(), "member") }
            coVerify(exactly = 3) { session.clearSession() }
            verify(exactly = 2) { protocol.validateFeatureRecovery(feature) }
            verify(exactly = 1) { protocol.completeFeatureRecovery() }
            verify(exactly = 1) { protocol.writeStatus("cleaned", emptyMap()) }
            coVerifyOrder {
                fixture.cleanup(feature)
                cleaner.clean(any(), any())
                session.clearSession()
                session.clearSession()
                protocol.writeFailure("local_session_clear_failed")
                session.authenticate(any(), "member")
                protocol.validateFeatureRecovery(feature)
                fixture.cleanup(feature)
                cleaner.clean(any(), any())
                session.clearSession()
                protocol.completeFeatureRecovery()
                protocol.writeStatus("cleaned", emptyMap())
            }
        }

    @Test
    fun `잘못된 run ownership과 비밀 변수는 거절한다`() {
        for (runId in listOf("", "../private", "0123456789abcdef\n", "0123456789ABCDEF")) {
            expectFailure("invalid_feature_request") {
                MaestroFeaturePolicy.validateIdentity("profile", "profile-edit", runId)
            }
        }
        for (variables in listOf(mapOf("AUTH_STATE" to "value"), mapOf("SAFE" to "eyJabc.def.ghi"))) {
            expectFailure("invalid_feature_variables") { MaestroFeaturePolicy.validateVariables(variables) }
        }
    }

    private fun expectFailure(
        reason: String,
        action: () -> Unit,
    ) {
        try {
            action()
            throw AssertionError("실패가 발생하지 않았습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
