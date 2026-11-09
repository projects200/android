package com.project200.undabang.e2e

import android.app.Application
import android.database.MatrixCursor
import android.util.AtomicFile
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.utils.ExerciseSyncAccount
import com.project200.data.utils.ExerciseSyncAccountGuard
import com.project200.undabang.oauth.AuthStateManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.openid.appauth.AuthState
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
class MaestroOfflineTransportTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database = mockk<UndabangDatabase>()
    private val helper = mockk<SupportSQLiteOpenHelper>()
    private val sql = mockk<SupportSQLiteDatabase>()
    private val preferences = mockk<PreferenceManager>()
    private val auth = mockk<AuthStateManager>()
    private val authState = mockk<AuthState>()
    private val files = ExerciseImageFileStore(context)
    private val accounts = ExerciseSyncAccountGuard(preferences, files, auth)
    private val faultFile = File(context.filesDir, "maestro-offline-transport.json")
    private val snapshotFile = File(context.filesDir, "maestro-offline-state.json")
    private val traceFile = File(context.filesDir, "maestro-offline-body-trace.json")
    private var currentMember: String? = MEMBER
    private var sessionRevision = 7L
    private var rows = listOf(row(), row())
    private lateinit var transport: MaestroOfflineTransport

    @Before
    fun setUp() {
        AtomicFile(faultFile).delete()
        AtomicFile(snapshotFile).delete()
        File(faultFile.path + ".new").delete()
        File(snapshotFile.path + ".new").delete()
        AtomicFile(traceFile).delete()
        File(traceFile.path + ".new").delete()
        every { preferences.getMemberId() } answers { currentMember }
        every { preferences.getMemberSubject() } answers { currentMember }
        every { preferences.exerciseSessionRevision() } answers { sessionRevision }
        every { auth.getCurrent() } returns authState
        every { authState.idToken } answers { syntheticToken(currentMember ?: "signed-out") }
        every { database.openHelper } returns helper
        every { helper.readableDatabase } returns sql
        every { sql.query(any<String>(), any<Array<out Any?>>()) } answers {
            val arguments = secondArg<Array<out Any?>>()
            assertThat(arguments.toList()).containsExactly(MEMBER, LOCAL, TITLE, "$TITLE-edited", MEMBER, LOCAL, TITLE, "$TITLE-edited").inOrder()
            MatrixCursor(arrayOf("localId", "serverId", "title", "startedAt", "endedAt")).apply {
                rows.forEach { addRow(it) }
            }
        }
        saveSnapshot()
        transport = newTransport()
    }

    @After
    fun tearDown() {
        AtomicFile(faultFile).delete()
        AtomicFile(snapshotFile).delete()
        File(faultFile.path + ".new").delete()
        File(snapshotFile.path + ".new").delete()
        AtomicFile(traceFile).delete()
        File(traceFile.path + ".new").delete()
    }

    @Test
    fun `BODY는 알려진 소유 ID의 PATCH만 HTTP 전에 IOException으로 차단한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val saved = faultFile.readBytes()
        assertFault(request("PATCH", "/dev/api/v1/exercises/41"))
        assertFault(request("PATCH", "/api/v1/exercises/41"))
        assertPass(request("POST", "/dev/api/v1/exercises"))
        assertPass(request("DELETE", "/dev/api/v1/exercises/41"))
        assertPass(request("GET", "/dev/api/v1/exercises/41"))
        assertPass(request("PATCH", "/dev/api/v1/exercises/42"))
        assertPass(request("POST", "/dev/api/v1/exercises/41/pictures"))
        assertThat(faultFile.readBytes()).isEqualTo(saved)
    }

    @Test
    fun `PHOTOS는 생성 ACK 전에는 통과하고 같은 localId의 ACK ID만 고정한다`() {
        rows = listOf(row(serverId = null), row(serverId = null))
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.PHOTOS)
        assertThat(JSONObject(faultFile.readText()).isNull("serverId")).isTrue()
        assertPass(request("POST", "/dev/api/v1/exercises"))
        assertPass(request("POST", "/dev/api/v1/exercises/41/pictures"))
        rows = listOf(row(), row())
        assertFault(request("POST", "/dev/api/v1/exercises/41/pictures?uploadRequestId=owned"))
        assertThat(JSONObject(faultFile.readText()).getLong("serverId")).isEqualTo(41L)
        assertFault(request("DELETE", "/dev/api/v1/exercises/41/pictures?pictureIds=91"))
        assertPass(request("PATCH", "/dev/api/v1/exercises/41"))
        assertPass(request("DELETE", "/dev/api/v1/exercises/41"))
        assertPass(request("GET", "/dev/api/v1/exercises/41/pictures"))
        rows = listOf(row(serverId = 42), row(serverId = 42))
        assertPass(request("POST", "/dev/api/v1/exercises/42/pictures"))
    }

    @Test
    fun `태그 없는 fixture API와 외부 회원 요청은 같은 ID라도 그대로 통과한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", null))
        currentMember = "foreign-member"
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", ExerciseSyncAccount("foreign-member", sessionRevision, 0)))
        currentMember = null
        assertPass(request("PATCH", "/dev/api/v1/exercises/41"))
        verify(exactly = 1) { sql.query(any<String>(), any<Array<out Any?>>()) }
    }

    @Test
    fun `현재 회원이 같아도 오래된 로그인 세션이나 파일 세션은 주입하지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val original = account()
        sessionRevision++
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", original))
        assertFault(request("PATCH", "/dev/api/v1/exercises/41"))
        val beforeFileExit = account()
        files.invalidateSession()
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", beforeFileExit))
        assertFault(request("PATCH", "/dev/api/v1/exercises/41"))
    }

    @Test
    fun `다시 만든 controller는 원 run 소유권을 유지하고 새 프로세스 세션만 허용한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val bytes = faultFile.readBytes()
        sessionRevision = 0
        val restartedFiles = ExerciseImageFileStore(context)
        val restartedGuard = ExerciseSyncAccountGuard(preferences, restartedFiles, auth)
        transport = MaestroOfflineTransport(context, database, preferences, restartedGuard)
        assertFault(request("PATCH", "/dev/api/v1/exercises/41", ExerciseSyncAccount(MEMBER, 0, 0)))
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", ExerciseSyncAccount(MEMBER, 7, 0)))
        assertThat(faultFile.readBytes()).isEqualTo(bytes)
        assertFailure("offline_transport_armed") { transport.assertClear() }
        transport.clear(MEMBER, RUN)
        transport.assertClear()
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", ExerciseSyncAccount(MEMBER, 0, 0)))
    }

    @Test
    fun `정확한 endpoint가 아닌 사진과 본문 경로는 차단하지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.PHOTOS)
        listOf(
            "/other/api/v1/exercises/41/pictures",
            "/dev/api/v1/exercises/41/pictures/91",
            "/dev/api/v1/exercises/41/pictures/",
            "/dev/api/v1/exercises/041/pictures",
            "/dev/api/v1/exercises/41/pictures-extra",
            "/dev/api/v1/feeds/41/pictures",
            "/dev/api/v1/profile-pictures",
        ).forEach { assertPass(request("POST", it)) }
    }

    @Test
    fun `BODY는 미확정 서버 ID와 기존 baseline 기록을 arm하지 않는다`() {
        rows = listOf(row(serverId = null), row(serverId = null))
        assertFailure("offline_transport_ownership_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
        transport.assertClear()
        rows = listOf(row(), row())
        saveSnapshot(baseline = JSONArray().put(LOCAL))
        assertFailure("offline_transport_ownership_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
        transport.assertClear()
    }

    @Test
    fun `제목과 시각 및 양쪽 투영의 소유권을 모두 확인한 뒤 arm한다`() {
        val invalidRows =
            listOf(
                listOf(row(title = "user-record"), row(title = "user-record")),
                listOf(row(startedAt = "2026-01-01T09:01"), row()),
                listOf(row(), row(serverId = 42)),
                listOf(row(), row(title = "$TITLE-edited", startedAt = "2026-01-01T09:05")),
                listOf(row()),
                listOf(row(), row(), row(localId = "other-local")),
            )
        invalidRows.forEach {
            rows = it
            assertFailure("offline_transport_ownership_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
            transport.assertClear()
        }
    }

    @Test
    fun `서명된 세션이 없거나 private snapshot 회원 실행이 다르면 arm하지 않는다`() {
        currentMember = null
        assertFailure("offline_transport_ownership_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
        currentMember = MEMBER
        saveSnapshot(memberId = "foreign")
        assertFailure("offline_transport_ownership_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
        saveSnapshot(runId = OTHER_RUN)
        assertFailure("offline_transport_ownership_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
        transport.assertClear()
    }

    @Test
    fun `다른 mode나 소유자가 기존 fault를 덮어쓰거나 지우지 못한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val bytes = faultFile.readBytes()
        val recovery = snapshotFile.readBytes()
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        assertFailure("offline_transport_already_armed") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.PHOTOS) }
        assertFailure("offline_transport_ownership_invalid") { transport.clear("foreign", RUN) }
        assertFailure("offline_transport_ownership_invalid") { transport.clear(MEMBER, OTHER_RUN) }
        assertThat(faultFile.readBytes()).isEqualTo(bytes)
        assertThat(snapshotFile.readBytes()).isEqualTo(recovery)
    }

    @Test
    fun `private snapshot이 바뀌거나 없으면 clear는 증거를 보존하고 실패한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val bytes = faultFile.readBytes()
        saveSnapshot(runId = OTHER_RUN)
        assertFailure("offline_transport_ownership_invalid") { transport.clear(MEMBER, RUN) }
        assertThat(faultFile.readBytes()).isEqualTo(bytes)
        snapshotFile.delete()
        assertFailure("offline_transport_state_invalid") { transport.clear(MEMBER, RUN) }
        assertThat(faultFile.readBytes()).isEqualTo(bytes)
    }

    @Test
    fun `승인된 행 정리와 로그아웃 뒤에도 원 실행만 fault를 해제할 수 있다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        rows = emptyList()
        currentMember = null
        val snapshot = snapshotFile.readBytes()
        transport.clear(MEMBER, RUN)
        transport.assertClear()
        assertThat(snapshotFile.readBytes()).isEqualTo(snapshot)
        verify(exactly = 1) { sql.query(any<String>(), any<Array<out Any?>>()) }
    }

    @Test
    fun `손상과 알 수 없는 제어 필드는 실패하고 원본 fault와 recovery를 보존한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val original = faultFile.readText()
        val snapshot = snapshotFile.readBytes()
        val malformed =
            listOf(
                "{private diagnostic",
                JSONObject(original).put("mode", "ALL").toString(),
                JSONObject(original).put("serverId", "41").toString(),
                JSONObject(original).put("unknown", true).toString(),
                JSONObject(original).put("schema", "1").toString(),
                JSONObject(original).put("serverId", JSONObject.NULL).toString(),
                JSONObject(original).put("endedAt", "2026-01-01T10:01").toString(),
            )
        malformed.forEach {
            faultFile.writeText(it)
            assertFailure("offline_transport_state_invalid") { transport.clear(MEMBER, RUN) }
            assertFailure("offline_transport_state_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
            assertThat(faultFile.readText()).isEqualTo(it)
            assertThat(snapshotFile.readBytes()).isEqualTo(snapshot)
            assertPass(request("PATCH", "/dev/api/v1/exercises/42"))
            assertPass(request("PATCH", "/dev/api/v1/exercises/41", null))
        }
    }

    @Test
    fun `중단된 AtomicFile write가 있으면 새 fault를 만들거나 기존 증거를 지우지 않는다`() {
        val pending = File(faultFile.path + ".new")
        pending.writeText("interrupted-private-state")
        assertFailure("offline_transport_armed") { transport.assertClear() }
        assertFailure("offline_transport_state_invalid") { transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY) }
        assertFailure("offline_transport_state_invalid") { transport.clear(MEMBER, RUN) }
        assertThat(pending.readText()).isEqualTo("interrupted-private-state")
    }

    @Test
    fun `잘못된 제어 식별자는 기존 fault와 recovery를 변경하지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val fault = faultFile.readBytes()
        val recovery = snapshotFile.readBytes()
        assertFailure("offline_transport_invalid_request") { transport.clear("", RUN) }
        assertFailure("offline_transport_invalid_request") { transport.clear(MEMBER, "not-a-run") }
        assertFailure("offline_transport_invalid_request") { transport.arm(MEMBER, RUN.uppercase(), LOCAL, MaestroOfflineTransport.Mode.BODY) }
        assertFailure("offline_transport_invalid_request") { transport.arm(MEMBER, RUN, "", MaestroOfflineTransport.Mode.BODY) }
        assertThat(faultFile.readBytes()).isEqualTo(fault)
        assertThat(snapshotFile.readBytes()).isEqualTo(recovery)
    }

    @Test
    fun `주입 시점에도 row나 snapshot 소유권이 달라지면 다른 기록을 실패시키지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val bytes = faultFile.readBytes()
        rows = listOf(row(title = "user-record"), row(title = "user-record"))
        assertPass(request("PATCH", "/dev/api/v1/exercises/41"))
        rows = listOf(row(), row())
        saveSnapshot(runId = OTHER_RUN)
        assertPass(request("PATCH", "/dev/api/v1/exercises/41"))
        assertThat(faultFile.readBytes()).isEqualTo(bytes)
    }

    @Test
    fun `clear 뒤 실제 ACK도 동일 요청 바이트로 기록하고 응답은 소비하거나 바꾸지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        val patch = request("PATCH", "/dev/api/v1/exercises/41")
        assertFault(patch)
        transport.clear(MEMBER, RUN)
        transport.assertClear()
        transport = newTransport()
        val response = passResponse(patch, 200, """{"succeed":true,"code":"OK","data":{"exerciseId":41,"version":8}}""")
        assertThat(response.body!!.string()).contains("\"version\":8")
        response.close()
        val attempts = transport.bodyTrace(MEMBER, RUN, LOCAL)
        assertThat(attempts.map { it.outcome }).containsExactly(
            MaestroOfflineTransport.BodyOutcome.INJECTED, MaestroOfflineTransport.BodyOutcome.ACK,
        ).inOrder()
        val bytes = Buffer().also { patch.body!!.writeTo(it) }.readByteArray()
        assertThat(attempts[0].bodySha256).isEqualTo(MaestroOfflinePhotoMedia.sha256(bytes))
        assertThat(attempts[1].bodySha256).isEqualTo(attempts[0].bodySha256)
        assertThat(attempts[1].editId).isEqualTo(EDIT_ID)
        assertThat(attempts[1].baseVersion).isEqualTo(7)
        assertThat(attempts[1].editedAt).isEqualTo(CLOCK)
        val persisted = traceFile.readText()
        assertThat(persisted).doesNotContain("private-owned-description")
        assertThat(persisted).doesNotContain("example.test")
        assertThat(persisted).doesNotContain("synthetic-secret-token")
        assertThat(android.system.Os.stat(traceFile.path).st_mode and 511).isEqualTo(384)
        // safe owned UI cleanup is checked by ExerciseCases before this lifecycle operation
        transport.finishTrace(MEMBER, RUN, LOCAL)
        assertThat(traceFile.exists()).isFalse()
    }

    @Test
    fun `같은 endpoint라도 stale session fixture foreign row는 trace에 포함하지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        transport.clear(MEMBER, RUN)
        val stale = account()
        sessionRevision++
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", stale))
        assertPass(request("PATCH", "/dev/api/v1/exercises/41", null))
        assertPass(request("PATCH", "/dev/api/v1/exercises/42"))
        rows = listOf(row(title = "foreign"), row(title = "foreign"))
        assertPass(request("PATCH", "/dev/api/v1/exercises/41"))
        rows = listOf(row(), row())
        assertThat(transport.bodyTrace(MEMBER, RUN, LOCAL)).isEmpty()
        assertFailure("offline_transport_ownership_invalid") { transport.bodyTrace("foreign", RUN, LOCAL) }
        assertFailure("offline_transport_ownership_invalid") { transport.finishTrace(MEMBER, OTHER_RUN, LOCAL) }
        assertFailure("offline_transport_ownership_invalid") { transport.finishTrace(MEMBER, RUN, "foreign-local") }
        assertThat(traceFile.exists()).isTrue()
    }

    @Test
    fun `실제 conflict code만 확정 거절로 기록하고 rebase key와 원문 digest를 구분한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        transport.clear(MEMBER, RUN)
        val patch = request("PATCH", "/dev/api/v1/exercises/41")
        passResponse(patch, 409, """{"succeed":false,"code":"EXERCISE_EDIT_CONFLICT","message":"private-error"}""").close()
        val nextBody = patchBody().put("editId", OTHER_EDIT_ID).put("baseVersion", 8)
        val rebased = patch.newBuilder().method("PATCH", nextBody.toString().toRequestBody()).build()
        passResponse(rebased, 200, """{"succeed":true,"code":"OK"}""").close()
        val attempts = transport.bodyTrace(MEMBER, RUN, LOCAL)
        assertThat(attempts[0].outcome).isEqualTo(MaestroOfflineTransport.BodyOutcome.CONFLICT)
        assertThat(attempts[0].responseStatus).isEqualTo(409)
        assertThat(attempts[1].editId).isEqualTo(OTHER_EDIT_ID)
        assertThat(attempts[1].baseVersion).isEqualTo(8)
        assertThat(attempts[1].intendedSha256).isEqualTo(attempts[0].intendedSha256)
        assertThat(attempts[1].bodySha256).isNotEqualTo(attempts[0].bodySha256)
        assertThat(traceFile.readText()).doesNotContain("private-error")
        passResponse(rebased, 409, """{"succeed":false,"code":"EXERCISE_NOT_FOUND"}""").close()
        assertThat(transport.bodyTrace(MEMBER, RUN, LOCAL).last().outcome).isEqualTo(MaestroOfflineTransport.BodyOutcome.OTHER)
    }

    @Test
    fun `trace 유실 손상 중단과 크기 초과는 성공한 HTTP를 바꾸지 않고 증명만 실패시킨다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        transport.clear(MEMBER, RUN)
        val original = traceFile.readText()
        val corrupt = listOf(
            "{broken-private-trace",
            JSONObject(original).put("extra", "private").toString(),
            JSONObject(original).put("attempts", JSONArray().put(JSONObject().put("stage", "ACK"))).toString(),
            "x".repeat(65_537),
        )
        corrupt.forEach { bytes ->
            traceFile.writeText(bytes)
            transport = newTransport()
            passResponse(request("PATCH", "/dev/api/v1/exercises/41"), 200, """{"succeed":true}""").close()
            assertFailure("offline_transport_trace_invalid") { transport.bodyTrace(MEMBER, RUN, LOCAL) }
            assertThat(traceFile.readText()).isEqualTo(bytes)
        }
        traceFile.writeText(original)
        File(traceFile.path + ".new").writeText("interrupted-private-trace")
        transport = newTransport()
        assertFailure("offline_transport_trace_invalid") { transport.bodyTrace(MEMBER, RUN, LOCAL) }
        File(traceFile.path + ".new").delete()
        traceFile.delete()
        transport = newTransport()
        assertFailure("offline_transport_trace_missing") { transport.bodyTrace(MEMBER, RUN, LOCAL) }
    }

    @Test
    fun `trace는 최대 시도 수를 넘으면 더 이상 pass proof가 되지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        repeat(65) { assertFault(request("PATCH", "/dev/api/v1/exercises/41")) }
        assertFailure("offline_transport_trace_invalid") { transport.bodyTrace(MEMBER, RUN, LOCAL) }
        assertThat(traceFile.length()).isAtMost(65_536L)
    }

    @Test
    fun `진짜 IO 실패는 원래 예외를 보존하고 미완료 요청을 성공으로 기록하지 않는다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        assertFailure("offline_transport_armed") { transport.finishTrace(MEMBER, RUN, LOCAL) }
        transport.clear(MEMBER, RUN)
        val patch = request("PATCH", "/dev/api/v1/exercises/41")
        val chain = mockk<Interceptor.Chain>()
        val failure = IOException("downstream-sentinel")
        every { chain.request() } returns patch
        every { chain.proceed(patch) } throws failure
        assertSame(failure, assertThrows(IOException::class.java) { transport.intercept(chain) })
        transport = newTransport()
        val attempt = transport.bodyTrace(MEMBER, RUN, LOCAL).single()
        assertThat(attempt.outcome).isEqualTo(MaestroOfflineTransport.BodyOutcome.IO_ERROR)
        assertThat(attempt.responseStatus).isNull()
        assertThat(traceFile.readText()).doesNotContain("downstream-sentinel")
    }

    @Test
    fun `허용되지 않은 요청 key는 원문 없이 trace를 실패시키고 실제 성공은 그대로 반환한다`() {
        transport.arm(MEMBER, RUN, LOCAL, MaestroOfflineTransport.Mode.BODY)
        transport.clear(MEMBER, RUN)
        val patch = request("PATCH", "/dev/api/v1/exercises/41").newBuilder()
            .method("PATCH", patchBody().put("editId", "private-invalid-key").toString().toRequestBody()).build()
        passResponse(patch, 200, """{"succeed":true,"code":"OK"}""").close()
        transport = newTransport()
        assertFailure("offline_transport_trace_invalid") { transport.bodyTrace(MEMBER, RUN, LOCAL) }
        assertThat(traceFile.readText()).doesNotContain("private-invalid-key")
    }

    private fun passResponse(request: Request, code: Int, body: String): Response {
        val chain = mockk<Interceptor.Chain>()
        val downstream = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message("sentinel").body(body.toResponseBody()).build()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns downstream
        assertSame(downstream, transport.intercept(chain))
        verify(exactly = 1) { chain.proceed(request) }
        return downstream
    }

    private fun patchBody() = JSONObject().put("exerciseTitle", TITLE).put("exercisePersonalType", "")
        .put("exerciseLocation", "").put("exerciseDetail", "private-owned-description")
        .put("exerciseStartedAt", "2026-01-01T09:00:00").put("exerciseEndedAt", "2026-01-01T10:00:00")
        .put("editId", EDIT_ID).put("baseVersion", 7).put("editedAt", CLOCK)

    private fun assertFault(request: Request) {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        assertThat(assertThrows(IOException::class.java) { transport.intercept(chain) }.message)
            .isEqualTo("MAESTRO_OFFLINE_TRANSPORT_INJECTED")
        verify(exactly = 0) { chain.proceed(any()) }
    }

    private fun assertPass(request: Request) {
        val chain = mockk<Interceptor.Chain>()
        val downstream =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(599)
                .message("Downstream sentinel")
                .body("downstream".toResponseBody())
                .build()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns downstream
        assertSame(downstream, transport.intercept(chain))
        verify(exactly = 1) { chain.proceed(request) }
        downstream.close()
    }

    private fun assertFailure(
        code: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows(MaestroSetupFailure::class.java) { block() }.reason).isEqualTo(code)
    }

    private fun newTransport() = MaestroOfflineTransport(context, database, preferences, accounts)

    private fun account() = ExerciseSyncAccount(MEMBER, sessionRevision, files.sessionRevision)

    private fun request(
        method: String,
        path: String,
        account: ExerciseSyncAccount? = account(),
    ): Request =
        Request.Builder()
            .url("https://example.test$path")
            .method(method, if (method == "PATCH") patchBody().toString().toRequestBody() else if (method == "POST") "owned-input".toRequestBody() else null)
            .header("Authorization", "Bearer synthetic-secret-token")
            .tag(ExerciseSyncAccount::class.java, account)
            .build()

    private fun saveSnapshot(
        memberId: String = MEMBER,
        runId: String = RUN,
        baseline: JSONArray = JSONArray(),
    ) {
        snapshotFile.writeText(
            JSONObject()
                .put("memberId", memberId)
                .put("runId", runId)
                .put("flow", "offline-body-retry")
                .put("baselineExerciseLocalIds", baseline)
                .put("parameters", JSONObject().put("startedAt", "2026-01-01T09:00").put("endedAt", "2026-01-01T10:00"))
                .toString(),
        )
    }

    private fun syntheticToken(subject: String): String {
        val payload = Base64.encodeToString(JSONObject().put("sub", subject).toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return "header.$payload.signature"
    }

    companion object {
        private const val MEMBER = "member"
        private const val RUN = "0123456789abcdef0123456789abcdef"
        private const val OTHER_RUN = "fedcba9876543210fedcba9876543210"
        private const val LOCAL = "owned-local"
        private const val TITLE = "maestro-e2e-$RUN"
        private const val EDIT_ID = "00000000-0000-0000-0000-000000000001"
        private const val OTHER_EDIT_ID = "00000000-0000-0000-0000-000000000002"
        private const val CLOCK = 1794150000000L

        private fun row(
            localId: String = LOCAL,
            serverId: Long? = 41,
            title: String = TITLE,
            startedAt: String = "2026-01-01T09:00",
        ): Array<Any?> = arrayOf(localId, serverId, title, startedAt, "2026-01-01T10:00")
    }
}
