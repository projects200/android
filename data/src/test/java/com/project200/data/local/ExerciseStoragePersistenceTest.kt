package com.project200.data.local

import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.impl.RoomExerciseCacheCleaner
import com.project200.data.impl.RoomSessionDataCleaner
import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseStoragePersistenceTest {
    private val context = RuntimeEnvironment.getApplication()
    private val name = "exercise-storage-persistence.db"
    private val date = LocalDate.parse("2025-01-10")
    private val preferences = mockk<PreferenceManager>(relaxed = true)
    private lateinit var database: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource

    @Before
    fun setUp() {
        every { preferences.getMemberId() } returns "member-a"
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    private fun openDatabase() {
        database =
            Room.databaseBuilder(context, UndabangDatabase::class.java, name)
                .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .allowMainThreadQueries()
                .build()
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
    }

    private fun reopen() {
        database.close()
        openDatabase()
    }

    @Test
    fun `저널 목록 상세 사진은 함께 저장하고 nullable 서버 ID와 경로를 재개방한다`() =
        runTest {
            val image = image()
            local.persistPending(journal(), item(), detail(), listOf(image))
            reopen()
            assertThat(database.exerciseSyncDao().get("member-a", "record")).isEqualTo(journal())
            assertThat(database.exerciseRecordDao().getListItem("member-a", "record")).isEqualTo(item())
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "record")).isEqualTo(detail())
            assertThat(database.exerciseImageDao().getByRecord("member-a", "record")).containsExactly(image)
            assertThat(database.exerciseSyncDao().getSendable("member-a")).containsExactly(journal())
        }

    @Test
    fun `다른 계정 기록 상태 날짜의 조합은 어떤 행도 저장하지 않는다`() =
        runTest {
            val attempts =
                listOf<suspend () -> Unit>(
                    { local.persistPending(journal(), item().copy(memberId = "member-b"), detail()) },
                    { local.persistPending(journal(), item(), detail().copy(localId = "other")) },
                    { local.persistPending(journal(), item().copy(syncState = SyncState.UPDATE_PENDING), detail()) },
                    { local.persistPending(journal(), item().copy(date = date.plusDays(1)), detail()) },
                    { local.persistPending(journal(), item(), detail(), listOf(image().copy(memberId = "member-b"))) },
                    { local.persistPending(journal(), item(), detail().copy(serverId = 7L)) },
                )
            attempts.forEach { attempt ->
                assertThat(runCatching { attempt() }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
                assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
                assertThat(database.exerciseRecordDao().getListItem("member-a", "record")).isNull()
                assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "record")).isNull()
                assertThat(database.exerciseImageDao().getByRecord("member-a", "record")).isEmpty()
                assertThat(database.exerciseSyncDao().getByMember("member-b")).isEmpty()
            }
        }

    @Test
    fun `실제 SQLite 사진 쓰기 오류는 앞서 변경한 저널과 투영과 사진까지 롤백한다`() =
        runTest {
            local.persistPending(journal(), item(), detail(), listOf(image()))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_image BEFORE INSERT ON exercise_image WHEN NEW.localId = 'reject' " +
                    "BEGIN SELECT RAISE(ABORT, 'TEST_PERSISTENCE_FAILURE'); END",
            )
            val failure =
                runCatching {
                    local.persistPending(
                        journal().copy(revision = 2, operationId = "edit-two"),
                        item().copy(title = "수정"),
                        detail().copy(title = "수정"),
                        listOf(image().copy(localFilePath = "/app/updated.jpg"), image().copy(localId = "reject")),
                    )
                }.exceptionOrNull()
            assertThat(failure).isInstanceOf(SQLiteException::class.java)
            reopen()
            assertThat(database.exerciseSyncDao().get("member-a", "record")).isEqualTo(journal())
            assertThat(database.exerciseRecordDao().getListItem("member-a", "record")).isEqualTo(item())
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "record")).isEqualTo(detail())
            assertThat(database.exerciseImageDao().getByRecord("member-a", "record")).containsExactly(image())
        }

    @Test
    fun `전송 중 재편집과 재개방은 이전 요청 원문을 고정하고 조건이 다른 완료는 무시한다`() =
        runTest {
            local.persistPending(journal(), item(), detail())
            assertThat(database.exerciseSyncDao().claim("member-a", "record", 1, "edit-one", "wrong-key", "body", null)).isFalse()
            var dao = database.exerciseSyncDao()
            assertThat(dao.claim("member-a", "record", 1, "edit-one", "create-one", "body-A", null)).isTrue()
            assertThat(dao.claim("member-a", "record", 1, "edit-one", "create-one", "replacement", null)).isFalse()
            local.persistPending(
                journal().copy(revision = 2, operationId = "edit-two", inFlightBody = "body-B", inFlightRequestId = "replacement"),
                item().copy(title = "최신 B"),
                detail().copy(title = "최신 B"),
            )
            reopen()
            dao = database.exerciseSyncDao()
            val saved = dao.get("member-a", "record")!!
            assertThat(saved.revision).isEqualTo(2L)
            assertThat(saved.operationId).isEqualTo("edit-two")
            assertThat(saved.inFlightRevision).isEqualTo(1L)
            assertThat(saved.inFlightOperation).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(saved.inFlightOperationId).isEqualTo("edit-one")
            assertThat(saved.inFlightRequestId).isEqualTo("create-one")
            assertThat(saved.inFlightBody).isEqualTo("body-A")
            assertThat(saved.inFlightBaseVersion).isNull()
            assertThat(dao.clearMatchingInFlight("member-b", "record", "edit-one", 1, "create-one")).isEqualTo(0)
            assertThat(dao.clearMatchingInFlight("member-a", "record", "edit-two", 1, "create-one")).isEqualTo(0)
            assertThat(dao.clearMatchingInFlight("member-a", "record", "edit-one", 2, "create-one")).isEqualTo(0)
            assertThat(dao.clearMatchingInFlight("member-a", "record", "edit-one", 1, "wrong")).isEqualTo(0)
            assertThat(dao.deleteMatching("member-a", "record", "edit-two", 2)).isEqualTo(0)
            assertThat(dao.get("member-a", "record")).isEqualTo(saved)
            assertThat(dao.clearMatchingInFlight("member-a", "record", "edit-one", 1, "create-one")).isEqualTo(1)
            assertThat(dao.get("member-a", "record")?.revision).isEqualTo(2L)
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "record")?.title).isEqualTo("최신 B")
        }

    @Test
    fun `같은 작업 ID의 최신 revision은 전송 해제 후에도 이전 revision의 늦은 삭제로 지우지 않는다`() =
        runTest {
            local.persistPending(journal(), item(), detail())
            val originalDao = database.exerciseSyncDao()
            assertThat(originalDao.claim("member-a", "record", 1, "edit-one", "create-one", "body-A", null)).isTrue()
            val latest = journal().copy(revision = 2)
            local.persistPending(latest, item().copy(title = "최신 B"), detail().copy(title = "최신 B"))
            assertThat(originalDao.deleteMatching("member-a", "record", "edit-one", 2)).isEqualTo(0)
            assertThat(originalDao.clearMatchingInFlight("member-a", "record", "edit-one", 1, "create-one")).isEqualTo(1)
            reopen()
            val dao = database.exerciseSyncDao()
            assertThat(dao.deleteMatching("member-a", "record", "edit-one", 1)).isEqualTo(0)
            assertThat(dao.get("member-a", "record")).isEqualTo(latest)
            assertThat(dao.deleteMatching("member-a", "record", "edit-one", 2)).isEqualTo(1)
            assertThat(dao.get("member-a", "record")).isNull()
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "record")?.title).isEqualTo("최신 B")
        }

    @Test
    fun `편집 기준 버전과 복구 상태를 보존하고 이전 revision과 생성 키 변경을 거부한다`() =
        runTest {
            val current =
                journal().copy(
                    serverId = 7,
                    operation = SyncState.UPDATE_PENDING,
                    revision = 2,
                    operationId = "edit-two",
                    serverVersion = 42,
                    confirmedDate = date.minusDays(1),
                    recoveryState = ExerciseRecoveryState.RETRYABLE,
                    lastError = ExerciseSyncError.NETWORK_ERROR,
                )
            val dao = database.exerciseSyncDao()
            dao.saveDesired(current)
            assertThat(runCatching { dao.saveDesired(current.copy(revision = 1)) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(runCatching { dao.saveDesired(current.copy(createRequestId = "changed")) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(dao.claim("member-a", "record", 2, "edit-two", "edit-request", "body", 42)).isTrue()
            reopen()
            val stored = database.exerciseSyncDao().get("member-a", "record")!!
            assertThat(stored.serverVersion).isEqualTo(42L)
            assertThat(stored.confirmedDate).isEqualTo(date.minusDays(1))
            assertThat(stored.inFlightBaseVersion).isEqualTo(42L)
            assertThat(stored.recoveryState).isEqualTo(ExerciseRecoveryState.RETRYABLE)
            assertThat(stored.lastError).isEqualTo(ExerciseSyncError.NETWORK_ERROR)
            database.exerciseSyncDao().saveDesired(stored.copy(recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED))
            assertThat(database.exerciseSyncDao().getSendable("member-a")).isEmpty()
            assertThat(database.exerciseSyncDao().claim("member-a", "record", 2, "edit-two", "other", "body", 42)).isFalse()
        }

    @Test
    fun `동일한 로컬 ID를 가진 계정의 저널 사진 조회 변경 삭제는 서로 격리한다`() =
        runTest {
            for (member in listOf("member-a", "member-b")) {
                every { preferences.getMemberId() } returns member
                local.persistPending(journal(member), item(member), detail(member), listOf(image(member)))
                database.exerciseImageDao().save(
                    image(member).copy(localId = "remote", serverPictureId = 12, localFilePath = null, requestId = null, state = ExerciseImageState.SYNCED),
                )
            }
            val dao = database.exerciseSyncDao()
            assertThat(dao.getSendable("member-a").map { it.memberId }).containsExactly("member-a")
            assertThat(database.exerciseImageDao().getPending("member-b")).containsExactly(image("member-b"))
            assertThat(dao.claim("member-b", "record", 99, "edit-one", "create-one", "body", 42)).isFalse()
            assertThat(dao.deleteMatching("member-a", "record", "wrong", 1)).isEqualTo(0)
            assertThat(dao.deleteMatching("member-a", "record", "edit-one", 1)).isEqualTo(1)
            assertThat(database.exerciseImageDao().deleteRemoteCache("member-a", "record", "remote")).isEqualTo(1)
            assertThat(database.exerciseImageDao().deleteMatching("member-a", "record", "image", "wrong")).isEqualTo(0)
            assertThat(database.exerciseImageDao().deleteMatching("member-a", "record", "image", "image-request")).isEqualTo(1)
            reopen()
            assertThat(database.exerciseSyncDao().getByMember("member-b")).hasSize(1)
            assertThat(database.exerciseSyncDao().get("member-a", "record")).isNull()
            assertThat(database.exerciseSyncDao().get("member-b", "record")).isEqualTo(journal("member-b"))
            assertThat(database.exerciseImageDao().getByRecord("member-a", "record")).isEmpty()
            assertThat(database.exerciseImageDao().getByRecord("member-b", "record")).hasSize(2)
        }

    @Test
    fun `서버 사진 갱신은 안정 ID를 유지하고 대기 삭제와 로컬 파일 요청을 보존한다`() =
        runTest {
            val dao = database.exerciseImageDao()
            val owned = image().copy(serverPictureId = 11, state = ExerciseImageState.DELETE_PENDING)
            val remote = image().copy(localId = "stable", serverPictureId = 12, localFilePath = null, requestId = null, state = ExerciseImageState.SYNCED)
            val syncedOwned = image().copy(localId = "owned-synced", serverPictureId = 13, state = ExerciseImageState.SYNCED)
            dao.save(owned)
            dao.save(remote)
            dao.save(syncedOwned)
            dao.refreshSynced(
                "member-a",
                "record",
                listOf(
                    CachedPicture(12, "https://example.test/new"),
                    CachedPicture(11, "https://example.test/old"),
                    CachedPicture(13, "https://example.test/owned"),
                ),
            )
            assertThat(runCatching { dao.save(owned.copy(requestId = "changed")) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
            reopen()
            assertThat(database.exerciseImageDao().getByRecord("member-a", "record"))
                .containsExactly(
                    owned,
                    remote.copy(serverUrl = "https://example.test/new", sortOrder = 0),
                    syncedOwned.copy(serverUrl = "https://example.test/owned", sortOrder = 2),
                )
            database.exerciseImageDao().refreshSynced("member-a", "record", emptyList())
            assertThat(database.exerciseImageDao().getByRecord("member-a", "record"))
                .containsExactly(owned, syncedOwned.copy(serverUrl = "https://example.test/owned", sortOrder = 2))
        }

    @Test
    fun `원격 목록 상세 쓰기 반영은 알려진 버전과 실제 조회 날짜와 사진 ID를 보존한다`() =
        runTest {
            val id = local.saveSyncedDetail(7, record())!!
            val original = database.exerciseRecordDao().getDetailByLocalId("member-a", id)!!
            assertThat(original.confirmedDate).isNull()
            database.exerciseRecordDao().upsertDetail(original.copy(serverVersion = 42))
            val queryDate = date.plusDays(1)
            local.replaceSyncedListByDate(queryDate, listOf(serverItem()))
            local.saveSyncedDetail(7, record().copy(title = "새 상세"))
            local.replaceSyncedListByDate(queryDate, listOf(serverItem().copy(title = "새 목록")))
            assertThat(database.exerciseRecordDao().getListItem("member-a", id)?.serverVersion).isEqualTo(42L)
            assertThat(database.exerciseRecordDao().getListItem("member-a", id)?.confirmedDate).isEqualTo(queryDate)
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", id)?.serverVersion).isEqualTo(42L)
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", id)?.confirmedDate).isEqualTo(queryDate)
            val images = database.exerciseImageDao().getByRecord("member-a", id)
            local.reconcileRecord(7, record(), "member-a")
            reopen()
            assertThat(database.exerciseImageDao().getByRecord("member-a", id)).isEqualTo(images)
            assertThat(database.exerciseRecordDao().getListItem("member-a", id)?.confirmedDate).isEqualTo(queryDate)
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", id)?.serverVersion).isEqualTo(42L)
        }

    @Test
    fun `저널만 대기인 투영도 원격 갱신 삭제 캐시 무효화와 기간 정리에서 보존한다`() =
        runTest {
            val id = local.reconcileRecord(7, record(), "member-a")!!
            val original = database.exerciseRecordDao().getDetailByLocalId("member-a", id)
            database.exerciseSyncDao().saveDesired(journal().copy(localId = id, serverId = 7, operation = SyncState.UPDATE_PENDING))
            local.replaceSyncedListByDate(date, emptyList())
            local.saveSyncedDetail(7, record().copy(title = "원격"))
            local.reconcileRecord(7, record().copy(title = "원격"), "member-a")
            local.reconcileDeletedRecord(7, "member-a")
            local.invalidateImages(7, "member-a")
            every { preferences.isExerciseCacheInvalidated("member-a") } returns true
            local.prepareCache("member-a")
            cleanExpired()
            reopen()
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", id)).isEqualTo(original)
            assertThat(database.exerciseRecordDao().getListItem("member-a", id)).isNotNull()
            assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        }

    @Test
    fun `사진 소유 행은 상세 갱신해도 남고 캐시 정리는 원격 전용 행만 제거한다`() =
        runTest {
            val ownedId = local.reconcileRecord(7, record(), "member-a")!!
            val pending = image().copy(recordLocalId = ownedId)
            database.exerciseImageDao().save(pending)
            val remoteId = local.reconcileRecord(8, record(), "member-a")!!
            local.saveSyncedDetail(7, record().copy(title = "갱신"))
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", ownedId)?.title).isEqualTo("갱신")
            every { preferences.isExerciseCacheInvalidated("member-a") } returns true
            local.prepareCache("member-a")
            cleanExpired()
            reopen()
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", ownedId)).isNotNull()
            assertThat(database.exerciseImageDao().getByRecord("member-a", ownedId)).contains(pending)
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", remoteId)).isNull()
            assertThat(database.exerciseImageDao().getByRecord("member-a", remoteId)).isEmpty()
            RoomSessionDataCleaner(database, UnconfinedTestDispatcher()).clearAll()
            assertThat(database.exerciseImageDao().getByRecord("member-a", ownedId)).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
        }

    private suspend fun cleanExpired() {
        val clock = mockk<ClockProvider>()
        every { clock.now() } returns LocalDate.parse("2026-10-10")
        RoomExerciseCacheCleaner(database, database.exerciseCountDao(), database.exerciseRecordDao(), clock, UnconfinedTestDispatcher())
            .cleanExpiredCache()
    }

    private fun journal(member: String = "member-a"): ExerciseSyncEntity =
        ExerciseSyncEntity(
            member,
            "record",
            null,
            SyncState.CREATE_PENDING,
            1,
            "edit-one",
            "create-one",
            null,
            null,
            date,
            ExerciseRecoveryState.READY,
        )

    private fun item(member: String = "member-a"): ExerciseListItemEntity =
        ExerciseListItemEntity(
            member,
            "record",
            null,
            SyncState.CREATE_PENDING,
            date,
            0,
            "제목",
            "WEIGHT",
            date.atTime(10, 0),
            date.atTime(11, 0),
            null,
        )

    private fun detail(member: String = "member-a"): ExerciseRecordDetailEntity =
        ExerciseRecordDetailEntity(
            member,
            "record",
            null,
            SyncState.CREATE_PENDING,
            "제목",
            "상세",
            "WEIGHT",
            date.atTime(10, 0),
            date.atTime(11, 0),
            "장소",
            null,
        )

    private fun image(member: String = "member-a"): ExerciseImageEntity =
        ExerciseImageEntity(member, "record", "image", null, "/app/image.jpg", null, ExerciseImageState.ADD_PENDING, "image-request", 1)

    private fun record(): ExerciseRecord =
        ExerciseRecord(
            title = "제목",
            detail = "상세",
            personalType = "WEIGHT",
            startedAt = date.atTime(10, 0),
            endedAt = date.atTime(11, 0),
            location = "장소",
            pictures = listOf(ExerciseRecordPicture(12, "https://example.test/picture")),
        )

    private fun serverItem(): ExerciseListItem =
        ExerciseListItem("7", "제목", "WEIGHT", date.atTime(10, 0), date.atTime(11, 0), listOf("https://example.test/picture"))
}
