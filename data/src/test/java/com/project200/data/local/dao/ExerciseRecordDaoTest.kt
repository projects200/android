package com.project200.data.local.dao

import com.google.common.truth.Truth.assertThat
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class ExerciseRecordDaoTest {
    private lateinit var database: UndabangDatabase
    private lateinit var dao: ExerciseRecordDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        dao = database.exerciseRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private val date = LocalDate.parse("2026-09-10")
    private val otherDate = LocalDate.parse("2026-09-11")

    private fun listItem(
        localId: String,
        serverId: Long?,
        syncState: SyncState = SyncState.SYNCED,
        memberId: String = "member-a",
        date: LocalDate = this.date,
        sortOrder: Int = 0,
    ) = ExerciseListItemEntity(
        memberId = memberId,
        localId = localId,
        serverId = serverId,
        syncState = syncState,
        date = date,
        sortOrder = sortOrder,
        title = "제목 $localId",
        personalType = "WEIGHT",
        startedAt = LocalDateTime.parse("2026-09-10T10:00:00"),
        endedAt = LocalDateTime.parse("2026-09-10T11:00:00"),
        thumbnailUrls = listOf("https://example.com/$localId.jpg"),
    )

    private fun detail(
        localId: String,
        serverId: Long?,
        memberId: String = "member-a",
    ) = ExerciseRecordDetailEntity(
        memberId = memberId,
        localId = localId,
        serverId = serverId,
        syncState = SyncState.SYNCED,
        title = "제목 $localId",
        detail = "본문",
        personalType = "WEIGHT",
        startedAt = LocalDateTime.parse("2026-09-10T10:00:00"),
        endedAt = LocalDateTime.parse("2026-09-10T11:00:00"),
        location = "헬스장",
        pictures = null,
    )

    // ── 목록 조회 ────────────────────────────────

    @Test
    fun `날짜별 조회 - 삭제 대기 행은 빼고 sortOrder로 정렬한다`() =
        runTest {
            // Given
            dao.upsertListItems(
                listOf(
                    listItem("b", serverId = 2L, sortOrder = 1),
                    listItem("a", serverId = 1L, sortOrder = 0),
                    listItem("c", serverId = 3L, syncState = SyncState.DELETE_PENDING, sortOrder = 2),
                ),
            )

            // When
            val result = dao.getListByDate("member-a", date)

            // Then
            assertThat(result.map { it.localId }).containsExactly("a", "b").inOrder()
        }

    @Test
    fun `날짜별 조회 - 다른 계정과 다른 날짜는 나오지 않는다`() =
        runTest {
            // Given
            dao.upsertListItems(
                listOf(
                    listItem("mine", serverId = 1L),
                    listItem("other-member", serverId = 2L, memberId = "member-b"),
                    listItem("other-date", serverId = 3L, date = otherDate),
                ),
            )

            // When
            val result = dao.getListByDate("member-a", date)

            // Then
            assertThat(result.map { it.localId }).containsExactly("mine")
        }

    @Test
    fun `날짜별 조회 - 서버 ID가 없는 생성 대기 행도 돌려준다`() =
        runTest {
            // Given: 오프라인에서 만든 기록은 서버 ID를 받기 전이다
            dao.upsertListItems(listOf(listItem("local-only", serverId = null, syncState = SyncState.CREATE_PENDING)))

            // When
            val result = dao.getListByDate("member-a", date)

            // Then: DAO는 걸러내지 않는다. 화면 표시 여부는 상위 계층이 정한다
            assertThat(result.map { it.serverId }).containsExactly(null)
        }

    // ── 전송 대기 행 ────────────────────────────────

    @Test
    fun `대기 서버ID 조회 - SYNCED가 아닌 행의 서버 ID만 돌려준다`() =
        runTest {
            // Given
            dao.upsertListItems(
                listOf(
                    listItem("synced", serverId = 1L),
                    listItem("updating", serverId = 2L, syncState = SyncState.UPDATE_PENDING),
                    listItem("deleting", serverId = 3L, syncState = SyncState.DELETE_PENDING),
                    listItem("creating", serverId = null, syncState = SyncState.CREATE_PENDING),
                ),
            )

            // When
            val result = dao.getPendingServerIds("member-a", date)

            // Then: 서버 ID가 없는 생성 대기 행은 비교 대상이 아니다
            assertThat(result).containsExactly(2L, 3L)
        }

    @Test
    fun `동기화 목록 삭제 - 전송 대기 행은 남긴다`() =
        runTest {
            // Given
            dao.upsertListItems(
                listOf(
                    listItem("synced", serverId = 1L),
                    listItem("updating", serverId = 2L, syncState = SyncState.UPDATE_PENDING),
                    listItem("creating", serverId = null, syncState = SyncState.CREATE_PENDING),
                ),
            )

            // When
            dao.deleteSyncedListByDate("member-a", date)

            // Then
            assertThat(dao.getListByDate("member-a", date).map { it.localId })
                .containsExactly("updating", "creating")
        }

    @Test
    fun `동기화 목록 조회 - 대기 행과 서버 ID 없는 행을 제외한다`() =
        runTest {
            // Given
            dao.upsertListItems(
                listOf(
                    listItem("synced", serverId = 1L),
                    listItem("updating", serverId = 2L, syncState = SyncState.UPDATE_PENDING),
                    listItem("creating", serverId = null, syncState = SyncState.CREATE_PENDING),
                ),
            )

            // When
            val result = dao.getSyncedByDate("member-a", date)

            // Then
            assertThat(result.map { it.serverId }).containsExactly(1L)
        }

    // ── localId 이어쓰기 ────────────────────────────────

    @Test
    fun `서버ID로 localId 조회 - 날짜를 가리지 않고 최신 날짜 행을 집는다`() =
        runTest {
            // Given: 서버에서 기록의 날짜가 바뀌어 두 날짜에 남은 상태
            dao.upsertListItems(
                listOf(
                    listItem("old-row", serverId = 7L, date = date),
                    listItem("new-row", serverId = 7L, date = otherDate),
                ),
            )

            // When
            val result = dao.findListItemLocalId("member-a", 7L)

            // Then
            assertThat(result).isEqualTo("new-row")
        }

    @Test
    fun `다른 날짜 정리 - 같은 서버ID의 옛 행만 지운다`() =
        runTest {
            // Given
            dao.upsertListItems(
                listOf(
                    listItem("old-row", serverId = 7L, date = date),
                    listItem("kept-other-server", serverId = 8L, date = date),
                    listItem("new-row", serverId = 7L, date = otherDate),
                ),
            )

            // When
            dao.deleteSyncedOnOtherDates("member-a", otherDate, listOf(7L))

            // Then
            assertThat(dao.getListByDate("member-a", date).map { it.localId })
                .containsExactly("kept-other-server")
            assertThat(dao.getListByDate("member-a", otherDate).map { it.localId })
                .containsExactly("new-row")
        }

    // ── 상세 ────────────────────────────────

    @Test
    fun `상세 조회 - 계정 조건이 걸린다`() =
        runTest {
            // Given
            dao.upsertDetail(detail("shared-local-id", serverId = 1L, memberId = "member-b"))

            // When & Then
            assertThat(dao.getDetailByLocalId("member-a", "shared-local-id")).isNull()
            assertThat(dao.getDetailByServerId("member-a", 1L)).isNull()
            assertThat(dao.getDetailByLocalId("member-b", "shared-local-id")).isNotNull()
        }

    @Test
    fun `상세 일괄 삭제 - 지정한 localId만 지운다`() =
        runTest {
            // Given
            dao.upsertDetail(detail("gone", serverId = 1L))
            dao.upsertDetail(detail("kept", serverId = 2L))

            // When
            dao.deleteDetailsByLocalIds("member-a", listOf("gone"))

            // Then
            assertThat(dao.getDetailByLocalId("member-a", "gone")).isNull()
            assertThat(dao.getDetailByLocalId("member-a", "kept")).isNotNull()
        }

    @Test
    fun `상세 저장 - 사진 목록이 JSON으로 왕복한다`() =
        runTest {
            // Given
            val pictures =
                listOf(
                    com.project200.data.local.entity.CachedPicture(id = 11L, url = "https://example.com/1.jpg"),
                    com.project200.data.local.entity.CachedPicture(id = 12L, url = "https://example.com/2.jpg"),
                )

            // When
            dao.upsertDetail(detail("with-pictures", serverId = 1L).copy(pictures = pictures))

            // Then: 순서까지 유지된다
            val saved = dao.getDetailByLocalId("member-a", "with-pictures")
            assertThat(saved?.pictures?.map { it.id }).containsExactly(11L, 12L).inOrder()
        }
}
