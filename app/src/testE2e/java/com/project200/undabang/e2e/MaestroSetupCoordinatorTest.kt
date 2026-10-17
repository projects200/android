package com.project200.undabang.e2e

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MaestroSetupCoordinatorTest {
    private val protocol = mockk<MaestroSetupProtocol>(relaxed = true)
    private val session = mockk<MaestroSessionBootstrap>(relaxed = true)
    private val legacyCleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
    private val fixtures = mockk<MaestroOfflineFixtures>(relaxed = true)
    private val coordinator = MaestroSetupCoordinator(protocol, session, legacyCleaner, mapOf("offline" to fixtures), fixtures)
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 10, 15, 9, 0),
            LocalDateTime.of(2026, 10, 15, 10, 0),
        )
    private val feature = MaestroFeatureRequest("offline", "offline-timers-online", run.id, "dedicated-member", mockk())

    @Test
    fun `업로드 관찰은 ownership과 현재 세션만 검증하고 인증 복원이나 Room 초기화를 수행하지 않는다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.OfflineObservation("dedicated-member", run, "timers-synced", feature)

            coordinator.consumeRequest()

            coVerifyOrder {
                protocol.validateFeatureRecovery(feature, required = true)
                session.verifyCurrent("dedicated-member")
                fixtures.observe(run, "timers-synced")
                protocol.writeStatus("observed")
            }
            coVerify(exactly = 0) { session.authenticate(any(), any()) }
            coVerify(exactly = 0) { session.clearSession() }
            coVerify(exactly = 0) { session.authenticateForOfflineCleanup(any(), any()) }
            coVerify(exactly = 0) { fixtures.clean(any()) }
            coVerify(exactly = 0) { legacyCleaner.clean(any(), any()) }
        }

    @Test
    fun `오프라인 준비는 공통 인증과 정리와 복구 표식 뒤에 handler를 실행한다`() =
        runTest {
            val auth = mockk<net.openid.appauth.AuthState>()
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.FixtureOperation("prepare", auth, "dedicated-member", LocalDate.of(2026, 1, 1), feature)
            coEvery { fixtures.prepare(feature) } returns emptyMap()

            coordinator.consumeRequest()

            coVerifyOrder {
                session.authenticate(auth, "dedicated-member")
                protocol.validateFeatureRecovery(feature)
                legacyCleaner.clean(any(), any())
                protocol.markFeatureRecovery(feature)
                fixtures.prepare(feature)
                protocol.writeStatus("ready", emptyMap())
            }
        }

    @Test
    fun `계정 불일치면 서버 관찰과 삭제를 수행하지 않는다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.OfflineObservation("wrong-member", run, "timers-synced", feature.copy(memberId = "wrong-member"))
            coEvery { session.verifyCurrent("wrong-member") } throws MaestroSetupFailure("member_mismatch")

            coordinator.consumeRequest()

            coVerify(exactly = 0) { fixtures.observe(any(), any()) }
            coVerify(exactly = 0) { fixtures.clean(any()) }
            verify(exactly = 1) { protocol.writeFailure("member_mismatch") }
            coVerify(exactly = 1) { session.clearTokens() }
            coVerify(exactly = 0) { session.clearSession() }
            verify(exactly = 0) { protocol.writeStatus(any(), any()) }
        }

    @Test
    fun `다른 기능의 미복구 ownership은 관찰 전에 거절한다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.OfflineObservation("dedicated-member", run, "timers-synced", feature)
            every { protocol.validateFeatureRecovery(feature, required = true) } throws
                MaestroSetupFailure("feature_recovery_owner_mismatch")

            coordinator.consumeRequest()

            coVerify(exactly = 0) { session.verifyCurrent(any()) }
            coVerify(exactly = 0) { fixtures.observe(any(), any()) }
            verify { protocol.writeFailure("feature_recovery_owner_mismatch") }
            verify(exactly = 0) { protocol.completeFeatureRecovery() }
            coVerify(exactly = 1) { session.clearTokens() }
            coVerify(exactly = 0) { session.clearSession() }
        }

    @Test
    fun `오프라인 정리가 실패하면 일반 정리와 복구 완료를 실행하지 않는다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.FixtureOperation("cleanup", mockk(), "dedicated-member", LocalDate.of(2026, 1, 1), feature)
            coEvery { fixtures.cleanup(feature) } throws MaestroSetupFailure("fixture_delete_failed")

            coordinator.consumeRequest()

            coVerifyOrder {
                protocol.validateFeatureRecovery(feature, required = true)
                session.authenticateForOfflineCleanup(any(), "dedicated-member")
                fixtures.cleanup(feature)
                session.clearTokens()
                protocol.writeFailure("fixture_delete_failed")
            }
            coVerify(exactly = 0) { session.authenticate(any(), any()) }
            coVerify(exactly = 0) { session.clearSession() }
            coVerify(exactly = 0) { legacyCleaner.clean(any(), any()) }
            verify(exactly = 0) { protocol.completeFeatureRecovery() }
            verify(exactly = 0) { protocol.writeStatus(any(), any()) }
            verify { protocol.writeFailure("fixture_delete_failed") }
        }

    @Test
    fun `오프라인 cleanup은 같은 계정 검증과 두 정리를 마친 뒤 세션과 ownership을 완료한다`() =
        runTest {
            val auth = mockk<net.openid.appauth.AuthState>()
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.FixtureOperation("cleanup", auth, "dedicated-member", LocalDate.of(2026, 1, 1), feature)

            coordinator.consumeRequest()

            coVerifyOrder {
                protocol.validateFeatureRecovery(feature, required = true)
                session.authenticateForOfflineCleanup(auth, "dedicated-member")
                fixtures.cleanup(feature)
                legacyCleaner.clean(any(), any())
                session.clearSession()
                protocol.completeFeatureRecovery()
                protocol.writeStatus("cleaned", emptyMap())
            }
            coVerify(exactly = 0) { session.authenticate(any(), any()) }
            coVerify(exactly = 0) { session.clearTokens() }
        }

    @Test
    fun `파싱된 오프라인 관찰과 cleanup은 결과 파일 초기화 실패부터 회원 보존 정책을 적용한다`() =
        runTest {
            val requests =
                listOf(
                    MaestroSetupRequest.OfflineObservation("dedicated-member", run, "timers-synced", feature),
                    MaestroSetupRequest.FixtureOperation("cleanup", mockk(), "dedicated-member", LocalDate.of(2026, 1, 1), feature),
                )
            every { protocol.clearOutputs() } throws MaestroSetupFailure("private_file_failed")

            for (request in requests) {
                every { protocol.consumeRequest() } returns request
                coordinator.consumeRequest()
            }

            coVerify(exactly = 2) { session.clearTokens() }
            coVerify(exactly = 0) { session.clearSession() }
            coVerify(exactly = 0) { session.authenticate(any(), any()) }
            coVerify(exactly = 0) { session.authenticateForOfflineCleanup(any(), any()) }
            coVerify(exactly = 0) { session.verifyCurrent(any()) }
            coVerify(exactly = 0) { fixtures.cleanup(any()) }
            coVerify(exactly = 0) { fixtures.observe(any(), any()) }
            verify(exactly = 0) { protocol.completeFeatureRecovery() }
            verify(exactly = 2) { protocol.writeFailure("private_file_failed") }
        }

    @Test
    fun `오프라인 cleanup은 ownership이 없으면 인증을 바꾸거나 정리하지 않는다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.FixtureOperation("cleanup", mockk(), "dedicated-member", LocalDate.of(2026, 1, 1), feature)
            every { protocol.validateFeatureRecovery(feature, required = true) } throws MaestroSetupFailure("feature_recovery_missing")

            coordinator.consumeRequest()

            coVerify(exactly = 0) { session.authenticateForOfflineCleanup(any(), any()) }
            coVerify(exactly = 0) { session.authenticate(any(), any()) }
            coVerify(exactly = 0) { fixtures.cleanup(any()) }
            coVerify(exactly = 0) { legacyCleaner.clean(any(), any()) }
            coVerify(exactly = 1) { session.clearTokens() }
            coVerify(exactly = 0) { session.clearSession() }
            verify { protocol.writeFailure("feature_recovery_missing") }
        }
}
