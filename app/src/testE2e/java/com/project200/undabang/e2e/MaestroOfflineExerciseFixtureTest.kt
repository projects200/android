package com.project200.undabang.e2e

import android.app.Application
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
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
    private val name = "maestro-offline-exercise.db"
    private val preferences = mockk<PreferenceManager>()
    private val run =
        MaestroOfflineRun(
            "0123456789abcdef0123456789abcdef",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 10, 0),
            MaestroOfflineTimerInput(45, 75, "offline-step"),
        )
    private lateinit var database: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource
    private lateinit var observer: MaestroOfflineExerciseFixture
    private lateinit var saved: JSONObject

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        every { preferences.getMemberId() } returns MEMBER
        File(context.filesDir, "maestro-offline-state.json").delete()
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
        File(context.filesDir, "maestro-offline-state.json").delete()
    }

    private fun openDatabase() {
        database =
            Room.databaseBuilder(context, UndabangDatabase::class.java, name)
                .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .allowMainThreadQueries()
                .build()
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
        observer = MaestroOfflineExerciseFixture(database, preferences)
    }

    private fun reopen() {
        database.close()
        openDatabase()
    }

    private suspend fun create(): String {
        saved = JSONObject().put("baselineExerciseLocalIds", observer.baseline(MEMBER, run))
        return local.createRecord(input(), MEMBER).recordId
    }

    private fun input() = ExerciseWriteInput(ExerciseRecord(run.exerciseTitle, "", "OTHER", run.startedAt, run.endedAt, "", null))

    private suspend fun observe(stage: String) = observer.observeLocal(MEMBER, run, saved, stage)

    @Test
    fun `실제 로컬 생성은 null 서버 ID 대기로 저장하고 재개방 후 같은 ID를 유지하며 실제 삭제 후 완전히 사라진다`() =
        runTest {
            val id = create()
            val journal = database.exerciseSyncDao().get(MEMBER, id)
            observe("exercise-local-created")
            assertThat(saved.getString("exerciseLocalId")).isEqualTo(id)
            assertThat(journal?.serverId).isNull()
            assertThat(database.exerciseRecordDao().getListItem(MEMBER, id)?.serverId).isNull()
            reopen()
            observe("exercise-local-created-relaunch")
            assertThat(database.exerciseSyncDao().get(MEMBER, id)).isEqualTo(journal)
            expectFailure("offline_exercise_cleanup_unsafe") { observer.requireSafeCleanup(MEMBER, run, saved) }
            local.deleteRecord(id, MEMBER)
            reopen()
            observe("exercise-local-deleted")
            observer.requireSafeCleanup(MEMBER, run, saved)
            assertThat(database.exerciseRecordDao().getListItem(MEMBER, id)).isNull()
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)).isNull()
            assertThat(database.exerciseSyncDao().get(MEMBER, id)).isNull()
            assertThat(database.exerciseImageDao().getByRecord(MEMBER, id)).isEmpty()
        }

    @Test
    fun `재실행 전에 원 ID를 다른 정상 생성으로 교체해도 같은 제목으로 통과하지 않는다`() =
        runTest {
            val id = create()
            observe("exercise-local-created")
            local.deleteRecord(id, MEMBER)
            val replacement = local.createRecord(input(), MEMBER).recordId
            reopen()
            expectFailure("offline_exercise_identity_changed") { observe("exercise-local-created-relaunch") }
            assertThat(saved.getString("exerciseLocalId")).isEqualTo(id)
            assertThat(database.exerciseSyncDao().get(MEMBER, replacement)).isNotNull()
        }

    @Test
    fun `기준 행과 중복 제목과 날짜 밖 상세만 남은 행을 새 생성으로 인정하지 않는다`() =
        runTest {
            val id = create()
            val baseline = JSONObject().put("baselineExerciseLocalIds", observer.baseline(MEMBER, run))
            expectFailure("offline_exercise_local_mismatch") {
                observer.observeLocal(MEMBER, run, baseline, "exercise-local-created")
            }
            val duplicate = local.createRecord(input(), MEMBER).recordId
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-created") }
            local.deleteRecord(duplicate, MEMBER)
            val orphan =
                database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)!!.copy(
                    localId = "orphan",
                    startedAt = run.startedAt.minusDays(1),
                )
            database.exerciseRecordDao().upsertDetail(orphan)
            reopen()
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-created") }
            expectFailure("offline_exercise_cleanup_unsafe") { observer.requireSafeCleanup(MEMBER, run, saved) }
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER, "orphan")).isEqualTo(orphan)
        }

    @Test
    fun `제목 날짜 시간 서버 ID 확인 날짜와 저널의 잘못된 값은 캡처 전에 거절하며 관찰은 변경하지 않는다`() =
        runTest {
            val id = create()
            val corruptions =
                listOf(
                    "UPDATE exercise_list_item SET title = 'wrong' WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_list_item SET date = '2025-12-31' WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_record_detail SET endedAt = '2026-01-01T11:00' WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_list_item SET serverId = 7 WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_record_detail SET confirmedDate = '2026-01-01' WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_sync SET revision = 0 WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_sync SET operationId = '' WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_sync SET createRequestId = NULL WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_sync SET recoveryState = 'RECOVERY_REQUIRED' WHERE memberId = ? AND localId = ?",
                    "UPDATE exercise_sync SET inFlightBody = 'uncertain' WHERE memberId = ? AND localId = ?",
                )
            val item = database.exerciseRecordDao().getListItem(MEMBER, id)!!
            val detail = database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)!!
            val journal = database.exerciseSyncDao().get(MEMBER, id)!!
            for (sql in corruptions) {
                database.openHelper.writableDatabase.execSQL(sql, arrayOf(MEMBER, id))
                val beforeItem = database.exerciseRecordDao().getListItem(MEMBER, id)
                val beforeDetail = database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)
                val beforeJournal = database.exerciseSyncDao().get(MEMBER, id)
                expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-created") }
                assertThat(saved.has("exerciseLocalId")).isFalse()
                assertThat(database.exerciseRecordDao().getListItem(MEMBER, id)).isEqualTo(beforeItem)
                assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)).isEqualTo(beforeDetail)
                assertThat(database.exerciseSyncDao().get(MEMBER, id)).isEqualTo(beforeJournal)
                database.exerciseRecordDao().upsertListItems(listOf(item))
                database.exerciseRecordDao().upsertDetail(detail)
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM exercise_sync WHERE memberId = ? AND localId = ?",
                    arrayOf(MEMBER, id),
                )
                database.exerciseSyncDao().saveDesired(journal)
            }
        }

    @Test
    fun `알 수 없는 전송 중 생성은 실제 삭제의 tombstone도 부재나 안전 정리로 인정하지 않는다`() =
        runTest {
            val id = create()
            observe("exercise-local-created")
            val journal = database.exerciseSyncDao().get(MEMBER, id)!!
            assertThat(
                database.exerciseSyncDao().claim(
                    MEMBER,
                    id,
                    journal.revision!!,
                    journal.operationId!!,
                    journal.createRequestId!!,
                    "body",
                    null,
                ),
            ).isTrue()
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-created-relaunch") }
            local.deleteRecord(id, MEMBER)
            reopen()
            val tombstone = database.exerciseSyncDao().get(MEMBER, id)
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-deleted") }
            expectFailure("offline_exercise_cleanup_unsafe") { observer.requireSafeCleanup(MEMBER, run, saved) }
            assertThat(database.exerciseSyncDao().get(MEMBER, id)).isEqualTo(tombstone)
        }

    @Test
    fun `삭제 뒤 저널이나 사진이나 상세만 남아도 재개방 후 부재를 인정하지 않는다`() =
        runTest {
            val id = create()
            observe("exercise-local-created")
            val journal = database.exerciseSyncDao().get(MEMBER, id)!!
            val detail = database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)!!
            local.deleteRecord(id, MEMBER)
            database.exerciseSyncDao().saveDesired(journal)
            reopen()
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-deleted") }
            expectFailure("offline_exercise_cleanup_unsafe") { observer.requireSafeCleanup(MEMBER, run, saved) }
            assertThat(database.exerciseSyncDao().get(MEMBER, id)).isEqualTo(journal)
            database.exerciseSyncDao().deleteMatching(MEMBER, id, journal.operationId, journal.revision)
            val image = ExerciseImageEntity(MEMBER, id, "photo", null, "/private/photo", null, ExerciseImageState.ADD_PENDING, "request", 0)
            database.exerciseImageDao().save(image)
            reopen()
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-deleted") }
            expectFailure("offline_exercise_cleanup_unsafe") { observer.requireSafeCleanup(MEMBER, run, saved) }
            assertThat(database.exerciseImageDao().getByRecord(MEMBER, id)).containsExactly(image)
            database.exerciseRecordDao().upsertDetail(detail.copy(localId = "orphan"))
            reopen()
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-deleted") }
        }

    @Test
    fun `다른 회원과 미캡처 삭제는 거절하고 원 회원의 대기 행을 보존한다`() =
        runTest {
            val id = create()
            expectFailure("offline_exercise_identity_changed") { observe("exercise-local-deleted") }
            every { preferences.getMemberId() } returns "other"
            expectFailure("member_mismatch") { observe("exercise-local-created") }
            expectFailure("member_mismatch") { observer.requireSafeCleanup(MEMBER, run, saved) }
            assertThat(database.exerciseSyncDao().get(MEMBER, id)).isNotNull()
        }

    @Test
    fun `삭제 뒤 원 ID가 없어도 날짜 밖 같은 제목의 상세는 부재로 통과하지 않는다`() =
        runTest {
            val id = create()
            observe("exercise-local-created")
            val detail = database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)!!
            local.deleteRecord(id, MEMBER)
            val orphan = detail.copy(localId = "orphan", startedAt = run.startedAt.minusDays(1))
            database.exerciseRecordDao().upsertDetail(orphan)
            reopen()
            expectFailure("offline_exercise_local_mismatch") { observe("exercise-local-deleted") }
            expectFailure("offline_exercise_cleanup_unsafe") { observer.requireSafeCleanup(MEMBER, run, saved) }
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER, "orphan")).isEqualTo(orphan)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER, id)).isNull()
        }

    @Test
    fun `캡처 전 불확실 운동도 빈 기준 상태와 달리 안전 정리를 거절한다`() =
        runTest {
            observer.requireSafeCleanup(MEMBER, run, null)
            val id = create()
            val journal = database.exerciseSyncDao().get(MEMBER, id)
            reopen()
            expectFailure("offline_exercise_cleanup_unsafe") { observer.requireSafeCleanup(MEMBER, run, null) }
            assertThat(database.exerciseSyncDao().get(MEMBER, id)).isEqualTo(journal)
        }

    @Test
    fun `목록 조회 뒤 새 대기가 생기면 원격 DELETE 직전 재검사로 삭제를 막는다`() =
        runTest {
            val api = mockk<ApiService>()
            val timers = mockk<TimerLocalDataSource>()
            val fixture = MaestroOfflineFixtures(api, context, timers, observer)
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns
                BaseResponse(
                    true,
                    "OK",
                    "",
                    listOf(GetExerciseRecordListDto(7, run.exerciseTitle, "OTHER", run.startedAt, run.endedAt, null)),
                )
            coEvery { api.getCustomTimerList() } returns BaseResponse(true, "OK", "", GetCustomTimerListDTO(0, emptyList()))
            var id: String? = null
            coEvery { api.getExerciseRecordDetail(7) } coAnswers {
                id = local.createRecord(input(), MEMBER).recordId
                BaseResponse(true, "OK", "", GetExerciseRecordData(run.exerciseTitle, "", "OTHER", run.startedAt, run.endedAt, "", null))
            }
            expectFailure("offline_exercise_cleanup_unsafe") { fixture.clean(MEMBER, run) }
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
            reopen()
            assertThat(database.exerciseSyncDao().get(MEMBER, checkNotNull(id))).isNotNull()
        }

    @Test
    fun `실제 fixture 정리는 대기 운동이 있으면 서버 삭제 전에 중단하고 UI 취소 뒤에만 허용한다`() =
        runTest {
            val api = mockk<ApiService>()
            val timers = mockk<TimerLocalDataSource>()
            every { timers.currentMemberId() } returns MEMBER
            coEvery { timers.getCustomTimers(MEMBER) } returns emptyList()
            coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns BaseResponse(true, "OK", "", emptyList())
            coEvery { api.getCustomTimerList() } returns BaseResponse(true, "OK", "", GetCustomTimerListDTO(0, emptyList()))
            val fixtures = MaestroOfflineFixtures(api, context, timers, observer)
            val request =
                MaestroFeatureRequest(
                    "offline",
                    "offline-exercise-create",
                    run.id,
                    MEMBER,
                    JSONObject()
                        .put("startedAt", run.startedAt.toString())
                        .put("endedAt", run.endedAt.toString())
                        .put("initialSeconds", "45")
                        .put("updatedSeconds", "75")
                        .put("stepName", "offline-step"),
                )
            fixtures.prepare(request)
            val id = local.createRecord(input(), MEMBER).recordId
            expectFailure("offline_exercise_cleanup_unsafe") { fixtures.cleanup(request) }
            fixtures.observe(request, "exercise-local-created")
            val snapshot = File(context.filesDir, "maestro-offline-state.json").readText()
            reopen()
            val reopenedFixtures = MaestroOfflineFixtures(api, context, timers, observer)
            reopenedFixtures.observe(request, "exercise-local-created-relaunch")
            expectFailure("offline_exercise_cleanup_unsafe") { reopenedFixtures.cleanup(request) }
            assertThat(File(context.filesDir, "maestro-offline-state.json").readText()).isEqualTo(snapshot)
            coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
            coVerify(exactly = 0) { api.deleteCustomTimer(any(), any()) }
            local.deleteRecord(id, MEMBER)
            reopen()
            MaestroOfflineFixtures(api, context, timers, observer).cleanup(request)
            assertThat(database.exerciseSyncDao().get(MEMBER, id)).isNull()
        }

    private suspend fun expectFailure(
        reason: String,
        operation: suspend () -> Unit,
    ) {
        val failure = runCatching { operation() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(MaestroSetupFailure::class.java)
        assertThat((failure as MaestroSetupFailure).reason).isEqualTo(reason)
    }

    companion object {
        private const val MEMBER = "member"
    }
}
