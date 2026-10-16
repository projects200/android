package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerDTO
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class MaestroTimerFixtureTest {
    private val apiService = mockk<ApiService>()
    private val fixture = MaestroTimerFixture(apiService)
    private val request = MaestroFeatureRequest("timer", "timer-simple-preset-crud", "0123456789abcdef", "member", mockk())

    @Test
    fun `첫 심플 CRUD 준비는 초기 목록 ID만 삭제하고 빈 서버 목록을 확인한 뒤 건수를 반환한다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returnsMany listOf(timers(7, 23), timers())
            coEvery { apiService.deleteSimpleTimer(any()) } returns deleted()

            val variables = fixture.prepare(request)

            assertThat(variables).containsExactly("TIMER_SIMPLE_RESET_DELETED_COUNT", "2")
            MaestroFeaturePolicy.validateVariables(variables)
            coVerifySequence {
                apiService.getSimpleTimers()
                apiService.deleteSimpleTimer(7)
                apiService.deleteSimpleTimer(23)
                apiService.getSimpleTimers()
            }
        }

    @Test
    fun `초기 목록이 비어도 재조회한 뒤 삭제 건수 0을 반환한다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returns timers()

            assertThat(fixture.prepare(request)).containsExactly("TIMER_SIMPLE_RESET_DELETED_COUNT", "0")

            coVerify(exactly = 2) { apiService.getSimpleTimers() }
            coVerify(exactly = 0) { apiService.deleteSimpleTimer(any()) }
        }

    @Test
    fun `나머지 네 플로우의 준비는 서버를 조회하거나 삭제하지 않는다`() =
        runTest {
            for (flow in listOf("timer-simple-run", "timer-custom-crud", "timer-custom-run", "timer-form-validation")) {
                assertThat(fixture.prepare(request.copy(flow = flow))).isEmpty()
            }

            verify { apiService wasNot Called }
        }

    @Test
    fun `모든 타이머 플로우의 cleanup은 서버를 조회하거나 삭제하지 않는다`() =
        runTest {
            for (
                flow in listOf(
                    "timer-simple-preset-crud",
                    "timer-simple-run",
                    "timer-custom-crud",
                    "timer-custom-run",
                    "timer-form-validation",
                )
            ) {
                fixture.cleanup(request.copy(flow = flow))
            }

            verify { apiService wasNot Called }
        }

    @Test
    fun `다른 feature는 준비와 정리 모두 API 호출 전에 거절한다`() =
        runTest {
            val other = request.copy(feature = "profile")

            expectFailure("timer_fixture_identity_invalid") { fixture.prepare(other) }
            expectFailure("timer_fixture_identity_invalid") { fixture.cleanup(other) }

            verify { apiService wasNot Called }
        }

    @Test
    fun `실패한 초기 목록 응답에 ID가 있어도 삭제하지 않는다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returns timers(7).copy(succeed = false)

            expectFailure("timer_fixture_read_failed") { fixture.prepare(request) }

            coVerify(exactly = 0) { apiService.deleteSimpleTimer(any()) }
        }

    @Test
    fun `초기 목록 DTO가 없으면 삭제하지 않는다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returns BaseResponse(true, "200", "")

            expectFailure("timer_fixture_read_failed") { fixture.prepare(request) }

            coVerify(exactly = 0) { apiService.deleteSimpleTimer(any()) }
        }

    @Test
    fun `초기 목록 건수와 모든 ID를 검증하기 전에는 유효한 앞 ID도 삭제하지 않는다`() =
        runTest {
            val invalid =
                listOf(
                    GetSimpleTimersDTO(-1, emptyList()),
                    GetSimpleTimersDTO(2, listOf(SimpleTimerDTO(7, 60))),
                    GetSimpleTimersDTO(2, listOf(SimpleTimerDTO(7, 60), SimpleTimerDTO(0, 60))),
                    GetSimpleTimersDTO(2, listOf(SimpleTimerDTO(7, 60), SimpleTimerDTO(-1, 60))),
                    GetSimpleTimersDTO(2, listOf(SimpleTimerDTO(7, 60), SimpleTimerDTO(7, 60))),
                )
            for (data in invalid) {
                coEvery { apiService.getSimpleTimers() } returns BaseResponse(true, "200", "", data)

                expectFailure("timer_fixture_read_failed") { fixture.prepare(request) }
            }

            coVerify(exactly = 0) { apiService.deleteSimpleTimer(any()) }
        }

    @Test
    fun `초기 목록 조회 예외는 본문 없는 고정 reason으로 실패한다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } throws IOException("이 예외 본문은 전달하지 않습니다")

            expectFailure("timer_fixture_read_failed") { fixture.prepare(request) }

            coVerify(exactly = 0) { apiService.deleteSimpleTimer(any()) }
        }

    @Test
    fun `일부 삭제 뒤 실패 응답이 오면 뒤 ID를 삭제하거나 성공으로 반환하지 않는다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returns timers(7, 23, 41)
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()
            coEvery { apiService.deleteSimpleTimer(23) } returns deleted().copy(succeed = false)

            expectFailure("timer_fixture_delete_failed") { fixture.prepare(request) }

            coVerifySequence {
                apiService.getSimpleTimers()
                apiService.deleteSimpleTimer(7)
                apiService.deleteSimpleTimer(23)
            }
        }

    @Test
    fun `일부 삭제 뒤 API 예외가 발생해도 고정 삭제 실패를 반환한다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returns timers(7, 23, 41)
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()
            coEvery { apiService.deleteSimpleTimer(23) } throws IOException("이 예외 본문은 전달하지 않습니다")

            expectFailure("timer_fixture_delete_failed") { fixture.prepare(request) }

            coVerifySequence {
                apiService.getSimpleTimers()
                apiService.deleteSimpleTimer(7)
                apiService.deleteSimpleTimer(23)
            }
        }

    @Test
    fun `삭제 후 실패한 조회 응답의 빈 DTO를 성공으로 취급하지 않는다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returnsMany listOf(timers(7), timers().copy(succeed = false))
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()

            expectFailure("timer_fixture_verify_failed") { fixture.prepare(request) }
        }

    @Test
    fun `삭제 후 DTO 없는 응답을 빈 목록으로 취급하지 않는다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returnsMany listOf(timers(7), BaseResponse(true, "200", ""))
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()

            expectFailure("timer_fixture_verify_failed") { fixture.prepare(request) }
        }

    @Test
    fun `삭제 후 재조회 예외는 고정 검증 실패로 반환한다`() =
        runTest {
            var reads = 0
            coEvery { apiService.getSimpleTimers() } coAnswers {
                if (reads++ == 0) timers(7) else throw IOException("이 예외 본문은 전달하지 않습니다")
            }
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()

            expectFailure("timer_fixture_verify_failed") { fixture.prepare(request) }
        }

    @Test
    fun `삭제 후 재조회한 DTO도 건수와 양수 중복없는 ID를 검증한다`() =
        runTest {
            val invalid =
                listOf(
                    GetSimpleTimersDTO(-1, emptyList()),
                    GetSimpleTimersDTO(1, emptyList()),
                    GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(0, 60))),
                    GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(-1, 60))),
                    GetSimpleTimersDTO(2, listOf(SimpleTimerDTO(23, 60), SimpleTimerDTO(23, 60))),
                )
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()
            for (data in invalid) {
                coEvery { apiService.getSimpleTimers() } returnsMany listOf(timers(7), BaseResponse(true, "200", "", data))

                expectFailure("timer_fixture_verify_failed") { fixture.prepare(request) }
            }

            coVerify(exactly = invalid.size) { apiService.deleteSimpleTimer(7) }
            coVerify(exactly = 0) { apiService.deleteSimpleTimer(23) }
        }

    @Test
    fun `재조회에 남은 초기 ID가 있으면 삭제 성공으로 반환하지 않는다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returns timers(7)
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()

            expectFailure("timer_fixture_reset_incomplete") { fixture.prepare(request) }

            coVerify(exactly = 1) { apiService.deleteSimpleTimer(7) }
            coVerify(exactly = 2) { apiService.getSimpleTimers() }
        }

    @Test
    fun `재조회에 처음 없던 ID가 나타나도 추가 삭제하지 않고 실패한다`() =
        runTest {
            coEvery { apiService.getSimpleTimers() } returnsMany listOf(timers(7), timers(23))
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()

            expectFailure("timer_fixture_reset_incomplete") { fixture.prepare(request) }

            coVerifySequence {
                apiService.getSimpleTimers()
                apiService.deleteSimpleTimer(7)
                apiService.getSimpleTimers()
            }
        }

    @Test
    fun `조회 취소를 삭제 준비 성공이나 일반 조회 실패로 바꾸지 않는다`() =
        runTest {
            val cancellation = CancellationException("cancelled")
            coEvery { apiService.getSimpleTimers() } throws cancellation

            try {
                fixture.prepare(request)
                throw AssertionError("취소가 전달되지 않았습니다")
            } catch (failure: CancellationException) {
                assertThat(failure).isSameInstanceAs(cancellation)
            }

            coVerify(exactly = 0) { apiService.deleteSimpleTimer(any()) }
        }

    @Test
    fun `실제 timer handler도 계정 인증이 실패하면 서버 요청 전에 중단한다`() =
        runTest {
            val protocol = mockk<MaestroSetupProtocol>(relaxed = true)
            val session = mockk<MaestroSessionBootstrap>(relaxed = true)
            val cleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
            every { protocol.consumeRequest() } returns operation()
            coEvery { session.authenticate(any(), "member") } throws MaestroSetupFailure("member_mismatch")

            MaestroSetupCoordinator(protocol, session, cleaner, mapOf("timer" to fixture)).consumeRequest()

            verify { apiService wasNot Called }
            coVerify(exactly = 0) { cleaner.clean(any(), any()) }
            verify { protocol.writeFailure("member_mismatch") }
            verify(exactly = 0) { protocol.writeStatus(any(), any()) }
        }

    @Test
    fun `coordinator는 인증과 서버 빈 목록 확인 뒤에만 ready와 삭제 건수를 기록한다`() =
        runTest {
            val protocol = mockk<MaestroSetupProtocol>(relaxed = true)
            val session = mockk<MaestroSessionBootstrap>(relaxed = true)
            val cleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
            every { protocol.consumeRequest() } returns operation()
            coEvery { apiService.getSimpleTimers() } returnsMany listOf(timers(7), timers())
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()

            MaestroSetupCoordinator(protocol, session, cleaner, mapOf("timer" to fixture)).consumeRequest()

            coVerifyOrder {
                session.authenticate(any(), "member")
                cleaner.clean(any(), any())
                protocol.markFeatureRecovery(request)
                apiService.getSimpleTimers()
                apiService.deleteSimpleTimer(7)
                apiService.getSimpleTimers()
                protocol.writeStatus("ready", mapOf("TIMER_SIMPLE_RESET_DELETED_COUNT" to "1"))
            }
            verify(exactly = 0) { protocol.writeFailure(any()) }
        }

    @Test
    fun `coordinator는 재조회 불일치의 고정 reason을 기록하고 ready를 쓰지 않는다`() =
        runTest {
            val protocol = mockk<MaestroSetupProtocol>(relaxed = true)
            val session = mockk<MaestroSessionBootstrap>(relaxed = true)
            val cleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
            every { protocol.consumeRequest() } returns operation()
            coEvery { apiService.getSimpleTimers() } returns timers(7)
            coEvery { apiService.deleteSimpleTimer(7) } returns deleted()

            MaestroSetupCoordinator(protocol, session, cleaner, mapOf("timer" to fixture)).consumeRequest()

            verify { protocol.writeFailure("timer_fixture_reset_incomplete") }
            verify(exactly = 0) { protocol.writeStatus(any(), any()) }
        }

    private fun timers(vararg ids: Long): BaseResponse<GetSimpleTimersDTO> =
        BaseResponse(true, "200", "", GetSimpleTimersDTO(ids.size, ids.map { SimpleTimerDTO(it, 60) }))

    private fun deleted(): BaseResponse<Unit?> = BaseResponse(true, "200", "")

    private fun operation() =
        MaestroSetupRequest.FixtureOperation("prepare", mockk(), "member", LocalDate.of(2026, 1, 1), request)

    private suspend fun expectFailure(
        reason: String,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
            throw AssertionError("실패 응답이 허용되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
            assertThat(failure.message).isEqualTo(reason)
            assertThat(failure.cause).isNull()
        }
    }
}
