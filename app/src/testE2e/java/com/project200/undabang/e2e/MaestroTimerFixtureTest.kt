package com.project200.undabang.e2e

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerDTO
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroTimerFixtureTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val api = mockk<ApiService>()
    private val local = mockk<TimerLocalDataSource>()
    private val fixture = MaestroTimerFixture(context, api, local)
    private val request =
        MaestroFeatureRequest(
            "timer",
            "timer-simple-preset-crud",
            "0123456789abcdef",
            "member",
            JSONObject().put("initialSeconds", "53").put("updatedSeconds", "97"),
        )

    @Before
    fun setUp() {
        File(context.filesDir, "maestro-timer-state.json").delete()
        every { local.currentMemberId() } returns "member"
        coEvery { local.getSimpleTimers("member") } returns emptyList()
        coEvery { local.getCustomTimers("member") } returns emptyList()
        coEvery { api.getSimpleTimers() } returns success(GetSimpleTimersDTO(0, emptyList()))
        coEvery { api.getCustomTimerList() } returns success(GetCustomTimerListDTO(0, emptyList()))
    }

    @Test
    fun `비기본 입력을 UI로 내보내고 관찰한 생성 시간이 다르면 실패한다`() =
        runTest {
            val variables = fixture.prepare(request)
            assertThat(variables["TIMER_INITIAL_TIME"]).isEqualTo("00:53")
            assertThat(variables["TIMER_UPDATED_TIME"]).isEqualTo("01:37")
            coEvery { local.getSimpleTimers("member") } returns listOf(simple("owned", 41))

            expectFailure("timer_local_value_mismatch") { fixture.observe(request, "simple-created") }

            val saved = JSONObject(File(context.filesDir, "maestro-timer-state.json").readText())
            assertThat(saved.getString("simpleLocalId")).isEqualTo("owned")
            assertThat(saved.getJSONObject("inputs").getString("initialSeconds")).isEqualTo("53")
        }

    @Test
    fun `기존 행과 시간이 같아도 새 localId만 관찰하고 서버의 잘못된 시간은 거절한다`() =
        runTest {
            coEvery { local.getSimpleTimers("member") } returns listOf(simple("baseline", 53))
            fixture.prepare(request)
            coEvery { local.getSimpleTimers("member") } returns listOf(simple("baseline", 53), simple("owned", 53))
            assertThat(fixture.observe(request, "simple-created")).containsEntry("TIMER_LOCAL_ID", "owned")
            coEvery { local.getSimpleTimer("owned") } returns simple("owned", 97).copy(serverId = 12)
            coEvery { api.getSimpleTimers() } returns success(GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(12, 42))))

            expectFailure("timer_server_id_missing") { fixture.observe(request, "simple-synced") }

            coEvery { api.getSimpleTimers() } returns success(GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(12, 97))))
            assertThat(fixture.observe(request, "simple-synced")).containsEntry("TIMER_LOCAL_ID", "owned")
            coVerify(exactly = 0) { api.deleteSimpleTimer(any(), any()) }
        }

    @Test
    fun `실행 시나리오도 관찰한 시간이 아니라 준비한 시간으로 서버를 확인한다`() =
        runTest {
            val run =
                request.copy(
                    flow = "timer-simple-run",
                    parameters = JSONObject().put("initialSeconds", "53").put("updatedSeconds", "53"),
                )
            fixture.prepare(run)
            coEvery { local.getSimpleTimers("member") } returns listOf(simple("owned", 53))
            fixture.observe(run, "simple-created")
            coEvery { local.getSimpleTimer("owned") } returns simple("owned", 45).copy(serverId = 12)
            coEvery { api.getSimpleTimers() } returns success(GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(12, 45))))
            expectFailure("timer_server_id_missing") { fixture.observe(run, "simple-synced") }
        }

    @Test
    fun `관찰과 정리는 입력이나 실행 소유권이 바뀐 요청을 받지 않는다`() =
        runTest {
            fixture.prepare(request)
            val changed = request.copy(parameters = JSONObject().put("initialSeconds", "54").put("updatedSeconds", "97"))
            expectFailure("timer_snapshot_input_mismatch") { fixture.observe(changed, "simple-created") }
            expectFailure("timer_snapshot_input_mismatch") { fixture.cleanup(changed) }
            expectFailure("timer_snapshot_identity_invalid") { fixture.observe(request.copy(memberId = "other"), "simple-created") }
            coVerify(exactly = 0) { api.deleteSimpleTimer(any(), any()) }
        }

    @Test
    fun `커스텀 ID는 기존 동명 행이 아니라 생성 전에 정한 소유 제목의 새 행에서만 얻는다`() =
        runTest {
            val customRequest = request.copy(flow = "timer-custom-crud", parameters = JSONObject())
            val title = "e2e611c-${request.runId}"
            coEvery { local.getCustomTimers("member") } returns listOf(custom("baseline", title))
            assertThat(fixture.prepare(customRequest)).containsEntry("TIMER_CUSTOM_TITLE", title)
            coEvery { local.getCustomTimers("member") } returns listOf(custom("baseline", title), custom("owned", title))
            assertThat(fixture.observe(customRequest, "custom-created")).containsEntry("TIMER_CUSTOM_LOCAL_ID", "owned")
            coEvery { local.getCustomTimers("member") } returns listOf(custom("replacement", title))
            expectFailure("timer_custom_identity_changed") { fixture.observe(customRequest, "custom-created") }
        }

    @Test
    fun `중복 소유 제목과 잘못된 입력은 임의의 첫 행으로 대체하지 않는다`() =
        runTest {
            val customRequest = request.copy(flow = "timer-custom-crud", parameters = JSONObject())
            val variables = fixture.prepare(customRequest)
            coEvery { local.getCustomTimers("member") } returns
                listOf(
                    custom("one", variables.getValue("TIMER_CUSTOM_TITLE")),
                    custom("two", variables.getValue("TIMER_CUSTOM_TITLE")),
                )
            expectFailure("timer_custom_local_id_missing") { fixture.observe(customRequest, "custom-created") }
            for (value in listOf("0", "01", "3600", "-1", "1.0")) {
                val parameters = JSONObject().put("initialSeconds", value).put("updatedSeconds", "97")
                expectFailure("invalid_feature_request") { fixture.prepare(request.copy(parameters = parameters)) }
            }
        }

    @Test
    fun `문자열이 아닌 입력과 알 수 없는 키는 서버 조회 전에 거절한다`() =
        runTest {
            val invalid =
                listOf(
                    JSONObject().put("initialSeconds", 53).put("updatedSeconds", "97"),
                    JSONObject().put("initialSeconds", "53"),
                    JSONObject().put("initialSeconds", "53").put("updatedSeconds", "97").put("extra", "value"),
                    JSONObject().put("initialSeconds", "53").put("updatedSeconds", "97").put("stage", "simple-created"),
                )
            for (parameters in invalid) {
                expectFailure("invalid_feature_request") { fixture.prepare(request.copy(parameters = parameters)) }
            }
            coVerify(exactly = 0) { api.getSimpleTimers() }
            coVerify(exactly = 0) { api.getCustomTimerList() }
        }

    @Test
    fun `숫자 입력의 양끝을 두 자리 분초 기대값으로 보낸다`() {
        val input = MaestroSimpleTimerInput.parse(JSONObject().put("initialSeconds", "1").put("updatedSeconds", "3599"))
        assertThat(input.variables()["TIMER_INITIAL_TIME"]).isEqualTo("00:01")
        assertThat(input.variables()["TIMER_UPDATED_TIME"]).isEqualTo("59:59")
    }

    private fun simple(
        id: String,
        seconds: Int,
    ) = SimpleTimerEntity("member", id, null, SyncState.CREATE_PENDING, seconds, "create-$id", null, 1)

    private fun custom(
        id: String,
        title: String,
    ) = CustomTimerEntity("member", id, null, SyncState.CREATE_PENDING, title, emptyList(), "create-$id", null, 1)

    private fun <T> success(value: T) = BaseResponse(true, "OK", "", value)

    private suspend fun expectFailure(
        reason: String,
        action: suspend () -> Unit,
    ) {
        try {
            action()
            throw AssertionError("실패할 관찰이 성공했습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
