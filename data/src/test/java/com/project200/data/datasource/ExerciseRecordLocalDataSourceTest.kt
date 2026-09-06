package com.project200.data.datasource

import com.google.common.truth.Truth.assertThat
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
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
            recordId = recordId,
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
    fun `목록 읽기 - 서버 ID가 없는 행은 아직 걸러진다`() =
        runTest {
            // Given
            insertRow("local-only", serverId = null, syncState = SyncState.CREATE_PENDING)
            insertRow("synced", serverId = 1L, syncState = SyncState.SYNCED)

            // When
            val result = dataSource.getListByDate(date)

            // Then: 화면이 서버 ID로 기록을 식별하는 동안의 동작이다 (#584에서 바뀐다)
            assertThat(result.map { it.recordId }).containsExactly(1L)
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
    fun `기록 삭제 - 목록과 상세를 함께 지운다`() =
        runTest {
            // Given
            insertRow("target", serverId = 1L, syncState = SyncState.SYNCED)
            insertDetail("target", serverId = 1L)

            // When
            dataSource.deleteRecord("target")

            // Then
            assertThat(database.exerciseRecordDao().getListByDate(MEMBER_ID, date)).isEmpty()
            assertThat(database.exerciseRecordDao().getDetailByLocalId(MEMBER_ID, "target")).isNull()
        }

    companion object {
        private const val MEMBER_ID = "member-a"
    }
}
