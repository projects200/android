package com.project200.undabang.e2e

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.FeatureFlagDataSource
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.CustomTimerDetailStepDTO
import com.project200.data.dto.CustomTimerSummaryDTO
import com.project200.data.dto.DeleteTimerRequestDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.TimerMutationResponseDTO
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.UpdateInfo
import com.project200.domain.repository.AppUpdateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroOfflineFixturesTest {
    private val api = mockk<ApiService>()
    private val flags = mockk<FeatureFlagDataSource>()
    private val updates = mockk<AppUpdateRepository>()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val local = mockk<TimerLocalDataSource>()
    private val fixtures = MaestroOfflineFixtures(api, flags, updates, context, local)
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 10, 15, 9, 0),
            LocalDateTime.of(2026, 10, 15, 10, 0),
            MaestroOfflineTimerInput(45, 75, "offline-step"),
        )

    @Before
    fun setUp() {
        File(context.filesDir, "maestro-offline-state.json").delete()
        coEvery { local.getCustomTimers() } returns emptyList()
    }

    @Test
    fun `실제 활성화된 캐시 플래그가 꺼져 있으면 준비를 거절한다`() =
        runTest {
            coEvery { updates.getUpdateInfo() } returns Result.success(mockk<UpdateInfo>())
            every { flags.isOfflineCacheEnabled() } returns false

            expectFailure("offline_cache_flag_disabled") { fixtures.requireCacheEnabled() }

            coVerify(exactly = 1) { updates.getUpdateInfo() }
            coVerify(exactly = 0) { api.getExerciseList(any()) }
        }

    @Test
    fun `서버 목록과 상세의 정확한 이름 시간 스텝을 직접 확인한다`() =
        runTest {
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer, run.updatedTimer)
            coEvery { api.getCustomTimer(1) } returns success(timer(1, run.createdTimer, 45))
            coEvery { api.getCustomTimer(2) } returns success(timer(2, run.updatedTimer, 75))

            fixtures.observe(run, "timers-synced")

            coVerify(exactly = 1) { api.getCustomTimerList() }
            coVerify(exactly = 1) { api.getCustomTimer(1) }
            coVerify(exactly = 1) { api.getCustomTimer(2) }
            coVerify(exactly = 0) { api.postCustomTimer(any()) }
            coVerify(exactly = 0) { api.putCustomTimer(any(), any()) }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
        }

    @Test
    fun `미전송 생성이나 삭제 잔존은 제한 시간 뒤 실제 실패다`() =
        runTest {
            coEvery { api.getCustomTimerList() } returns timerList(run.originalTimer, run.deletedTimer)

            expectFailure("offline_upload_not_observed") { fixtures.observe(run, "timers-synced") }

            coVerify(atLeast = 2) { api.getCustomTimerList() }
            coVerify(exactly = 0) { api.postCustomTimer(any()) }
        }

    @Test
    fun `같은 고유 제목의 중복 서버 행은 업로드 성공으로 인정하지 않는다`() =
        runTest {
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer, run.createdTimer, run.updatedTimer)

            expectFailure("offline_upload_not_observed") { fixtures.observe(run, "timers-synced") }
        }

    @Test
    fun `수정 제목만 맞고 스텝 시간이 이전 값이면 성공하지 않는다`() =
        runTest {
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer, run.updatedTimer)
            coEvery { api.getCustomTimer(1) } returns success(timer(1, run.createdTimer, 45))
            coEvery { api.getCustomTimer(2) } returns success(timer(2, run.updatedTimer, 45))

            expectFailure("offline_upload_not_observed") { fixtures.observe(run, "timers-synced") }
        }

    @Test
    fun `서버 실패와 null 본문은 빈 목록이나 로컬 성공으로 바꾸지 않는다`() =
        runTest {
            for (response in listOf(
                BaseResponse<GetCustomTimerListDTO>(false, "SERVER_ERROR", "private server diagnostic"),
                BaseResponse<GetCustomTimerListDTO>(true, "OK", "", null),
            )) {
                coEvery { api.getCustomTimerList() } returns response
                expectFailure("fixture_read_failed") { fixtures.observe(run, "timers-synced") }
            }
        }

    @Test
    fun `UI로 만든 운동의 목록과 상세를 서버에서 독립 확인한다`() =
        runTest {
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(listOf(exercise(1, run.exerciseTitle)))
            coEvery { api.getExerciseRecordDetail(1) } returns success(exerciseDetail(run.exerciseTitle))

            fixtures.observe(run, "exercise-online")

            coVerify(exactly = 1) { api.getExerciseRecordDetail(1) }
            coVerify(exactly = 0) { api.postExerciseRecord(any()) }
        }

    @Test
    fun `정리는 이번 UUID와 정확히 일치하는 운동과 타이머만 삭제한다`() =
        runTest {
            val other = "maestro-offline-ffffffffffffffffffffffffffffffff-create"
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns
                success(listOf(exercise(1, run.exerciseTitle), exercise(2, "personal exercise")))
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer, other, "personal timer")
            coEvery { api.getExerciseRecordDetail(1) } returns success(exerciseDetail(run.exerciseTitle))
            coEvery { api.getCustomTimer(1) } returns success(timer(1, run.createdTimer, 45))
            coEvery { api.deleteExerciseRecord(1) } returns success<Unit?>(null)
            coEvery { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) } returns success(TimerMutationResponseDTO())

            fixtures.clean(run)

            coVerify(exactly = 1) { api.deleteExerciseRecord(1) }
            coVerify(exactly = 1) { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) }
            coVerify(exactly = 0) { api.deleteExerciseRecord(2) }
            coVerify(exactly = 0) { api.deleteCustomTimer(2, DeleteTimerRequestDTO()) }
            coVerify(exactly = 0) { api.deleteCustomTimer(3, DeleteTimerRequestDTO()) }
            coVerify(exactly = 0) { api.deleteSimpleTimer(any(), any()) }
        }

    @Test
    fun `스냅샷 저장 전 준비가 실패해도 실행 이름과 상세를 재확인한 행만 정리한다`() =
        runTest {
            coEvery { local.getCustomTimers() } throws IllegalStateException("local read failed")
            try {
                fixtures.prepare(request(run))
                throw AssertionError("실패할 준비가 성공했습니다")
            } catch (failure: IllegalStateException) {
                assertThat(failure.message).isEqualTo("local read failed")
            }
            assertThat(File(context.filesDir, "maestro-offline-state.json").exists()).isFalse()
            coVerify(exactly = 0) { api.getCustomTimerList() }
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns
                success(listOf(exercise(1, run.exerciseTitle), exercise(2, "personal exercise")))
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer, run.originalTimer, "personal timer")
            coEvery { api.getExerciseRecordDetail(1) } returns success(exerciseDetail(run.exerciseTitle))
            coEvery { api.getCustomTimer(1) } returns success(timer(1, run.createdTimer, 45))
            coEvery { api.getCustomTimer(2) } returns success(timer(2, "personal timer", 45))
            coEvery { api.deleteExerciseRecord(1) } returns success<Unit?>(null)
            coEvery { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) } returns success(TimerMutationResponseDTO())

            fixtures.cleanup(request(run))

            coVerify(exactly = 1) { api.deleteExerciseRecord(1) }
            coVerify(exactly = 1) { api.deleteCustomTimer(1, DeleteTimerRequestDTO()) }
            coVerify(exactly = 0) { api.deleteExerciseRecord(2) }
            coVerify(exactly = 0) { api.deleteCustomTimer(2, DeleteTimerRequestDTO()) }
            coVerify(exactly = 0) { api.deleteCustomTimer(3, DeleteTimerRequestDTO()) }
            coVerify(exactly = 0) { api.deleteSimpleTimer(any(), any()) }
        }

    @Test
    fun `모든 목록 조회 전에 삭제하지 않고 뒤 조회 실패 시 후보도 보존한다`() =
        runTest {
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(listOf(exercise(1, run.exerciseTitle)))
            coEvery { api.getCustomTimerList() } returns BaseResponse(false, "SERVER_ERROR", "")

            expectFailure("fixture_read_failed") { fixtures.clean(run) }

            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
        }

    @Test
    fun `상세가 일반 제목으로 바뀐 타이머는 정리하지 않는다`() =
        runTest {
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer)
            coEvery { api.getCustomTimer(1) } returns success(timer(1, "personal timer", 45))

            fixtures.clean(run)

            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
        }

    @Test
    fun `서버에 미저장 draft가 추가되면 두 저장값이 맞아도 성공하지 않는다`() =
        runTest {
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer, run.updatedTimer, run.draftTimer)

            expectFailure("offline_upload_not_observed") { fixtures.observe(run, "timers-synced") }
        }

    @Test
    fun `삭제 실패는 정리 성공으로 숨기지 않는다`() =
        runTest {
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer)
            coEvery { api.getCustomTimer(1) } returns success(timer(1, run.createdTimer, 45))
            coEvery {
                api.deleteCustomTimer(1, DeleteTimerRequestDTO())
            } returns BaseResponse(false, "SERVER_ERROR", "private diagnostic")

            expectFailure("fixture_delete_failed") { fixtures.clean(run) }
        }

    @Test
    fun `공통 feature 준비는 실행별 정리 뒤 실제 플래그를 검사하고 정리는 같은 요청을 재사용한다`() =
        runTest {
            val request = request(run)
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList()
            coEvery { updates.getUpdateInfo() } returns Result.success(mockk<UpdateInfo>())
            every { flags.isOfflineCacheEnabled() } returns true

            val variables = fixtures.prepare(request)
            assertThat(variables["TIMER_INITIAL_TIME"]).isEqualTo("00:45")
            assertThat(variables["TIMER_UPDATED_TIME"]).isEqualTo("01:15")
            assertThat(variables["TIMER_STEP_NAME"]).isEqualTo("offline-step")
            assertThat(variables["TIMER_CREATE_TITLE"]).isEqualTo(run.createdTimer)
            fixtures.cleanup(request)

            coVerifyOrder {
                api.getExerciseList(run.startedAt.toLocalDate())
                api.getCustomTimerList()
                updates.getUpdateInfo()
                flags.isOfflineCacheEnabled()
                api.getExerciseList(run.startedAt.toLocalDate())
                api.getCustomTimerList()
            }
            coVerify(exactly = 0) { api.postCustomTimer(any()) }
            coVerify(exactly = 0) { api.putCustomTimer(any(), any()) }
        }

    @Test
    fun `다른 feature나 관찰 단계가 prepare로 들어오면 서버를 변경하지 않는다`() =
        runTest {
            for (request in listOf(
                MaestroFeatureRequest("profile", "offline-exercise-create", run.id, "member", mockk()),
                MaestroFeatureRequest("offline", "offline-timers-reconnect", run.id, "member", mockk()),
            )) {
                expectFailure("invalid_feature_request") { fixtures.prepare(request) }
            }
            coVerify(exactly = 0) { api.getExerciseList(any()) }
            coVerify(exactly = 0) { api.getCustomTimerList() }
            coVerify(exactly = 0) { updates.getUpdateInfo() }
        }

    @Test
    fun `입력을 바꾸면 UI 기대값과 직접 조회한 서버 값이 함께 바뀐다`() =
        runTest {
            val changed = run.copy(timerInput = MaestroOfflineTimerInput(53, 97, "set-639"))
            coEvery { api.getExerciseList(changed.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList()
            coEvery { updates.getUpdateInfo() } returns Result.success(mockk<UpdateInfo>())
            every { flags.isOfflineCacheEnabled() } returns true
            val variables = fixtures.prepare(request(changed))
            assertThat(variables["TIMER_INITIAL_TIME"]).isEqualTo("00:53")
            assertThat(variables["TIMER_UPDATED_TIME"]).isEqualTo("01:37")
            assertThat(variables["TIMER_UPDATED_MINUTES_PART"]).isEqualTo("1")
            assertThat(variables["TIMER_UPDATED_SECONDS_PART"]).isEqualTo("37")
            coEvery { api.getCustomTimerList() } returns timerList(changed.createdTimer, changed.updatedTimer)
            coEvery { api.getCustomTimer(1) } returns success(timer(1, changed.createdTimer, 53, "set-639"))
            coEvery { api.getCustomTimer(2) } returns success(timer(2, changed.updatedTimer, 97, "set-639"))
            fixtures.observe(changed, "timers-synced")

            coEvery { api.getCustomTimer(2) } returns success(timer(2, changed.updatedTimer, 75, "set-639"))
            expectFailure("offline_upload_not_observed") { fixtures.observe(changed, "timers-synced") }
        }

    @Test
    fun `올바른 ID와 시간만으로 다른 스텝 이름 순서 개수를 통과시키지 않는다`() {
        val expected = timer(2, run.updatedTimer, 97, "set-639")
        assertThat(MaestroOfflineFixtures.matchesTimer(expected, 2, run.updatedTimer, 97, "set-639")).isTrue()
        val wrong =
            listOf(
                expected.copy(customTimerSteps = listOf(expected.customTimerSteps.single().copy(customTimerStepName = "offline-step"))),
                expected.copy(customTimerSteps = listOf(expected.customTimerSteps.single().copy(customTimerStepOrder = 1))),
                expected.copy(customTimerStepCount = 2),
            )
        for (detail in wrong) {
            assertThat(MaestroOfflineFixtures.matchesTimer(detail, 2, run.updatedTimer, 97, "set-639")).isFalse()
        }
    }

    @Test
    fun `관찰과 정리는 준비 전에 저장한 입력을 바꾸지 못한다`() =
        runTest {
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList()
            coEvery { updates.getUpdateInfo() } returns Result.success(mockk<UpdateInfo>())
            every { flags.isOfflineCacheEnabled() } returns true
            fixtures.prepare(request(run))
            val changed = request(run.copy(timerInput = MaestroOfflineTimerInput(53, 97, "set-639")))
            expectFailure("offline_snapshot_input_mismatch") { fixtures.validateRequest(changed) }
            expectFailure("offline_snapshot_input_mismatch") { fixtures.cleanup(changed) }
            expectFailure("offline_snapshot_identity_invalid") { fixtures.cleanup(request(run).copy(memberId = "other")) }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
        }

    @Test
    fun `알 수 없거나 숫자형 또는 비정규 문자열 입력은 UI와 서버 변경 전에 실패한다`() =
        runTest {
            val invalid =
                listOf(
                    request(run).parameters.put("extra", "value"),
                    request(run).parameters.put("initialSeconds", 53),
                    request(run).parameters.put("initialSeconds", "053"),
                    request(run).parameters.put("updatedSeconds", "3600"),
                    request(run).parameters.put("initialSeconds", "4"),
                    request(run).parameters.put("updatedSeconds", run.timerInput.initialSeconds.toString()),
                    request(run).parameters.put("stepName", ""),
                    request(run).parameters.put("stepName", "line\nbreak"),
                )
            for (parameters in invalid) {
                expectFailure("invalid_feature_request") { fixtures.prepare(request(run).copy(parameters = parameters)) }
            }
            coVerify(exactly = 0) { api.getCustomTimerList() }
            coVerify(exactly = 0) { api.getExerciseList(any()) }
        }

    @Test
    fun `기준 동명 행을 보존하고 이번 생성의 ID만 반환하며 중복이나 교체는 거절한다`() =
        runTest {
            val baseline = localTimer("baseline", run.createdTimer)
            coEvery { local.getCustomTimers() } returns listOf(baseline)
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList()
            coEvery { updates.getUpdateInfo() } returns Result.success(mockk<UpdateInfo>())
            every { flags.isOfflineCacheEnabled() } returns true
            val request = request(run)
            fixtures.prepare(request)
            val owned = localTimer("owned", run.createdTimer)
            coEvery { local.getCustomTimers() } returns listOf(baseline, owned)
            assertThat(fixtures.observe(request, "timers-local-created")).containsEntry("TIMER_CREATE_LOCAL_ID", "owned")
            coEvery { local.getCustomTimers() } returns listOf(baseline, owned, localTimer("duplicate", run.createdTimer))
            expectFailure("offline_timer_local_id_missing") { fixtures.observe(request, "timers-local-created") }
            coEvery { local.getCustomTimers() } returns listOf(baseline, localTimer("replacement", run.createdTimer))
            expectFailure("offline_timer_identity_changed") { fixtures.observe(request, "timers-local-created") }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
        }

    private fun localTimer(
        id: String,
        title: String,
    ) = CustomTimerEntity("member", id, null, SyncState.CREATE_PENDING, title, emptyList(), "create-$id", null, 1)

    private fun request(run: MaestroOfflineRun) =
        MaestroFeatureRequest(
            "offline",
            "offline-timers-online",
            run.id,
            "member",
            JSONObject()
                .put("startedAt", run.startedAt.toString())
                .put("endedAt", run.endedAt.toString())
                .put("initialSeconds", run.timerInput.initialSeconds.toString())
                .put("updatedSeconds", run.timerInput.updatedSeconds.toString())
                .put("stepName", run.timerInput.stepName),
        )

    private fun timerList(vararg names: String) =
        success(GetCustomTimerListDTO(names.size, names.mapIndexed { index, name -> CustomTimerSummaryDTO(index.toLong() + 1, name) }))

    private fun timer(
        id: Long,
        name: String,
        seconds: Int,
        stepName: String = "offline-step",
    ) = GetCustomTimerDetailDTO(id, name, 1, listOf(CustomTimerDetailStepDTO(100 + id, stepName, 0, seconds)))

    private fun exercise(
        id: Long,
        title: String,
    ) = GetExerciseRecordListDto(id, title, "OTHER", run.startedAt, run.endedAt, null)

    private fun exerciseDetail(title: String) = GetExerciseRecordData(title, "", "OTHER", run.startedAt, run.endedAt, "", null)

    private fun <T> success(data: T): BaseResponse<T> = BaseResponse(true, "OK", "", data)

    private suspend fun expectFailure(
        reason: String,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
            throw AssertionError("실패할 서버 관찰이 성공했습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
