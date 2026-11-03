package com.project200.undabang.e2e

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroSetupDiagnosticsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val protocol = spyk(MaestroSetupProtocol(context))
    private val session = mockk<MaestroSessionBootstrap>(relaxed = true)
    private val cleaner = mockk<MaestroFixtureCleaner>(relaxed = true)
    private val offline = mockk<MaestroOfflineFixtures>(relaxed = true)
    private val resultFile = File(context.filesDir, "maestro-result.json")
    private val requestFile = File(context.filesDir, "maestro-request.json")
    private val sessionFile = File(context.filesDir, "maestro-session.json")
    private val recoveryFile = File(context.filesDir, "maestro-feature-recovery.json")

    @Before
    @After
    fun clearFiles() {
        MaestroSetupProtocol(context).apply {
            deleteRequest()
            clearOutputs()
            completeFeatureRecovery()
        }
    }

    @Test
    fun `요청 삭제와 세션 정리 실패는 원래 인증 실패와 별개 JSON 코드로 남는다`() =
        runTest {
            for (feature in listOf(null, "timer", "offline", "chatting")) {
                val preserve = feature in setOf("timer", "offline")
                writePrepare(feature)
                recoveryFile.writeText("private-recovery-evidence")
                coEvery { session.authenticate(any(), any()) } throws MaestroSetupFailure("registration_http_502")
                every { protocol.deleteRequest() } throws IOException(SECRET)
                var sessionDeletes = 0
                every { protocol.deleteSession() } answers {
                    if (++sessionDeletes > 1) throw IOException(SECRET)
                    callOriginal()
                }
                coEvery { session.clearTokens() } throws IOException(SECRET)
                coEvery { session.clearSession() } throws IOException(SECRET)

                coordinator().consumeRequest()

                val text = resultFile.readText()
                val result = JSONObject(text)
                assertThat(result.keys().asSequence().toSet()).containsExactly("status", "reason", "cleanupFailures")
                assertThat(result.getString("status")).isEqualTo("failed")
                assertThat(result.getString("reason")).isEqualTo("registration_http_502")
                val cleanup = result.getJSONArray("cleanupFailures")
                assertThat((0 until cleanup.length()).map(cleanup::getString))
                    .containsExactly("private_file_failed", "local_session_clear_failed")
                assertThat(text).doesNotContain(SECRET)
                assertThat(text).doesNotContain("private-member")
                assertThat(requestFile.exists()).isFalse()
                assertThat(recoveryFile.readText()).isEqualTo("private-recovery-evidence")
                assertThat(sessionDeletes).isEqualTo(2)
                if (preserve) {
                    coVerify { session.clearTokens() }
                } else {
                    coVerify { session.clearSession() }
                }
            }
            coVerify(exactly = 2) { session.clearTokens() }
            coVerify(exactly = 2) { session.clearSession() }
        }

    @Test
    fun `알 수 없는 예외 본문은 결과에 쓰지 않고 성공 상태를 남기지 않는다`() =
        runTest {
            writePrepare(null)
            coEvery { session.authenticate(any(), any()) } throws IOException(SECRET)

            coordinator().consumeRequest()

            val text = resultFile.readText()
            val result = JSONObject(text)
            assertThat(result.getString("status")).isEqualTo("failed")
            assertThat(result.getString("reason")).isEqualTo("setup_failed")
            assertThat(result.getJSONArray("cleanupFailures").length()).isEqualTo(0)
            assertThat(text).doesNotContain(SECRET)
            assertThat(requestFile.exists()).isFalse()
            assertThat(sessionFile.exists()).isFalse()
        }

    @Test
    fun `세션 파일 삭제만 실패해도 최초 실패와 private 코드가 결과에 남는다`() =
        runTest {
            writePrepare(null)
            coEvery { session.authenticate(any(), any()) } coAnswers {
                sessionFile.writeText(SECRET)
                throw MaestroSetupFailure("untrusted_session")
            }
            var sessionDeletes = 0
            every { protocol.deleteSession() } answers {
                if (++sessionDeletes > 1) throw IOException(SECRET)
                callOriginal()
            }

            coordinator().consumeRequest()

            val text = resultFile.readText()
            val result = JSONObject(text)
            assertThat(result.getString("status")).isEqualTo("failed")
            assertThat(result.getString("reason")).isEqualTo("untrusted_session")
            val cleanup = result.getJSONArray("cleanupFailures")
            assertThat((0 until cleanup.length()).map(cleanup::getString)).containsExactly("private_file_failed")
            assertThat(text).doesNotContain(SECRET)
            assertThat(sessionFile.readText()).isEqualTo(SECRET)
            assertThat(sessionDeletes).isEqualTo(2)
        }

    @Test
    fun `세션 삭제와 결과 쓰기가 실패해도 이전 성공 파일은 제거한다`() =
        runTest {
            writePrepare("offline")
            recoveryFile.writeText("private-recovery-evidence")
            coEvery { session.authenticate(any(), any()) } throws MaestroSetupFailure("refresh_failed")
            var sessionDeletes = 0
            every { protocol.deleteSession() } answers {
                if (++sessionDeletes > 1) throw IOException(SECRET)
                callOriginal()
            }
            every { protocol.writeFailure(any(), any()) } answers {
                // 실패한 writer가 성공 파일을 복원해도 clearOutputs가 이를 무효화해야 합니다.
                protocol.writeStatus("ready")
                throw IOException(SECRET)
            }

            coordinator().consumeRequest()

            assertThat(resultFile.exists()).isFalse()
            assertThat(File(context.filesDir, "maestro-result.json.bak").exists()).isFalse()
            assertThat(File(context.filesDir, "maestro-result.json.new").exists()).isFalse()
            assertThat(requestFile.exists()).isFalse()
            assertThat(recoveryFile.readText()).isEqualTo("private-recovery-evidence")
            coVerify(exactly = 1) { session.clearTokens() }
            coVerify(exactly = 0) { session.clearSession() }
            assertThat(sessionDeletes).isEqualTo(3)
        }

    private fun coordinator() = MaestroSetupCoordinator(protocol, session, cleaner, emptyMap(), offline)

    private fun writePrepare(feature: String?) {
        requestFile.writeText(
            JSONObject()
                .put("operation", "prepare")
                .put("authState", AuthState().jsonSerialize())
                .put("memberId", "private-member")
                .put("fixtureStartDate", "2026-01-01")
                .apply {
                    if (feature != null) {
                        put("feature", feature)
                        put("flow", "$feature-flow")
                        put("runId", "0123456789abcdef")
                        put("parameters", JSONObject())
                    }
                }.toString(),
        )
    }

    companion object {
        private const val SECRET = "synthetic-private-exception-body"
    }
}
