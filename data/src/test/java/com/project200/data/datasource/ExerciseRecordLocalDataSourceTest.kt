package com.project200.data.datasource

import com.google.common.truth.Truth.assertThat
import com.project200.data.di.DatabaseModule
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 메모리 DB로 캐시 계층을 검증합니다.
 *
 * DAO를 목킹하면 호출 인자만 확인할 수 있어서 SQL과 트랜잭션 결과는 검증되지 않습니다.
 * 대기 행 보존은 틀려도 화면에 드러나지 않으므로 실제 DB로 확인합니다
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseRecordLocalDataSourceTest {
    private lateinit var database: UndabangDatabase
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var dataSource: ExerciseRecordLocalDataSource

    private val date = LocalDate.parse("2026-09-10")
    private val otherDate = LocalDate.parse("2026-09-11")

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        preferenceManager = mockk()
        every { preferenceManager.getMemberId() } returns MEMBER_ID
        dataSource =
            ExerciseRecordLocalDataSource(
                database = database,
                exerciseCountDao = database.exerciseCountDao(),
                exerciseRecordDao = database.exerciseRecordDao(),
                preferenceManager = preferenceManager,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun serverItem(recordId: Long) =
        ExerciseListItem(
            recordId = recordId.toString(),
            title = "제목 $recordId",
            type = "WEIGHT",
            startTime = LocalDateTime.parse("2026-09-10T10:00:00"),
            endTime = LocalDateTime.parse("2026-09-10T11:00:00"),
            imageUrl = null,
        )

    private fun record() =
        ExerciseRecord(
            title = "제목",
            detail = "본문",
            personalType = "WEIGHT",
            startedAt = LocalDateTime.parse("2026-09-10T10:00:00"),
            endedAt = LocalDateTime.parse("2026-09-10T11:00:00"),
            location = "헬스장",
            pictures = null,
        )

    private suspend fun insertRow(
        localId: String,
        serverId: Long?,
        syncState: SyncState,
        date: LocalDate = this.date,
    ) {
        database.exerciseRecordDao().upsertListItems(
            listOf(
                ExerciseListItemEntity(
                    memberId = MEMBER_ID,
                    localId = localId,
                    serverId = serverId,
                    syncState = syncState,
                    date = date,
                    sortOrder = 0,
                    title = "로컬 $localId",
                    personalType = "WEIGHT",
                    startedAt = LocalDateTime.parse("2026-09-10T09:00:00"),
                    endedAt = LocalDateTime.parse("2026-09-10T09:30:00"),
                    thumbnailUrls = null,
                ),
            ),
        )
    }

    private suspend fun insertDetail(
        localId: String,
        serverId: Long?,
    ) {
        database.exerciseRecordDao().upsertDetail(
            ExerciseRecordDetailEntity(
                memberId = MEMBER_ID,
                localId = localId,
                serverId = serverId,
                syncState = SyncState.SYNCED,
                title = "상세 $localId",
                detail = "본문",
                personalType = "WEIGHT",
                startedAt = LocalDateTime.parse("2026-09-10T10:00:00"),
                endedAt = LocalDateTime.parse("2026-09-10T11:00:00"),
                location = "헬스장",
                pictures = null,
            ),
        )
    }

    // ── 서버 목록 반영 ────────────────────────────────

    @Test
    fun `목록 반영 - 전송 대기 행이 걸린 기록은 서버 값으로 덮이지 않는다`() =
        runTest {
            // Given: 서버 기록 2번에 로컬 수정이 걸려 있다
            insertRow("pending-2", serverId = 2L, syncState = SyncState.UPDATE_PENDING)
            insertRow("synced-1", serverId = 1L, syncState = SyncState.SYNCED)

            // When
            dataSource.replaceSyncedListByDate(
                date,
                listOf(serverItem(1L), serverItem(2L), serverItem(3L)),
            )

            // Then: 2번은 로컬 값 그대로 남고 서버 값이 들어오지 않는다
            val rows = database.exerciseRecordDao().getListByDate(MEMBER_ID, date)
            val pending = rows.single { it.serverId == 2L }
            assertThat(pending.localId).isEqualTo("pending-2")
            assertThat(pending.syncState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(pending.title).isEqualTo("로컬 pending-2")
            assertThat(rows.map { it.serverId }).containsExactly(1L, 2L, 3L)
        }

    @Test
    fun `목록 반영 - 이미 캐시된 기록은 localId를 이어 쓴다`() =
        runTest {
            // Given
            insertRow("keep-me", serverId = 1L, syncState = SyncState.SYNCED)
            insertDetail("keep-me", serverId = 1L)

            // When
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(1L)))

            // Then: 상세와 화면이 가리키던 행이 유지된다
            val row = database.exerciseRecordDao().getListByDate(MEMBER_ID, date).single()
            assertThat(row.localId).isEqualTo("keep-me")
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, "keep-me")).isNotNull()
        }

    @Test
    fun `목록 반영 - 처음 보는 기록은 새 localId를 받는다`() =
        runTest {
            // When
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(9L)))

            // Then
            val row = database.exerciseRecordDao().getListByDate(MEMBER_ID, date).single()
            assertThat(row.localId).isNotEmpty()
            assertThat(row.syncState).isEqualTo(SyncState.SYNCED)
            assertThat(row.sortOrder).isEqualTo(0)
        }

    @Test
    fun `목록 반영 - 서버 목록에서 빠진 기록은 상세도 지운다`() =
        runTest {
            // Given
            insertRow("gone", serverId = 5L, syncState = SyncState.SYNCED)
            insertDetail("gone", serverId = 5L)

            // When
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(6L)))

            // Then
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, "gone")).isNull()
            assertThat(database.exerciseRecordDao().getListByDate(MEMBER_ID, date).map { it.serverId })
                .containsExactly(6L)
        }

    @Test
    fun `목록 반영 - 날짜가 바뀐 기록은 옛 날짜 행을 남기지 않는다`() =
        runTest {
            // Given: 서버에서 기록 4번의 날짜가 바뀐 상황
            insertRow("moved", serverId = 4L, syncState = SyncState.SYNCED, date = date)

            // When
            dataSource.replaceSyncedListByDate(otherDate, listOf(serverItem(4L)))

            // Then: 같은 serverId가 두 날짜에 남지 않는다
            assertThat(database.exerciseRecordDao().getListByDate(MEMBER_ID, date)).isEmpty()
            val moved = database.exerciseRecordDao().getListByDate(MEMBER_ID, otherDate).single()
            assertThat(moved.localId).isEqualTo("moved")
        }

    @Test
    fun `목록 반영 - 회원ID가 없으면 아무것도 쓰지 않는다`() =
        runTest {
            // Given
            every { preferenceManager.getMemberId() } returns null

            // When
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(1L)))

            // Then
            assertThat(database.exerciseRecordDao().getListByDate(MEMBER_ID, date)).isEmpty()
        }

    // ── 읽기 ────────────────────────────────

    @Test
    fun `목록 읽기 - 서버 ID 없이도 로컬 식별자와 대기 표시를 유지한다`() =
        runTest {
            // Given
            insertRow("local-only", serverId = null, syncState = SyncState.CREATE_PENDING)
            insertRow("synced", serverId = 1L, syncState = SyncState.SYNCED)

            // When
            val result = dataSource.getListByDate(date)

            assertThat(result.map { it.recordId }).containsExactly("local-only", "synced")
            assertThat(result.single { it.recordId == "local-only" }.syncPending).isTrue()
            assertThat(result.single { it.recordId == "synced" }.syncPending).isFalse()
        }

    @Test
    fun `목록 읽기 - 회원ID가 없으면 빈 목록을 돌려준다`() =
        runTest {
            // Given
            insertRow("synced", serverId = 1L, syncState = SyncState.SYNCED)
            every { preferenceManager.getMemberId() } returns null

            // When & Then
            assertThat(dataSource.getListByDate(date)).isEmpty()
        }

    // ── 캘린더 집계 ────────────────────────────────

    @Test
    fun `집계 반영 - 구간을 비우고 다시 채운다`() =
        runTest {
            // Given: 0건이 된 날이 남지 않아야 한다
            dataSource.replaceCountsByRange(
                date,
                otherDate,
                listOf(ExerciseCount(date, 2), ExerciseCount(otherDate, 1)),
            )

            // When
            dataSource.replaceCountsByRange(date, otherDate, listOf(ExerciseCount(date, 3)))

            // Then
            val result = dataSource.getCountsByRange(date, otherDate)
            assertThat(result.map { it.date to it.count }).containsExactly(date to 3)
        }

    // ── 상세와 삭제 ────────────────────────────────

    @Test
    fun `상세 저장 - 목록에 있는 기록이면 그 localId를 쓴다`() =
        runTest {
            // Given
            insertRow("list-row", serverId = 3L, syncState = SyncState.SYNCED)

            // When
            dataSource.saveSyncedDetail(3L, record())

            // Then
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, "list-row")).isNotNull()
        }

    @Test
    fun `기록 삭제 - 목록과 상세를 숨기고 삭제 상태를 함께 남긴다`() =
        runTest {
            // Given
            insertRow("target", serverId = 1L, syncState = SyncState.SYNCED)
            insertDetail("target", serverId = 1L)

            // When
            dataSource.deleteRecord("target")

            // Then
            assertThat(database.exerciseRecordDao().getListByDate(MEMBER_ID, date)).isEmpty()
            assertThat(dataSource.getDetailByLocalId("target")).isNull()
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, "target")?.operation).isEqualTo(SyncState.DELETE_PENDING)
        }

    @Test
    fun `상세를 먼저 받은 뒤 목록을 받아도 같은 로컬 식별자를 쓴다`() =
        runTest {
            val id = dataSource.saveSyncedDetail(7L, record())
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(7L)))
            assertThat(dataSource.getListByDate(date).single().recordId).isEqualTo(id)
            assertThat(dataSource.getDetailByLocalId(id!!)).isNotNull()
        }

    @Test
    fun `성공한 빈 조회만 캐시로 인정하고 다른 계정에는 노출하지 않는다`() =
        runTest {
            assertThat(dataSource.getListSnapshot(date, MEMBER_ID)).isNull()
            assertThat(dataSource.getCountsSnapshot(date, otherDate, MEMBER_ID)).isNull()
            dataSource.replaceSyncedListByDate(date, emptyList())
            dataSource.replaceCountsByRange(date, otherDate, emptyList())
            assertThat(dataSource.getListSnapshot(date, MEMBER_ID)).isEmpty()
            assertThat(dataSource.getCountsSnapshot(date, otherDate, MEMBER_ID)).isEmpty()

            every { preferenceManager.getMemberId() } returns "member-b"
            assertThat(dataSource.getListSnapshot(date, "member-b")).isNull()
            assertThat(dataSource.getCountsSnapshot(date, otherDate, "member-b")).isNull()
            // 요청 전에 캡처한 이전 계정도 현재 계정의 결과로 바뀌지 않습니다.
            assertThat(dataSource.getListSnapshot(date, MEMBER_ID)).isNull()
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(7L)), MEMBER_ID)
            assertThat(database.exerciseRecordDao().getListByDate("member-b", date)).isEmpty()
        }

    @Test
    fun `다른 날짜로 이동한 대기 행은 이전 날짜의 서버 목록으로 복원하지 않는다`() =
        runTest {
            insertRow("moved-pending", 7L, SyncState.UPDATE_PENDING, otherDate)
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(7L)))
            assertThat(dataSource.getListByDate(date)).isEmpty()
            assertThat(dataSource.getListByDate(otherDate).single().recordId).isEqualTo("moved-pending")
        }

    @Test
    fun `상세에만 있는 대기 변경은 목록 삭제와 상세 갱신에도 보존된다`() =
        runTest {
            insertRow("protected", 7L, SyncState.SYNCED)
            insertDetail("protected", 7L)
            val detail = database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, "protected")!!
            database.exerciseRecordDao().upsertDetail(detail.copy(syncState = SyncState.UPDATE_PENDING))

            dataSource.replaceSyncedListByDate(date, emptyList())
            dataSource.saveSyncedDetail(7L, record().copy(title = "서버 값"))
            assertThat(dataSource.getListByDate(date).single().recordId).isEqualTo("protected")
            assertThat(dataSource.getDetailByLocalId("protected")?.title).isEqualTo(detail.title)
            assertThat(dataSource.getDetailByLocalId("protected")?.syncPending).isTrue()
        }

    @Test
    fun `빈 조회 메타데이터 추가 마이그레이션은 기존 계정 대기 행과 타이머를 보존한다`() =
        runTest {
            insertRow("pending", null, SyncState.CREATE_PENDING)
            insertDetail("detail", 7L)
            val timer = SimpleTimerEntity(MEMBER_ID, "timer", null, SyncState.CREATE_PENDING, 60, "request", null, 1L)
            database.timerDao().upsertSimpleTimer(timer)
            val sql = database.openHelper.writableDatabase
            sql.execSQL("DROP TABLE exercise_cache_query")
            DatabaseModule.MIGRATION_2_3.migrate(sql)
            dataSource.replaceSyncedListByDate(otherDate, emptyList())

            assertThat(dataSource.getListByDate(date).single().recordId).isEqualTo("pending")
            assertThat(dataSource.getDetailByLocalId("detail")).isNotNull()
            assertThat(database.timerDao().getSimpleTimer(MEMBER_ID, "timer")).isEqualTo(timer)
            assertThat(dataSource.getListSnapshot(otherDate, MEMBER_ID)).isEmpty()
        }

    @Test
    fun `집계의 조회 표식 없는 0행은 성공한 빈 쿼리로 꾸미지 않는다`() =
        runTest {
            database.exerciseCountDao().upsertAll(listOf(ExerciseCountEntity(MEMBER_ID, date, 0)))
            assertThat(dataSource.getCountsSnapshot(date, otherDate, MEMBER_ID)).isNull()
            dataSource.replaceCountsByRange(date, otherDate, emptyList())
            assertThat(dataSource.getCountsSnapshot(date, otherDate, MEMBER_ID)).isEmpty()
            assertThat(dataSource.getCountsSnapshot(date, otherDate.plusDays(1), MEMBER_ID)).isNull()
        }

    @Test
    fun `미전송 생성 수정 삭제는 같은 로컬 ID와 생성 키를 사용하고 저널까지 제거한다`() =
        runTest {
            val id = dataSource.createRecord(ExerciseWriteInput(record())).recordId
            val created = database.exerciseSyncDao().get(MEMBER_ID, id)!!
            assertThat(created.operation).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(created.revision).isEqualTo(1L)
            assertThat(created.createRequestId).isNotEmpty()
            assertThat(dataSource.getCountsByRange(date, date).single().count).isEqualTo(1)
            assertThat(dataSource.updateRecord(id, ExerciseWriteInput(record().copy(title = "로컬 수정"))).recordId).isEqualTo(id)
            val edited = database.exerciseSyncDao().get(MEMBER_ID, id)!!
            assertThat(edited.revision).isEqualTo(2L)
            assertThat(edited.createRequestId).isEqualTo(created.createRequestId)
            assertThat(edited.operationId).isNotEqualTo(created.operationId)
            assertThat(dataSource.getListByDate(date).single().recordId).isEqualTo(id)
            assertThat(dataSource.getDetailByLocalId(id)?.title).isEqualTo("로컬 수정")
            assertThat(dataSource.getDetailByLocalId(id)?.canModify).isTrue()
            assertThat(dataSource.getCountsByRange(date, date).single().count).isEqualTo(1)
            dataSource.deleteRecord(id)
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)).isNull()
            assertThat(database.exerciseRecordDao().getListItem(MEMBER_ID, id)).isNull()
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, id)).isNull()
            assertThat(dataSource.getListSnapshot(date, MEMBER_ID)).isEmpty()
            assertThat(dataSource.getCountsSnapshot(date, date, MEMBER_ID)?.sumOf { it.count }).isEqualTo(0)
        }

    private suspend fun confirmedRecord(): String {
        dataSource.replaceSyncedListByDate(date, listOf(serverItem(7)))
        val id = dataSource.saveSyncedDetail(7, record())!!
        val detail = database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, id)!!
        database.exerciseRecordDao().upsertDetail(detail.copy(serverVersion = 9))
        return id
    }

    @Test
    fun `날짜 이동 재편집 복귀 삭제와 새 서버 집계는 최초 확정 날짜의 변경을 한 번만 반영한다`() =
        runTest {
            val id = confirmedRecord()
            dataSource.replaceCountsByRange(date, otherDate, listOf(ExerciseCount(date, 2), ExerciseCount(otherDate, 3)))
            val moved = record().copy(startedAt = otherDate.atTime(10, 0), endedAt = otherDate.atTime(11, 0))
            dataSource.updateRecord(id, ExerciseWriteInput(moved))
            dataSource.updateRecord(id, ExerciseWriteInput(moved.copy(title = "다시 수정")))
            val journal = database.exerciseSyncDao().get(MEMBER_ID, id)!!
            assertThat(journal.confirmedDate).isEqualTo(date)
            assertThat(journal.currentDate).isEqualTo(otherDate)
            assertThat(journal.revision).isEqualTo(2L)
            assertThat(journal.serverVersion).isEqualTo(9L)
            assertThat(dataSource.getCountsByRange(date, otherDate).associate { it.date to it.count })
                .containsExactly(date, 1, otherDate, 4)
            assertThat(dataSource.getListByDate(date)).isEmpty()
            assertThat(dataSource.getListByDate(otherDate).single().recordId).isEqualTo(id)
            dataSource.replaceCountsByRange(date, otherDate, listOf(ExerciseCount(date, 4), ExerciseCount(otherDate, 5)))
            assertThat(dataSource.getCountsByRange(date, otherDate).associate { it.date to it.count })
                .containsExactly(date, 3, otherDate, 6)
            dataSource.updateRecord(id, ExerciseWriteInput(record()))
            assertThat(dataSource.getCountsByRange(date, otherDate).associate { it.date to it.count })
                .containsExactly(date, 4, otherDate, 5)
            dataSource.updateRecord(id, ExerciseWriteInput(moved))
            dataSource.deleteRecord(id)
            dataSource.deleteRecord(id)
            assertThat(dataSource.getCountsByRange(date, otherDate).associate { it.date to it.count })
                .containsExactly(date, 3, otherDate, 5)
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)?.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(database.exerciseCountDao().getRange(MEMBER_ID, date, date).single().count).isEqualTo(4)
        }

    @Test
    fun `마지막 서버 기록 삭제는 빈 조회를 유지하며 새 서버 목록 상세로 부활하지 않는다`() =
        runTest {
            val id = confirmedRecord()
            dataSource.replaceCountsByRange(date, date, listOf(ExerciseCount(date, 1)))
            dataSource.deleteRecord(id)
            dataSource.replaceSyncedListByDate(date, listOf(serverItem(7)))
            dataSource.saveSyncedDetail(7, record())
            assertThat(dataSource.getListSnapshot(date, MEMBER_ID)).isEmpty()
            assertThat(dataSource.getDetailByLocalId(id)).isNull()
            assertThat(dataSource.getDetailByServerId(7)).isNull()
            assertThat(dataSource.getCountsSnapshot(date, date, MEMBER_ID)?.single()?.count).isEqualTo(0)
        }

    @Test
    fun `서버 목록의 확정 날짜와 버전은 본문 재편집으로 다시 추정하지 않는다`() =
        runTest {
            dataSource.replaceSyncedListByDate(otherDate, listOf(serverItem(7)))
            val id = dataSource.saveSyncedDetail(7, record())!!
            val dao = database.exerciseRecordDao()
            dao.upsertDetail(dao.getDetailByLocalId(MEMBER_ID, id)!!.copy(serverVersion = 9))
            dataSource.replaceCountsByRange(date, otherDate, listOf(ExerciseCount(otherDate, 1)))
            dataSource.updateRecord(id, ExerciseWriteInput(record().copy(title = "본문만 수정")))
            dataSource.updateRecord(id, ExerciseWriteInput(record().copy(title = "다시 수정")))
            val journal = database.exerciseSyncDao().get(MEMBER_ID, id)!!
            assertThat(journal.confirmedDate).isEqualTo(otherDate)
            assertThat(journal.currentDate).isEqualTo(otherDate)
            assertThat(journal.serverVersion).isEqualTo(9L)
            assertThat(dataSource.getCountsByRange(date, otherDate).sumOf { it.count }).isEqualTo(1)
        }

    @Test
    fun `전송 중 생성 재편집 삭제는 원래 원문 키와 revision을 보존한다`() =
        runTest {
            val id = dataSource.createRecord(ExerciseWriteInput(record())).recordId
            val dao = database.exerciseSyncDao()
            val created = dao.get(MEMBER_ID, id)!!
            val request = requireNotNull(created.createRequestId)
            assertThat(dao.claim(MEMBER_ID, id, 1, requireNotNull(created.operationId), request, "body-A", null)).isTrue()
            dataSource.updateRecord(id, ExerciseWriteInput(record().copy(title = "본문 B")))
            assertThat(dataSource.getDetailByLocalId(id)?.title).isEqualTo("본문 B")
            dataSource.deleteRecord(id)
            val deleted = dao.get(MEMBER_ID, id)!!
            assertThat(deleted.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(deleted.revision).isEqualTo(3L)
            assertThat(deleted.createRequestId).isEqualTo(request)
            assertThat(deleted.inFlightOperation).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(deleted.inFlightBody).isEqualTo("body-A")
            assertThat(deleted.inFlightOperationId).isEqualTo(created.operationId)
            assertThat(deleted.inFlightRequestId).isEqualTo(request)
            assertThat(deleted.inFlightRevision).isEqualTo(1L)
            assertThat(dataSource.getCountsByRange(date, date).single().count).isEqualTo(0)
        }

    @Test
    fun `전송 중 수정의 재편집은 기준 버전과 원래 수정 snapshot을 바꾸지 않는다`() =
        runTest {
            val id = confirmedRecord()
            dataSource.updateRecord(id, ExerciseWriteInput(record().copy(title = "수정 A")))
            val dao = database.exerciseSyncDao()
            val first = dao.get(MEMBER_ID, id)!!
            assertThat(dao.claim(MEMBER_ID, id, 1, requireNotNull(first.operationId), "update-request", "update-body-A", 9)).isTrue()
            dataSource.updateRecord(id, ExerciseWriteInput(record().copy(title = "수정 B")))
            val latest = dao.get(MEMBER_ID, id)!!
            assertThat(latest.revision).isEqualTo(2L)
            assertThat(latest.serverVersion).isEqualTo(9L)
            assertThat(latest.inFlightBaseVersion).isEqualTo(9L)
            assertThat(latest.inFlightBody).isEqualTo("update-body-A")
            assertThat(latest.inFlightOperationId).isEqualTo(first.operationId)
            assertThat(latest.inFlightRequestId).isEqualTo("update-request")
            assertThat(latest.inFlightRevision).isEqualTo(1L)
            assertThat(dataSource.getDetailByLocalId(id)?.title).isEqualTo("수정 B")
        }

    @Test
    fun `로컬 생성과 대기 변경은 다른 계정에 노출하거나 수정하지 않는다`() =
        runTest {
            val id = dataSource.createRecord(ExerciseWriteInput(record())).recordId
            val original = database.exerciseSyncDao().get(MEMBER_ID, id)
            every { preferenceManager.getMemberId() } returns "member-b"
            assertThat(dataSource.getListByDate(date)).isEmpty()
            assertThat(dataSource.getDetailByLocalId(id)).isNull()
            assertThat(dataSource.getCountsByRange(date, date)).isEmpty()
            assertThat(runCatching { dataSource.updateRecord(id, ExerciseWriteInput(record())) }.isFailure).isTrue()
            assertThat(runCatching { dataSource.deleteRecord(id) }.isFailure).isTrue()
            assertThat(runCatching { dataSource.createRecord(ExerciseWriteInput(record()), MEMBER_ID) }.isFailure).isTrue()
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)).isEqualTo(original)
        }

    @Test
    fun `알 수 없는 과거 UPDATE 날짜는 같은 날짜 재편집에서도 CREATE로 세지 않는다`() =
        runTest {
            insertRow("legacy", 7, SyncState.UPDATE_PENDING)
            insertDetail("legacy", 7)
            dataSource.replaceCountsByRange(date, otherDate, listOf(ExerciseCount(date, 4)))
            dataSource.updateRecord("legacy", ExerciseWriteInput(record().copy(title = "사용자 변경")))
            dataSource.updateRecord("legacy", ExerciseWriteInput(record().copy(title = "재변경")))
            val journal = database.exerciseSyncDao().get(MEMBER_ID, "legacy")!!
            assertThat(journal.confirmedDate).isNull()
            assertThat(journal.serverVersion).isNull()
            assertThat(journal.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(dataSource.getDetailByLocalId("legacy")?.title).isEqualTo("재변경")
            assertThat(dataSource.getCountsByRange(date, otherDate).single().count).isEqualTo(4)
        }

    @Test
    fun `알 수 없는 과거 생성 취소는 미확정 생성 증거와 복구 tombstone을 남긴다`() =
        runTest {
            insertRow("legacy", null, SyncState.CREATE_PENDING)
            insertDetail("legacy", null)
            dataSource.deleteRecord("legacy")
            val journal = database.exerciseSyncDao().get(MEMBER_ID, "legacy")!!
            assertThat(journal.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(journal.createRequestId).isNull()
            assertThat(journal.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, "legacy")?.detail).isEqualTo("본문")
            assertThat(dataSource.getDetailByLocalId("legacy")).isNull()
            assertThat(dataSource.getListByDate(date)).isEmpty()
        }

    private fun insertMalformedPicturesMarker(id: String) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO exercise_sync(memberId, localId, serverId, operation, revision, operationId, createRequestId, " +
                "serverVersion, confirmedDate, currentDate, recoveryState, lastError) " +
                "VALUES(?, ?, 7, 'SYNCED', NULL, NULL, NULL, NULL, NULL, ?, 'RECOVERY_REQUIRED', 'MALFORMED_PICTURES')",
            arrayOf(MEMBER_ID, id, date.toString()),
        )
    }

    @Test
    fun `손상 사진 SYNCED 표식 뒤 마지막 삭제는 명시적 확정 날짜와 버전으로 횟수를 0으로 만든다`() =
        runTest {
            val id = confirmedRecord()
            insertMalformedPicturesMarker(id)
            dataSource.replaceCountsByRange(date, date, listOf(ExerciseCount(date, 1)))

            dataSource.deleteRecord(id)

            val journal = database.exerciseSyncDao().get(MEMBER_ID, id)!!
            assertThat(journal.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(journal.confirmedDate).isEqualTo(date)
            assertThat(journal.serverVersion).isEqualTo(9L)
            assertThat(journal.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(journal.lastError).isEqualTo(ExerciseSyncError.MALFORMED_PICTURES)
            assertThat(dataSource.getCountsSnapshot(date, date, MEMBER_ID)?.single()?.count).isEqualTo(0)
            assertThat(dataSource.getListByDate(date)).isEmpty()
            assertThat(dataSource.getDetailByLocalId(id)).isNull()
            assertThat(database.exerciseCountDao().getRange(MEMBER_ID, date, date).single().count).isEqualTo(1)
        }

    @Test
    fun `손상 사진 SYNCED 표식 뒤 날짜 이동과 재편집 삭제는 원래 명시적 기준을 유지한다`() =
        runTest {
            val id = confirmedRecord()
            insertMalformedPicturesMarker(id)
            dataSource.replaceCountsByRange(date, otherDate, listOf(ExerciseCount(date, 1)))
            val moved = record().copy(startedAt = otherDate.atTime(10, 0), endedAt = otherDate.atTime(11, 0))

            dataSource.updateRecord(id, ExerciseWriteInput(moved))
            dataSource.updateRecord(id, ExerciseWriteInput(moved.copy(title = "재편집")))

            val journal = database.exerciseSyncDao().get(MEMBER_ID, id)!!
            assertThat(journal.operation).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(journal.confirmedDate).isEqualTo(date)
            assertThat(journal.currentDate).isEqualTo(otherDate)
            assertThat(journal.serverVersion).isEqualTo(9L)
            assertThat(journal.lastError).isEqualTo(ExerciseSyncError.MALFORMED_PICTURES)
            assertThat(journal.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(dataSource.getCountsByRange(date, otherDate).associate { it.date to it.count })
                .containsExactly(date, 0, otherDate, 1)
            dataSource.deleteRecord(id)
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)?.confirmedDate).isEqualTo(date)
            assertThat(dataSource.getCountsByRange(date, otherDate).sumOf { it.count }).isEqualTo(0)
        }

    @Test
    fun `손상 사진 SYNCED 표식도 명시적 기준이 없으면 시작 날짜로 채우지 않는다`() =
        runTest {
            insertRow("unknown-marker", 7, SyncState.SYNCED)
            insertDetail("unknown-marker", 7)
            insertMalformedPicturesMarker("unknown-marker")

            dataSource.updateRecord("unknown-marker", ExerciseWriteInput(record().copy(title = "기준 없는 수정")))
            dataSource.deleteRecord("unknown-marker")

            val journal = database.exerciseSyncDao().get(MEMBER_ID, "unknown-marker")!!
            assertThat(journal.confirmedDate).isNull()
            assertThat(journal.serverVersion).isNull()
            assertThat(journal.lastError).isEqualTo(ExerciseSyncError.MALFORMED_PICTURES)
            assertThat(journal.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
        }

    @Test
    fun `손상 사진 복구용 SYNCED 저널은 운동 본문 집계를 늘리지 않는다`() =
        runTest {
            val id = confirmedRecord()
            dataSource.replaceCountsByRange(date, date, listOf(ExerciseCount(date, 2)))
            insertMalformedPicturesMarker(id)
            val counts = dataSource.getCountsByRange(date, date).single()
            assertThat(counts.count).isEqualTo(2)
            assertThat(counts.syncPending).isFalse()
        }

    @Test
    fun `모순 서버 식별자는 편집 삭제로 조용히 합치지 않는다`() =
        runTest {
            insertRow("contradiction", 8, SyncState.UPDATE_PENDING)
            insertDetail("contradiction", 7)
            assertThat(runCatching { dataSource.updateRecord("contradiction", ExerciseWriteInput(record())) }.isFailure).isTrue()
            assertThat(runCatching { dataSource.deleteRecord("contradiction") }.isFailure).isTrue()
            assertThat(database.exerciseRecordDao().getListItem(MEMBER_ID, "contradiction")?.serverId).isEqualTo(8L)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, "contradiction")?.serverId).isEqualTo(7L)
            assertThat(database.exerciseSyncDao().getByMember(MEMBER_ID)).isEmpty()
        }

    @Test
    fun `부분 전송 증거와 알 수 없는 기준 날짜는 재편집 취소로 지우거나 추정하지 않는다`() =
        runTest {
            val id = confirmedRecord()
            database.exerciseSyncDao().saveDesired(
                ExerciseSyncEntity(
                    MEMBER_ID,
                    id,
                    7,
                    SyncState.UPDATE_PENDING,
                    null,
                    null,
                    null,
                    null,
                    null,
                    date,
                    ExerciseRecoveryState.RECOVERY_REQUIRED,
                    ExerciseSyncError.LEGACY_PENDING,
                ),
            )
            dataSource.updateRecord(id, ExerciseWriteInput(record().copy(title = "원문 유지")))
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)?.confirmedDate).isNull()
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)?.serverVersion).isNull()
            dataSource.deleteRecord(id)
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)?.confirmedDate).isNull()
            assertThat(database.exerciseSyncDao().get(MEMBER_ID, id)?.serverVersion).isNull()

            val created = dataSource.createRecord(ExerciseWriteInput(record())).recordId
            database.openHelper.writableDatabase.execSQL(
                "UPDATE exercise_sync SET inFlightRevision = 1 WHERE memberId = ? AND localId = ?",
                arrayOf(MEMBER_ID, created),
            )
            dataSource.deleteRecord(created)
            val tombstone = database.exerciseSyncDao().get(MEMBER_ID, created)!!
            assertThat(tombstone.inFlightRevision).isEqualTo(1L)
            assertThat(tombstone.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(database.exerciseSyncDao().deleteMatching(MEMBER_ID, created, tombstone.operationId, tombstone.revision))
                .isEqualTo(0)
        }

    @Test
    fun `재시도 중 생성의 취소는 생성 키와 본문을 지우지 않고 복구 상태로 남긴다`() =
        runTest {
            val id = dataSource.createRecord(ExerciseWriteInput(record())).recordId
            val dao = database.exerciseSyncDao()
            val original = dao.get(MEMBER_ID, id)!!
            dao.saveDesired(original.copy(recoveryState = ExerciseRecoveryState.RETRYABLE, lastError = ExerciseSyncError.NETWORK_ERROR))
            dataSource.deleteRecord(id)
            val deleted = dao.get(MEMBER_ID, id)!!
            assertThat(deleted.createRequestId).isEqualTo(original.createRequestId)
            assertThat(deleted.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(deleted.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, id)?.detail).isEqualTo(record().detail)
            assertThat(dao.getSendable(MEMBER_ID)).isEmpty()
            assertThat(dataSource.getListByDate(date)).isEmpty()
        }

    @Test
    fun `서버 ID 없는 과거 UPDATE를 재편집해도 새 CREATE 의도로 재구성하지 않는다`() =
        runTest {
            insertRow("legacy-update", null, SyncState.UPDATE_PENDING)
            insertDetail("legacy-update", null)
            dataSource.updateRecord("legacy-update", ExerciseWriteInput(record().copy(title = "보존할 편집")))
            val journal = database.exerciseSyncDao().get(MEMBER_ID, "legacy-update")!!
            assertThat(journal.operation).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(journal.serverId).isNull()
            assertThat(journal.createRequestId).isNull()
            assertThat(journal.lastError).isEqualTo(ExerciseSyncError.CONTRADICTORY_STATE)
            assertThat(journal.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(dataSource.getDetailByLocalId("legacy-update")?.title).isEqualTo("보존할 편집")
            assertThat(database.exerciseSyncDao().getSendable(MEMBER_ID)).isEmpty()
        }

    companion object {
        private const val MEMBER_ID = "member-a"
    }
}
