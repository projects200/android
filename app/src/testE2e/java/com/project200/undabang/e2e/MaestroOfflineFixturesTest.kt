package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.FeatureFlagDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.CustomTimerDetailStepDTO
import com.project200.data.dto.CustomTimerSummaryDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.domain.model.UpdateInfo
import com.project200.domain.repository.AppUpdateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import java.time.LocalDateTime

class MaestroOfflineFixturesTest {
    private val api = mockk<ApiService>()
    private val flags = mockk<FeatureFlagDataSource>()
    private val updates = mockk<AppUpdateRepository>()
    private val fixtures = MaestroOfflineFixtures(api, flags, updates)
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 10, 15, 9, 0),
            LocalDateTime.of(2026, 10, 15, 10, 0),
        )

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
            coVerify(exactly = 0) { api.deleteCustomTimer(any()) }
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
            coEvery { api.deleteCustomTimer(1) } returns success<Unit?>(null)

            fixtures.clean(run)

            coVerify(exactly = 1) { api.deleteExerciseRecord(1) }
            coVerify(exactly = 1) { api.deleteCustomTimer(1) }
            coVerify(exactly = 0) { api.deleteExerciseRecord(2) }
            coVerify(exactly = 0) { api.deleteCustomTimer(2) }
            coVerify(exactly = 0) { api.deleteCustomTimer(3) }
            coVerify(exactly = 0) { api.deleteSimpleTimer(any()) }
        }

    @Test
    fun `모든 목록 조회 전에 삭제하지 않고 뒤 조회 실패 시 후보도 보존한다`() =
        runTest {
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(listOf(exercise(1, run.exerciseTitle)))
            coEvery { api.getCustomTimerList() } returns BaseResponse(false, "SERVER_ERROR", "")

            expectFailure("fixture_read_failed") { fixtures.clean(run) }

            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
            coVerify(exactly = 0) { api.deleteCustomTimer(any()) }
        }

    @Test
    fun `상세가 일반 제목으로 바뀐 타이머는 정리하지 않는다`() =
        runTest {
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList(run.createdTimer)
            coEvery { api.getCustomTimer(1) } returns success(timer(1, "personal timer", 45))

            fixtures.clean(run)

            coVerify(exactly = 0) { api.deleteCustomTimer(any()) }
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
            coEvery { api.deleteCustomTimer(1) } returns BaseResponse(false, "SERVER_ERROR", "private diagnostic")

            expectFailure("fixture_delete_failed") { fixtures.clean(run) }
        }

    @Test
    fun `공통 feature 준비는 실행별 정리 뒤 실제 플래그를 검사하고 정리는 같은 요청을 재사용한다`() =
        runTest {
            val parameters = mockk<JSONObject>()
            every { parameters.keys() } answers { mutableListOf("startedAt", "endedAt").iterator() }
            every { parameters.getString("startedAt") } returns run.startedAt.toString()
            every { parameters.getString("endedAt") } returns run.endedAt.toString()
            val request = MaestroFeatureRequest("offline", "offline-exercise-create", run.id, "member", parameters)
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            coEvery { api.getCustomTimerList() } returns timerList()
            coEvery { updates.getUpdateInfo() } returns Result.success(mockk<UpdateInfo>())
            every { flags.isOfflineCacheEnabled() } returns true

            assertThat(fixtures.prepare(request)).isEmpty()
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

    private fun timerList(vararg names: String) =
        success(GetCustomTimerListDTO(names.size, names.mapIndexed { index, name -> CustomTimerSummaryDTO(index.toLong() + 1, name) }))

    private fun timer(
        id: Long,
        name: String,
        seconds: Int,
    ) = GetCustomTimerDetailDTO(id, name, 1, listOf(CustomTimerDetailStepDTO(100 + id, "offline-step", 0, seconds)))

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
