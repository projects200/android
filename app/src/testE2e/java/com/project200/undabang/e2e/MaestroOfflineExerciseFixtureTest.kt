package com.project200.undabang.e2e

import android.app.Application
import android.util.AtomicFile
import androidx.room.Room
import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
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
class MaestroOfflineExerciseFixtureTest {
    private val context = RuntimeEnvironment.getApplication()
    private val api = mockk<ApiService>()
    private val preferences = mockk<PreferenceManager>()
    private val timers = mockk<TimerLocalDataSource>()
    private val name = "maestro-offline-exercise.db"
    private val memberId = "member"
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 10, 0),
            MaestroOfflineTimerInput(45, 75, "offline-step"),
        )
    private val request =
        MaestroFeatureRequest(
            "offline",
            "offline-exercise-create",
            run.id,
            memberId,
            JSONObject()
                .put("startedAt", run.startedAt.toString())
                .put("endedAt", run.endedAt.toString())
                .put("initialSeconds", "45")
                .put("updatedSeconds", "75")
                .put("stepName", "offline-step"),
        )
    private lateinit var database: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource
    private lateinit var observer: MaestroOfflineExerciseFixture
    private lateinit var fixtures: MaestroOfflineFixtures

    @Before
    fun setUp() {
        AtomicFile(File(context.filesDir, "maestro-offline-state.json")).delete()
        every { preferences.getMemberId() } returns memberId
        every { timers.currentMemberId() } returns memberId
        coEvery { timers.getCustomTimers(memberId) } returns emptyList()
        coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
        coEvery { api.getCustomTimerList() } returns success(GetCustomTimerListDTO(0, emptyList()))
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
        AtomicFile(File(context.filesDir, "maestro-offline-state.json")).delete()
    }

    private fun openDatabase() {
        database =
            Room.databaseBuilder(context, UndabangDatabase::class.java, name)
                .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .allowMainThreadQueries()
                .build()
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
        observer = MaestroOfflineExerciseFixture(database, preferences, api)
        fixtures = MaestroOfflineFixtures(api, context, timers, observer)
    }

    private fun reopen() {
        database.close()
        openDatabase()
    }

    @Test
    fun `실제 Room 생성 수정 삭제 저널과 소유 식별자는 매 재개방 뒤에도 유지된다`() =
        runTest {
            fixtures.prepare(request)
            val id = local.createRecord(ExerciseWriteInput(record())).recordId
            observeLocal("exercise-local-created")
            assertThat(snapshot().getString(MaestroOfflineExerciseFixture.LOCAL_ID)).isEqualTo(id)
            reopen()
            observeLocal("exercise-local-created-relaunch")
            coVerify(exactly = 1) { api.getExerciseList(any()) }

            settle(id, edited = false)
            remote(edited = false)
            fixtures.observeServer(request, "exercise-created-synced")
            assertThat(snapshot().getLong(MaestroOfflineExerciseFixture.SERVER_ID)).isEqualTo(101L)

            local.updateRecord(id, ExerciseWriteInput(record(edited = true)))
            observeLocal("exercise-local-updated")
            reopen()
            observeLocal("exercise-local-updated-relaunch")
            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.UPDATE_PENDING)
            settle(id, edited = true)
            remote(edited = true)
            fixtures.observeServer(request, "exercise-updated-synced")

            local.deleteRecord(id)
            observeLocal("exercise-local-deleted")
            reopen()
            observeLocal("exercise-local-deleted-relaunch")
            assertThat(database.exerciseRecordDao().getListByDate(memberId, run.startedAt.toLocalDate())).isEmpty()
            assertThat(database.exerciseSyncDao().get(memberId, id)?.serverId).isEqualTo(101L)
            removeAcknowledged(id)
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            fixtures.observeServer(request, "exercise-deleted-synced")
            assertThat(snapshot().getString(MaestroOfflineExerciseFixture.LOCAL_ID)).isEqualTo(id)
            assertThat(snapshot().getLong(MaestroOfflineExerciseFixture.SERVER_ID)).isEqualTo(101L)
            coVerify(exactly = 0) { api.postExerciseRecord(any()) }
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        }

    @Test
    fun `빈 응답과 이전 값 뒤 정확한 서버 값도 Room ACK 전에는 통과하지 않는다`() =
        runTest {
            val id = prepareCreated()
            var reads = 0
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } coAnswers {
                reads += 1
                when (reads) {
                    1 -> success(emptyList())
                    2 -> success(listOf(summary().copy(exerciseTitle = run.editedExerciseTitle)))
                    else -> success(listOf(summary()))
                }
            }
            coEvery { api.getExerciseRecordDetail(101) } coAnswers {
                if (reads >= 4) settle(id, edited = false)
                success(detail())
            }

            fixtures.observeServer(request, "exercise-created-synced")

            assertThat(reads).isAtLeast(4)
            assertThat(database.exerciseSyncDao().get(memberId, id)).isNull()
            assertThat(snapshot().getLong(MaestroOfflineExerciseFixture.SERVER_ID)).isEqualTo(101L)
        }

    @Test
    fun `정확한 서버 값만 있고 로컬 ACK가 없으면 제한 시간에 실패한다`() =
        runTest {
            val id = prepareCreated()
            remote(edited = false)

            expectFailure("offline_upload_not_observed") { fixtures.observeServer(request, "exercise-created-synced") }

            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(snapshot().has(MaestroOfflineExerciseFixture.SERVER_ID)).isFalse()
            coVerify(atLeast = 2) { api.getExerciseList(any()) }
        }

    @Test
    fun `로컬 ACK만 있고 서버 응답이 없거나 시간이 틀리면 성공하지 않는다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = false)
            expectFailure("offline_upload_not_observed") { fixtures.observeServer(request, "exercise-created-synced") }
            remote(edited = false)
            coEvery { api.getExerciseRecordDetail(101) } returns success(detail().copy(exerciseStartedAt = run.editedStartedAt))
            expectFailure("offline_upload_not_observed") { fixtures.observeServer(request, "exercise-created-synced") }
            assertThat(snapshot().has(MaestroOfflineExerciseFixture.SERVER_ID)).isFalse()
        }

    @Test
    fun `중복 제목이나 중복 서버 ID와 잘못된 날짜는 성공이 아니다`() =
        runTest {
            prepareCreated()
            val responses =
                listOf(
                    listOf(summary(), summary().copy(exerciseId = 102)),
                    listOf(summary(), summary()),
                    listOf(summary().copy(exerciseStartedAt = run.startedAt.minusDays(1))),
                )
            for (rows in responses) {
                coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(rows)
                expectFailure("offline_exercise_server_mismatch") { fixtures.observeServer(request, "exercise-created-synced") }
            }
        }

    @Test
    fun `재개방 뒤 localId 교체나 다른 계정은 관찰과 삭제를 허가하지 않는다`() =
        runTest {
            val id = prepareCreated()
            removeAcknowledged(id)
            local.createRecord(ExerciseWriteInput(record()))
            reopen()
            expectFailure("offline_exercise_identity_changed") { observeLocal("exercise-local-created-relaunch") }
            every { preferences.getMemberId() } returns "other-member"
            expectFailure("member_mismatch") { observeLocal("exercise-local-created-relaunch") }
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        }

    @Test
    fun `수정과 삭제 관찰은 캡처한 서버 ID 없이는 부재를 성공으로 보지 않는다`() =
        runTest {
            prepareCreated()
            expectFailure("offline_exercise_identity_changed") { fixtures.observeServer(request, "exercise-deleted-synced") }
            expectFailure("offline_exercise_identity_changed") { fixtures.observeServer(request, "exercise-updated-synced") }
        }

    @Test
    fun `저장한 localId가 없으면 서버 부재와 로컬 부재를 삭제 완료 증거로 쓰지 않는다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = false)
            remote(edited = false)
            fixtures.observeServer(request, "exercise-created-synced")
            removeAcknowledged(id)
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())
            val saved = snapshot().apply { remove(MaestroOfflineExerciseFixture.LOCAL_ID) }
            File(context.filesDir, "maestro-offline-state.json").writeText(saved.toString())

            expectFailure("offline_exercise_identity_changed") { fixtures.observeServer(request, "exercise-deleted-synced") }
        }

    @Test
    fun `수정은 같은 제목의 다른 서버 ID를 받아들이지 않는다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = false)
            remote(edited = false)
            fixtures.observeServer(request, "exercise-created-synced")
            local.updateRecord(id, ExerciseWriteInput(record(edited = true)))
            observeLocal("exercise-local-updated")
            settle(id, edited = true)
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns
                success(listOf(summary(edited = true).copy(exerciseId = 102)))

            expectFailure("offline_exercise_identity_changed") { fixtures.observeServer(request, "exercise-updated-synced") }
        }

    @Test
    fun `서버 삭제만으로는 삭제 저널이 남은 로컬 상태를 완료하지 않는다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = false)
            remote(edited = false)
            fixtures.observeServer(request, "exercise-created-synced")
            local.updateRecord(id, ExerciseWriteInput(record(edited = true)))
            settle(id, edited = true)
            local.deleteRecord(id)
            observeLocal("exercise-local-deleted")
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(emptyList())

            expectFailure("offline_upload_not_observed") { fixtures.observeServer(request, "exercise-deleted-synced") }

            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.DELETE_PENDING)
        }

    @Test
    fun `정리는 수정된 소유 pending과 in-flight 사진을 보존하고 서버 삭제를 하지 않는다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = false)
            remote(edited = false)
            fixtures.observeServer(request, "exercise-created-synced")
            local.updateRecord(id, ExerciseWriteInput(record(edited = true)))
            observeLocal("exercise-local-updated")
            remote(edited = true)
            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }
            val journal = checkNotNull(database.exerciseSyncDao().get(memberId, id))
            database.exerciseSyncDao().saveSyncState(journal.copy(inFlightOperationId = "uncertain"))
            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }
            settle(id, edited = true)
            database.exerciseImageDao().save(
                ExerciseImageEntity(
                    memberId,
                    id,
                    "photo",
                    null,
                    "/private/photo.jpg",
                    null,
                    ExerciseImageState.ADD_LOCAL,
                    "photo-key",
                    0,
                ),
            )
            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }
            val image = database.exerciseImageDao().getByRecord(memberId, id).single()
            database.exerciseImageDao().save(image.copy(state = ExerciseImageState.SYNCED, serverPictureId = 17))
            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }
            assertThat(snapshot().getString(MaestroOfflineExerciseFixture.LOCAL_ID)).isEqualTo(id)
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        }

    @Test
    fun `불확실한 미전송 생성도 직접 정리하지 않고 private 증거를 남긴다`() =
        runTest {
            val id = prepareCreated()
            val journal = checkNotNull(database.exerciseSyncDao().get(memberId, id))
            database.exerciseSyncDao().saveSyncState(journal.copy(recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED))

            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }

            assertThat(database.exerciseSyncDao().get(memberId, id)).isNotNull()
            assertThat(snapshot().getString(MaestroOfflineExerciseFixture.LOCAL_ID)).isEqualTo(id)
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        }

    @Test
    fun `일반 pending은 안전한 수정 소유 기록의 기존 서버 정리를 막지 않는다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = true)
            local.createRecord(ExerciseWriteInput(record().copy(title = "personal")))
            remote(edited = true)
            coEvery { api.deleteExerciseRecord(101) } returns success<ExerciseIdDto?>(null)

            fixtures.cleanup(request)

            coVerify(exactly = 1) { api.deleteExerciseRecord(101) }
            assertThat(database.exerciseSyncDao().getByMember(memberId)).hasSize(1)
            assertThat(database.exerciseSyncDao().getByMember(memberId).single().localId).isNotEqualTo(id)
        }

    @Test
    fun `목록과 상세의 값이 갈리면 같은 localId와 저널만으로 성공하지 않는다`() =
        runTest {
            val id = prepareCreated()
            val detail = checkNotNull(database.exerciseRecordDao().getDetailByLocalId(memberId, id))
            database.exerciseRecordDao().upsertDetail(detail.copy(startedAt = run.editedStartedAt))

            expectFailure("offline_exercise_local_mismatch") { observeLocal("exercise-local-created-relaunch") }

            assertThat(snapshot().getString(MaestroOfflineExerciseFixture.LOCAL_ID)).isEqualTo(id)
            coVerify(exactly = 1) { api.getExerciseList(any()) }
        }

    @Test
    fun `서버에 행이 남으면 로컬 제거만으로 삭제 동기화를 통과하지 않는다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = false)
            remote(edited = false)
            fixtures.observeServer(request, "exercise-created-synced")
            removeAcknowledged(id)

            expectFailure("offline_upload_not_observed") { fixtures.observeServer(request, "exercise-deleted-synced") }

            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        }

    @Test
    fun `관찰 전 상세만 남은 불확실 생성도 정리에서 누락하지 않는다`() =
        runTest {
            fixtures.prepare(request)
            val id = local.createRecord(ExerciseWriteInput(record())).recordId
            database.exerciseRecordDao().deleteConfirmedList(memberId, id)

            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }

            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)).isNotNull()
            assertThat(database.exerciseSyncDao().get(memberId, id)).isNotNull()
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        }

    @Test
    fun `서버 상세 조회 중 pending으로 바뀌면 삭제 직전 재검사에서 보존한다`() =
        runTest {
            val id = prepareCreated()
            settle(id, edited = false)
            remote(edited = false)
            coEvery { api.getExerciseRecordDetail(101) } coAnswers {
                local.updateRecord(id, ExerciseWriteInput(record(edited = true)))
                success(detail())
            }

            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }

            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.UPDATE_PENDING)
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        }

    private suspend fun prepareCreated(): String {
        fixtures.prepare(request)
        val id = local.createRecord(ExerciseWriteInput(record())).recordId
        observeLocal("exercise-local-created")
        return id
    }

    private suspend fun observeLocal(stage: String) {
        fixtures.observe(request.copy(parameters = JSONObject(request.parameters.toString()).put("stage", stage)), stage)
    }

    private fun snapshot() = JSONObject(File(context.filesDir, "maestro-offline-state.json").readText())

    private fun record(edited: Boolean = false) =
        ExerciseRecord(
            if (edited) run.editedExerciseTitle else run.exerciseTitle,
            "",
            "OTHER",
            if (edited) run.editedStartedAt else run.startedAt,
            run.endedAt,
            "",
            null,
        )

    // 테스트만 ACK 투영을 구성합니다. 실제 E2E observer에는 이 쓰기 경로가 없습니다.
    private suspend fun settle(
        id: String,
        edited: Boolean,
    ) {
        val record = record(edited)
        database.withTransaction {
            database.exerciseRecordDao().upsertListItems(
                listOf(
                    ExerciseListItemEntity(
                        memberId = memberId,
                        localId = id,
                        serverId = 101,
                        syncState = SyncState.SYNCED,
                        date = run.startedAt.toLocalDate(),
                        sortOrder = 0,
                        title = record.title,
                        personalType = "OTHER",
                        startedAt = record.startedAt,
                        endedAt = record.endedAt,
                        thumbnailUrls = null,
                        serverVersion = 1,
                        confirmedDate = run.startedAt.toLocalDate(),
                    ),
                ),
            )
            database.exerciseRecordDao().upsertDetail(
                ExerciseRecordDetailEntity(
                    memberId = memberId,
                    localId = id,
                    serverId = 101,
                    syncState = SyncState.SYNCED,
                    title = record.title,
                    detail = "",
                    personalType = "OTHER",
                    startedAt = record.startedAt,
                    endedAt = record.endedAt,
                    location = "",
                    pictures = null,
                    serverVersion = 1,
                    confirmedDate = run.startedAt.toLocalDate(),
                ),
            )
            database.exerciseSyncDao().deleteApplied(memberId, id)
        }
    }

    private suspend fun removeAcknowledged(id: String) {
        database.withTransaction {
            database.exerciseRecordDao().deleteConfirmedList(memberId, id)
            database.exerciseRecordDao().deleteConfirmedDetail(memberId, id)
            database.exerciseSyncDao().deleteApplied(memberId, id)
        }
    }

    private fun remote(edited: Boolean) {
        coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns success(listOf(summary(edited)))
        coEvery { api.getExerciseRecordDetail(101) } returns success(detail(edited))
    }

    private fun summary(edited: Boolean = false): GetExerciseRecordListDto {
        val record = record(edited)
        return GetExerciseRecordListDto(101, record.title, "OTHER", record.startedAt, record.endedAt, null)
    }

    private fun detail(edited: Boolean = false): GetExerciseRecordData {
        val record = record(edited)
        return GetExerciseRecordData(record.title, "", "OTHER", record.startedAt, record.endedAt, "", null)
    }

    private fun <T> success(data: T): BaseResponse<T> = BaseResponse(true, "OK", "", data)

    private suspend fun expectFailure(
        reason: String,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
            throw AssertionError("관찰 또는 안전 정리가 실패해야 합니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
