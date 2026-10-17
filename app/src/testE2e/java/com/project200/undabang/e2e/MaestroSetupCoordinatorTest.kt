package com.project200.undabang.e2e

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime

class MaestroSetupCoordinatorTest {
    private val protocol = mockk<MaestroSetupProtocol>(relaxed = true)
    private val session = mockk<MaestroSessionBootstrap>(relaxed = true)
    private val legacyCleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
    private val fixtures = mockk<MaestroOfflineFixtures>(relaxed = true)
    private val coordinator = MaestroSetupCoordinator(protocol, session, legacyCleaner, fixtures)
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 10, 15, 9, 0),
            LocalDateTime.of(2026, 10, 15, 10, 0),
        )

    @Test
    fun `업로드 관찰은 현재 세션을 검증하고 인증 복원이나 Room 초기화를 수행하지 않는다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.OfflineOperation("offline-observe", "dedicated-member", null, run, "timers-synced")

            coordinator.consumeRequest()

            coVerifyOrder {
                session.verifyCurrent("dedicated-member")
                fixtures.observe(run, "timers-synced")
                protocol.writeStatus("observed")
            }
            coVerify(exactly = 0) { session.authenticate(any(), any()) }
            coVerify(exactly = 0) { session.clearSession() }
            coVerify(exactly = 0) { fixtures.clean(any()) }
            coVerify(exactly = 0) { legacyCleaner.clean(any(), any()) }
        }

    @Test
    fun `오프라인 준비는 계정 확인과 실행별 정리와 플래그 확인 뒤에만 성공한다`() =
        runTest {
            val auth = mockk<net.openid.appauth.AuthState>()
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.OfflineOperation("offline-prepare", "dedicated-member", auth, run, null)

            coordinator.consumeRequest()

            coVerifyOrder {
                session.authenticate(auth, "dedicated-member")
                fixtures.clean(run)
                fixtures.requireCacheEnabled()
                protocol.writeStatus("ready")
            }
        }

    @Test
    fun `계정 불일치면 서버 관찰과 삭제를 수행하지 않는다`() =
        runTest {
            every { protocol.consumeRequest() } returns
                MaestroSetupRequest.OfflineOperation("offline-observe", "wrong-member", null, run, "timers-synced")
            coEvery { session.verifyCurrent("wrong-member") } throws MaestroSetupFailure("member_mismatch")

            coordinator.consumeRequest()

            coVerify(exactly = 0) { fixtures.observe(any(), any()) }
            coVerify(exactly = 0) { fixtures.clean(any()) }
            coVerify(exactly = 1) { protocol.writeFailure("member_mismatch") }
            coVerify(exactly = 0) { protocol.writeStatus(any()) }
        }
}
