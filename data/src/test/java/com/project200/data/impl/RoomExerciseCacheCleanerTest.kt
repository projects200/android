package com.project200.data.impl

import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.ExerciseCacheQueryEntity
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.ExerciseRecordDetailEntity
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class RoomExerciseCacheCleanerTest {
    private lateinit var database: UndabangDatabase
    private val clockProvider = mockk<ClockProvider>()
    private val testDispatcher = StandardTestDispatcher()
    private val today = LocalDate.parse("2026-08-31")
    private val cutoff = LocalDate.parse("2026-02-28")
    private val oldDate = cutoff.minusDays(1)

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        every { clockProvider.now() } returns today
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun cleaner(countDao: ExerciseCountDao = database.exerciseCountDao()) =
        RoomExerciseCacheCleaner(
            database = database,
            exerciseCountDao = countDao,
            exerciseRecordDao = database.exerciseRecordDao(),
            clockProvider = clockProvider,
            ioDispatcher = testDispatcher,
        )

    private fun listItem(
        localId: String,
        serverId: Long?,
        syncState: SyncState = SyncState.SYNCED,
        date: LocalDate = oldDate,
        memberId: String = "member-a",
    ) = ExerciseListItemEntity(
        memberId = memberId,
        localId = localId,
        serverId = serverId,
        syncState = syncState,
        date = date,
        sortOrder = 0,
        title = "제목 $localId",
        personalType = "WEIGHT",
        startedAt = date.atTime(10, 0),
        endedAt = date.atTime(11, 0),
        thumbnailUrls = null,
    )

    private fun detail(
        localId: String,
        serverId: Long?,
        syncState: SyncState = SyncState.SYNCED,
        date: LocalDate = oldDate,
        memberId: String = "member-a",
    ) = ExerciseRecordDetailEntity(
        memberId = memberId,
        localId = localId,
        serverId = serverId,
        syncState = syncState,
        title = "상세 $localId",
        detail = "본문",
        personalType = "WEIGHT",
        startedAt = date.atStartOfDay(),
        endedAt = date.atTime(1, 0),
        location = "헬스장",
        pictures = null,
    )

    private suspend fun insertCachedRecord(
        localId: String = "old",
        serverId: Long = 1L,
        date: LocalDate = oldDate,
    ) {
        database.exerciseRecordDao().upsertListItems(listOf(listItem(localId, serverId, date = date)))
        database.exerciseRecordDao().upsertDetail(detail(localId, serverId, date = date))
        database.exerciseCountDao().upsertAll(listOf(ExerciseCountEntity("member-a", date, 1)))
        saveCacheQuery(ExerciseCacheQueryEntity("member-a", "list", date))
        saveCacheQuery(ExerciseCacheQueryEntity("member-a", "count", date))
    }

    // #584의 조회/저장 API를 복사하지 않고 공유 스키마의 실제 빈 응답 마커를 준비합니다
    private fun saveCacheQuery(query: ExerciseCacheQueryEntity) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO exercise_cache_query(memberId, queryType, date) VALUES (?, ?, ?)",
            arrayOf(query.memberId, query.queryType, query.date.toString()),
        )
    }

    private fun hasCacheQuery(query: ExerciseCacheQueryEntity): Boolean =
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM exercise_cache_query WHERE memberId = ? AND queryType = ? AND date = ?",
            arrayOf(query.memberId, query.queryType, query.date.toString()),
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) > 0
        }

    @Test
    fun `기록 없는 성공 조회 마커도 모든 회원의 기준일 이전 값만 만료한다`() =
        runTest(testDispatcher) {
            val markers =
                listOf("member-a", "member-b").flatMap { memberId ->
                    listOf("list", "count").flatMap { type ->
                        listOf(oldDate, cutoff, cutoff.plusDays(1)).map { date ->
                            ExerciseCacheQueryEntity(memberId, type, date)
                        }
                    }
                }
            markers.forEach { marker ->
                saveCacheQuery(marker)
                assertThat(hasCacheQuery(marker)).isTrue()
                assertThat(database.exerciseRecordDao().getListByDate(marker.memberId, marker.date)).isEmpty()
                assertThat(database.exerciseCountDao().getRange(marker.memberId, marker.date, marker.date)).isEmpty()
            }

            cleaner().cleanExpiredCache()

            markers.forEach { marker ->
                assertThat(hasCacheQuery(marker)).isEqualTo(!marker.date.isBefore(cutoff))
            }
        }

    @Test
    fun `월말에서 6개월을 뺀 기준일은 남기고 그 이전 횟수 목록 상세만 삭제한다`() =
        runTest(testDispatcher) {
            // 180일 전이 아니라 8월 31일에서 달력상 6개월 전인 2월 28일이 기준이다
            val dates = listOf(oldDate, cutoff, cutoff.plusDays(1), today, today.plusDays(1))
            dates.forEachIndexed { index, date ->
                insertCachedRecord(localId = date.toString(), serverId = index.toLong(), date = date)
            }

            cleaner().cleanExpiredCache()

            val expectedDates = dates.drop(1)
            assertThat(database.exerciseCountDao().getRange("member-a", oldDate, today.plusDays(1)).map { it.date })
                .containsExactlyElementsIn(expectedDates).inOrder()
            dates.forEach { date ->
                val rows = database.exerciseRecordDao().getListByDate("member-a", date)
                val savedDetail = database.exerciseRecordDao().getDetailByLocalId("member-a", date.toString())
                if (date == oldDate) {
                    assertThat(rows).isEmpty()
                    assertThat(savedDetail).isNull()
                } else {
                    assertThat(rows.map { it.localId }).containsExactly(date.toString())
                    assertThat(savedDetail).isEqualTo(detail(date.toString(), dates.indexOf(date).toLong(), date = date))
                }
            }
        }

    @Test
    fun `윤년 월말은 2월 29일을 포함해 보관한다`() =
        runTest(testDispatcher) {
            every { clockProvider.now() } returns LocalDate.parse("2024-08-31")
            val leapCutoff = LocalDate.parse("2024-02-29")
            insertCachedRecord("expired", 1L, leapCutoff.minusDays(1))
            insertCachedRecord("boundary", 2L, leapCutoff)

            cleaner().cleanExpiredCache()

            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "expired")).isNull()
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "boundary")).isNotNull()
            assertThat(database.exerciseCountDao().getRange("member-a", leapCutoff.minusDays(1), leapCutoff).map { it.date })
                .containsExactly(leapCutoff)
        }

    @Test
    fun `목록 없는 상세도 시작 시각이 기준일 자정보다 이르면 삭제한다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            dao.upsertDetail(detail("expired-detail", 1L).copy(startedAt = cutoff.atStartOfDay().minusNanos(1)))
            val boundary = detail("boundary-detail", 2L, date = cutoff)
            val future = detail("future-detail", 3L, date = today.plusDays(1))
            dao.upsertDetail(boundary)
            dao.upsertDetail(future)

            cleaner().cleanExpiredCache()

            assertThat(dao.getDetailByLocalId("member-a", "expired-detail")).isNull()
            assertThat(dao.getDetailByLocalId("member-a", "boundary-detail")).isEqualTo(boundary)
            assertThat(dao.getDetailByLocalId("member-a", "future-detail")).isEqualTo(future)
        }

    @Test
    fun `모든 대기 상태와 서버 ID 없는 목록 상세는 값 그대로 남는다`() =
        runTest(testDispatcher) {
            val states = SyncState.entries.filterNot { it == SyncState.SYNCED }
            val lists =
                states.mapIndexed { index, state -> listItem(state.name, index.toLong(), state) } +
                    listItem("local-only", null) + listItem("creating-local", null, SyncState.CREATE_PENDING)
            val details =
                states.mapIndexed { index, state -> detail(state.name, index.toLong(), state) } +
                    detail("local-only", null) + detail("creating-local", null, SyncState.CREATE_PENDING)
            val dao = database.exerciseRecordDao()
            dao.upsertListItems(lists)
            details.forEach { dao.upsertDetail(it) }

            cleaner().cleanExpiredCache()

            // excluded를 바꿔 DELETE_PENDING 행도 실제 DB에 남았는지 확인한다
            assertThat(dao.getListByDate("member-a", oldDate, excluded = SyncState.SYNCED))
                .containsExactlyElementsIn(lists.filterNot { it.syncState == SyncState.SYNCED })
            assertThat(dao.getListByDate("member-a", oldDate).single { it.localId == "local-only" })
                .isEqualTo(lists.single { it.localId == "local-only" })
            details.forEach { assertThat(dao.getDetailByLocalId("member-a", it.localId)).isEqualTo(it) }
        }

    @Test
    fun `대기 상세의 같은 로컬 ID나 서버 ID로 연결된 동기화 목록을 보존한다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            val lists = listOf(listItem("same-local", 1L), listItem("list-server", 2L))
            val details =
                listOf(
                    detail("same-local", null, SyncState.CREATE_PENDING),
                    detail("detail-server", 2L, SyncState.DELETE_PENDING),
                )
            dao.upsertListItems(lists)
            details.forEach { dao.upsertDetail(it) }

            cleaner().cleanExpiredCache()

            assertThat(dao.getListByDate("member-a", oldDate)).containsExactlyElementsIn(lists)
            details.forEach { assertThat(dao.getDetailByLocalId("member-a", it.localId)).isEqualTo(it) }
        }

    @Test
    fun `대기 목록의 같은 로컬 ID나 서버 ID로 연결된 동기화 상세를 보존한다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            val lists =
                listOf(
                    listItem("same-local", null, SyncState.CREATE_PENDING),
                    listItem("list-server", 2L, SyncState.UPDATE_PENDING),
                )
            val details = listOf(detail("same-local", 1L), detail("detail-server", 2L))
            dao.upsertListItems(lists)
            details.forEach { dao.upsertDetail(it) }

            cleaner().cleanExpiredCache()

            assertThat(dao.getListByDate("member-a", oldDate)).containsExactlyElementsIn(lists)
            details.forEach { assertThat(dao.getDetailByLocalId("member-a", it.localId)).isEqualTo(it) }
        }

    @Test
    fun `다른 로컬 ID로 남은 같은 서버 기록의 대기 행도 캐시 삭제를 막는다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            val lists = listOf(listItem("cached-list", 1L), listItem("pending-list", 1L, SyncState.DELETE_PENDING))
            val details = listOf(detail("cached-detail", 2L), detail("pending-detail", 2L, SyncState.UPDATE_PENDING))
            dao.upsertListItems(lists)
            details.forEach { dao.upsertDetail(it) }

            cleaner().cleanExpiredCache()

            assertThat(dao.getListByDate("member-a", oldDate)).containsExactly(lists.first())
            assertThat(dao.getListByDate("member-a", oldDate, excluded = SyncState.SYNCED)).containsExactly(lists.last())
            details.forEach { assertThat(dao.getDetailByLocalId("member-a", it.localId)).isEqualTo(it) }
        }

    @Test
    fun `기준일 안쪽의 짝이 있으면 오래된 목록이나 상세도 보존한다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            val oldList = listItem("old-list", 1L)
            val currentList = listItem("current-list", 2L, date = cutoff)
            val currentDetail = detail("old-list", 1L, date = cutoff)
            val oldDetail = detail("current-list", 2L)
            dao.upsertListItems(listOf(oldList, currentList))
            dao.upsertDetail(currentDetail)
            dao.upsertDetail(oldDetail)

            cleaner().cleanExpiredCache()

            assertThat(dao.getListByDate("member-a", oldDate)).containsExactly(oldList)
            assertThat(dao.getListByDate("member-a", cutoff)).containsExactly(currentList)
            assertThat(dao.getDetailByLocalId("member-a", "old-list")).isEqualTo(currentDetail)
            assertThat(dao.getDetailByLocalId("member-a", "current-list")).isEqualTo(oldDetail)
        }

    @Test
    fun `짝 보호는 회원 안에서만 적용하고 다른 회원의 오래된 캐시는 독립적으로 정리한다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            val pendingList = listItem("shared-list", 1L, SyncState.UPDATE_PENDING)
            val pendingDetail = detail("shared-detail", 2L, SyncState.DELETE_PENDING)
            dao.upsertListItems(listOf(pendingList, pendingList.copy(memberId = "member-b", syncState = SyncState.SYNCED)))
            dao.upsertDetail(pendingDetail)
            dao.upsertDetail(pendingDetail.copy(memberId = "member-b", syncState = SyncState.SYNCED))
            // 다른 회원의 대기 상세/목록도 삭제 대상의 짝으로 취급하면 안 된다
            dao.upsertDetail(detail("shared-list", 1L, memberId = "member-b"))
            dao.upsertListItems(listOf(listItem("shared-detail", 2L, memberId = "member-b")))

            cleaner().cleanExpiredCache()

            assertThat(dao.getListByDate("member-a", oldDate)).containsExactly(pendingList)
            assertThat(dao.getDetailByLocalId("member-a", "shared-detail")).isEqualTo(pendingDetail)
            assertThat(dao.getListByDate("member-b", oldDate)).isEmpty()
            assertThat(dao.getDetailByLocalId("member-b", "shared-list")).isNull()
            assertThat(dao.getDetailByLocalId("member-b", "shared-detail")).isNull()
        }

    @Test
    fun `동기화 표시라도 서버 ID 없는 짝이 있으면 양쪽 캐시를 보존한다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            val lists = listOf(listItem("local-detail", 1L), listItem("local-list", null))
            val details = listOf(detail("local-detail", null), detail("local-list", 2L))
            dao.upsertListItems(lists)
            details.forEach { dao.upsertDetail(it) }

            cleaner().cleanExpiredCache()

            assertThat(dao.getListByDate("member-a", oldDate)).containsExactlyElementsIn(lists)
            details.forEach { assertThat(dao.getDetailByLocalId("member-a", it.localId)).isEqualTo(it) }
        }

    @Test
    fun `같은 서버 ID의 보관 기간 안쪽 행은 다른 로컬 ID의 오래된 캐시도 보존한다`() =
        runTest(testDispatcher) {
            val dao = database.exerciseRecordDao()
            val oldList = listItem("old-list", 1L)
            val currentList = listItem("new-list", 1L, date = cutoff)
            val oldDetail = detail("old-detail", 2L)
            val currentDetail = detail("new-detail", 2L, date = cutoff)
            dao.upsertListItems(listOf(oldList, currentList))
            dao.upsertDetail(oldDetail)
            dao.upsertDetail(currentDetail)

            cleaner().cleanExpiredCache()

            assertThat(dao.getListByDate("member-a", oldDate)).containsExactly(oldList)
            assertThat(dao.getListByDate("member-a", cutoff)).containsExactly(currentList)
            assertThat(dao.getDetailByLocalId("member-a", "old-detail")).isEqualTo(oldDetail)
            assertThat(dao.getDetailByLocalId("member-a", "new-detail")).isEqualTo(currentDetail)
        }

    @Test
    fun `오래된 단순 커스텀 타이머는 동기화 상태와 관계없이 보존한다`() =
        runTest(testDispatcher) {
            insertCachedRecord()
            val timerDao = database.timerDao()
            val simpleTimers =
                SyncState.entries.map { state ->
                    SimpleTimerEntity(
                        memberId = "member-a",
                        localId = state.name,
                        serverId = 1L,
                        syncState = state,
                        time = 60,
                        createRequestId = "create-${state.name}",
                        pendingEditId = "edit-${state.name}",
                        editedAt = 0L,
                    )
                }
            val customTimers =
                SyncState.entries.map { state ->
                    CustomTimerEntity(
                        memberId = "member-a",
                        localId = state.name,
                        serverId = 2L,
                        syncState = state,
                        name = "타이머 ${state.name}",
                        steps = listOf(CachedTimerStep(order = 0, name = "운동", time = 30)),
                        createRequestId = "create-${state.name}",
                        pendingEditId = "edit-${state.name}",
                        editedAt = 0L,
                    )
                }
            simpleTimers.forEach { timerDao.upsertSimpleTimer(it) }
            customTimers.forEach { timerDao.upsertCustomTimer(it) }

            cleaner().cleanExpiredCache()

            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "old")).isNull()
            simpleTimers.forEach { assertThat(timerDao.getSimpleTimer("member-a", it.localId)).isEqualTo(it) }
            customTimers.forEach { assertThat(timerDao.getCustomTimer("member-a", it.localId)).isEqualTo(it) }
        }

    @Test
    fun `마지막 삭제가 실패하면 횟수 목록 상세와 조회 마커 삭제가 모두 롤백된다`() =
        runTest(testDispatcher) {
            insertCachedRecord()
            val countDao = database.exerciseCountDao()
            val expected = IllegalStateException("정리 실패")
            val failingDao =
                object : ExerciseCountDao by countDao {
                    override suspend fun deleteBefore(cutoff: LocalDate) {
                        countDao.deleteBefore(cutoff)
                        throw expected
                    }
                }

            val failure = runCatching { cleaner(failingDao).cleanExpiredCache() }.exceptionOrNull()

            assertThat(failure).isSameInstanceAs(expected)
            assertThat(database.exerciseCountDao().getRange("member-a", oldDate, oldDate))
                .containsExactly(ExerciseCountEntity("member-a", oldDate, 1))
            assertThat(database.exerciseRecordDao().getListByDate("member-a", oldDate)).containsExactly(listItem("old", 1L))
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "old")).isEqualTo(detail("old", 1L))
            assertThat(hasCacheQuery(ExerciseCacheQueryEntity("member-a", "list", oldDate))).isTrue()
            assertThat(hasCacheQuery(ExerciseCacheQueryEntity("member-a", "count", oldDate))).isTrue()
        }

    @Test
    fun `정리 도중 취소는 전파하고 이미 실행한 삭제도 롤백한다`() =
        runTest(testDispatcher) {
            insertCachedRecord()
            val countDao = database.exerciseCountDao()
            val expected = CancellationException("정리 취소")
            val cancellingDao =
                object : ExerciseCountDao by countDao {
                    override suspend fun deleteBefore(cutoff: LocalDate) {
                        countDao.deleteBefore(cutoff)
                        throw expected
                    }
                }

            val failure = runCatching { cleaner(cancellingDao).cleanExpiredCache() }.exceptionOrNull()

            assertThat(failure).isInstanceOf(CancellationException::class.java)
            assertThat(database.exerciseCountDao().getRange("member-a", oldDate, oldDate))
                .containsExactly(ExerciseCountEntity("member-a", oldDate, 1))
            assertThat(database.exerciseRecordDao().getListByDate("member-a", oldDate)).containsExactly(listItem("old", 1L))
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", "old")).isEqualTo(detail("old", 1L))
            assertThat(hasCacheQuery(ExerciseCacheQueryEntity("member-a", "list", oldDate))).isTrue()
            assertThat(hasCacheQuery(ExerciseCacheQueryEntity("member-a", "count", oldDate))).isTrue()
        }
}
