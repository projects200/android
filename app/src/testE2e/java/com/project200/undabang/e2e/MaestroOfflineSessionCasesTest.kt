package com.project200.undabang.e2e

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.local.PendingLogin
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.SessionExitReason
import com.project200.domain.usecase.ClearSessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroOfflineSessionCasesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = PreferenceManager(context)
    private val bootstrap = mockk<MaestroSessionBootstrap>()
    private val clear = mockk<ClearSessionUseCase>()
    private val cleaner = mockk<SessionDataCleaner>()
    private val exerciseCases = mockk<MaestroOfflineExerciseCases>()
    private val api = mockk<ApiService>()
    private val database = mockk<UndabangDatabase>()
    private val authorized = mockk<AuthState>()
    private val signedOut = AuthState()
    private var current: AuthState = signedOut
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 10, 0),
            MaestroOfflineTimerInput(45, 75, "offline-step"),
        )
    private lateinit var observer: MaestroOfflineSessionCases

    @Before
    fun setUp() {
        preferences.completePendingLogin()
        preferences.saveMemberId("member-a", "subject-a")
        every { authorized.isAuthorized } returns true
        every { authorized.refreshToken } returns "synthetic-refresh"
        every { bootstrap.currentState() } answers { current }
        current = authorized
        coEvery { cleaner.hasStoredData() } returns false
        coEvery { exerciseCases.assertRetained(any(), any(), any()) } returns Unit
        coEvery { exerciseCases.assertErased(any(), any(), any()) } returns Unit
        observer =
            MaestroOfflineSessionCases(
                preferences,
                bootstrap,
                clear,
                cleaner,
                ExerciseImageFileStore(context),
                exerciseCases,
                api,
                database,
            )
    }

    @After
    fun tearDown() {
        preferences.completePendingLogin()
        preferences.clearMemberId()
    }

    @Test
    fun `global clearing baseline rejects any preexisting product data without blessing the snapshot`() =
        runTest {
            coEvery { cleaner.hasStoredData() } returns true
            val saved = JSONObject()
            assertThat(failure { observer.observe(request(), run, saved, "session-baseline") })
                .isEqualTo("offline_session_baseline_not_empty")
            assertThat(saved.has("session")).isFalse()
            coVerify(exactly = 0) { clear(any()) }
            coVerify(exactly = 0) { bootstrap.stagePendingLogin(any(), any(), any()) }
        }

    @Test
    fun `baseline cannot be recaptured after pending ownership evidence exists`() =
        runTest {
            val saved = prepared()
            assertThat(failure { observer.observe(request(), run, saved, "session-baseline") })
                .isEqualTo("offline_session_boundary_invalid")
            assertThat(saved.getJSONObject("session").getBoolean("pendingCaptured")).isTrue()
        }

    @Test
    fun `forced policy exit checks frozen data before and after and never uses user logout`() =
        runTest {
            val saved = prepared()
            coEvery { clear(SessionExitReason.FORCED) } coAnswers { current = signedOut }
            observer.control(request(), run, saved, "force-exit", null, null)
            observer.observe(request(), run, saved, "session-forced-retained")
            assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
            assertThat(saved.getJSONObject("session").getBoolean("forcedExit")).isTrue()
            coVerify(exactly = 1) { clear(SessionExitReason.FORCED) }
            coVerify(exactly = 0) { clear(SessionExitReason.USER_INITIATED) }
            coVerify(exactly = 4) { exerciseCases.assertRetained(any(), any(), any()) }
            coVerify(exactly = 0) { cleaner.clearAll() }
        }

    @Test
    fun `peer staging is rejected before initial clean baseline and immutable exercise proof`() =
        runTest {
            val saved = JSONObject().put("session", JSONObject().put("pendingCaptured", true))
            failure { observer.control(request("offline-session-adoption"), run, saved, "stage-peer-login", authorized, "member-b") }
            coVerify(exactly = 0) { bootstrap.stagePendingLogin(any(), any(), any()) }
            assertThat(preferences.getMemberId()).isEqualTo("member-a")
        }

    @Test
    fun `pending peer observation requires genuine production confirmation and keeps original owner`() =
        runTest {
            val request = request("offline-session-adoption")
            val saved = prepared(request)
            val session = saved.getJSONObject("session").put("peerMemberId", "member-b").put("stagedTarget", "peer")
            preferences.savePendingLogin(pending(confirmation = null))
            failure { observer.observe(request, run, saved, "session-peer-pending") }
            assertThat(session.optBoolean("peerConfirmationObserved")).isFalse()
            preferences.savePendingLogin(pending(confirmation = "private-confirmation"))
            assertThat(observer.observe(request, run, saved, "session-peer-pending")).isEmpty()
            assertThat(preferences.getMemberId()).isNull()
            assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
            assertThat(session.getBoolean("peerConfirmationObserved")).isTrue()
            coVerify(exactly = 0) { bootstrap.authenticate(any(), any()) }
        }

    @Test
    fun `cancelled peer login and process reconstruction preserve A without approving erasure`() =
        runTest {
            val request = request("offline-session-adoption")
            val saved = prepared(request)
            saved.getJSONObject("session").put("peerMemberId", "member-b").put("stagedTarget", "peer")
            preferences.savePendingLogin(pending("private-confirmation"))
            observer.observe(request, run, saved, "session-peer-pending")
            // Product cancelLogin has cleared credentials and its pending attempt, not the retained owner.
            preferences.completePendingLogin()
            current = signedOut
            val reopened = JSONObject(saved.toString())
            observer.observe(request, run, reopened, "session-peer-cancelled")
            assertThat(reopened.getJSONObject("session").getBoolean("peerCancelled")).isTrue()
            assertThat(reopened.getJSONObject("session").optBoolean("approvedErasure")).isFalse()
            failure { observer.requireSafeCleanup(request, run, reopened) }
            coVerify(exactly = 0) { clear(any()) }
            coVerify(exactly = 0) { cleaner.clearAll() }
        }

    @Test
    fun `UI logout approval cannot become proof until all frozen original rows and files are absent`() =
        runTest {
            val request = request("offline-session-logout")
            val saved = prepared(request)
            observer.observe(request, run, saved, "session-logout-cancelled")
            preferences.clearMemberId()
            current = signedOut
            coEvery { exerciseCases.assertErased(any(), any(), any()) } throws MaestroSetupFailure("original_file_present")
            assertThat(failure { observer.observe(request, run, saved, "session-logout-approved") }).isEqualTo("original_file_present")
            assertThat(saved.getJSONObject("session").optBoolean("approvedErasure")).isFalse()
            coEvery { exerciseCases.assertErased(any(), any(), any()) } returns Unit
            observer.observe(request, run, saved, "session-logout-approved")
            assertThat(saved.getJSONObject("session").getBoolean("approvedErasure")).isTrue()
            coVerify(exactly = 0) { clear(any()) }
            coVerify(exactly = 0) { cleaner.clearAll() }
        }

    @Test
    fun `active B cannot be silently restored to A during cleanup or credential staging`() =
        runTest {
            val request = request("offline-session-adoption")
            val saved = prepared(request)
            saved.getJSONObject("session")
                .put("approvedErasure", true).put("peerApproved", true).put("peerMemberId", "member-b")
            preferences.saveMemberId("member-b", "subject-b")
            failure { observer.control(request, run, saved, "stage-primary-login", authorized, "member-a") }
            failure { observer.requireSafeCleanup(request, run, saved) }
            coVerify(exactly = 0) { bootstrap.stagePendingLogin(any(), any(), any()) }
            coVerify(exactly = 0) { clear(any()) }
            assertThat(preferences.getMemberId()).isEqualTo("member-b")
        }

    @Test
    fun `approved flag alone cannot authorize cleanup and still requires actual restored A`() =
        runTest {
            val request = request("offline-session-adoption")
            val saved = prepared(request)
            saved.getJSONObject("session").put("approvedErasure", true)
            failure { observer.requireSafeCleanup(request, run, saved) }
            assertThat(preferences.getMemberId()).isEqualTo("member-a")
            coVerify(exactly = 0) { bootstrap.stagePendingLogin(any(), any(), any()) }
        }

    @Test
    fun `independent B server result containing an A title fails isolation without blessing cleanup`() =
        runTest {
            val request = request("offline-session-adoption")
            val saved = prepared(request)
            val session =
                saved.getJSONObject("session")
                    .put("approvedErasure", true).put("peerApproved", true).put("peerMemberId", "member-b")
            preferences.saveMemberId("member-b", "subject-b")
            coEvery { bootstrap.verifyCurrent("member-b") } returns "member-b"
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns
                BaseResponse(
                    true,
                    "OK",
                    "",
                    listOf(GetExerciseRecordListDto(71, run.exerciseTitle, "RUNNING", run.startedAt, run.endedAt, null)),
                )
            assertThat(failure { observer.observe(request, run, saved, "session-peer-isolated") })
                .isEqualTo("offline_session_peer_not_isolated")
            assertThat(session.optBoolean("peerIsolated")).isFalse()
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
            coVerify(exactly = 0) { bootstrap.stagePendingLogin(any(), any(), any()) }
        }

    private suspend fun prepared(request: MaestroFeatureRequest = request()): JSONObject =
        JSONObject().also {
            observer.observe(request, run, it, "session-baseline")
            observer.observe(request, run, it, "session-pending")
        }

    private fun pending(confirmation: String?): PendingLogin =
        PendingLogin(
            "private-attempt",
            "member-a",
            "subject-a",
            preferences.exerciseSessionRevision(),
            0,
            "subject-b",
            "member-b",
            confirmation,
        )

    private fun request(flow: String = "offline-session-retain"): MaestroFeatureRequest =
        MaestroFeatureRequest("offline", flow, run.id, "member-a", JSONObject())

    private suspend fun failure(action: suspend () -> Unit): String {
        val error = runCatching { action() }.exceptionOrNull()
        assertThat(error).isInstanceOf(MaestroSetupFailure::class.java)
        return (error as MaestroSetupFailure).reason
    }
}
