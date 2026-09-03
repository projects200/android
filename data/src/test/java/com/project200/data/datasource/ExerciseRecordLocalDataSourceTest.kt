package com.project200.data.datasource

import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.dao.ExerciseCountDao
import com.project200.data.local.dao.ExerciseRecordDao
import com.project200.data.local.dao.ServerIdLocalId
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseListItem
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class ExerciseRecordLocalDataSourceTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var database: UndabangDatabase

    @MockK
    private lateinit var exerciseCountDao: ExerciseCountDao

    @MockK(relaxUnitFun = true)
    private lateinit var exerciseRecordDao: ExerciseRecordDao

    @MockK
    private lateinit var preferenceManager: PreferenceManager

    private lateinit var dataSource: ExerciseRecordLocalDataSource

    @Before
    fun setUp() {
        // withTransaction은 RoomDatabase 확장 함수라 블록을 그대로 실행하도록 바꿉니다
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transaction = slot<suspend () -> Any>()
        coEvery { database.withTransaction(capture(transaction)) } coAnswers { transaction.captured.invoke() }
        every { preferenceManager.getMemberId() } returns MEMBER_ID

        coEvery { exerciseRecordDao.getPendingServerIds(any(), any(), any()) } returns emptyList()
        coEvery { exerciseRecordDao.getSyncedByServerIds(any(), any(), any()) } returns emptyList()
        coEvery { exerciseRecordDao.getSyncedByDate(any(), any(), any()) } returns emptyList()

        dataSource =
            ExerciseRecordLocalDataSource(database, exerciseCountDao, exerciseRecordDao, preferenceManager)
    }

    @Test
    fun `전송 대기 행이 걸린 기록은 서버 값으로 덮이지 않는다`() =
        runTest {
            // Given: 기록 2는 아직 못 올린 로컬 변경이 걸려 있다
            coEvery { exerciseRecordDao.getPendingServerIds(MEMBER_ID, DATE, SyncState.SYNCED) } returns listOf(2L)
            val captured = slot<List<com.project200.data.local.entity.ExerciseListItemEntity>>()
            coEvery { exerciseRecordDao.upsertListItems(capture(captured)) } just Runs

            // When
            dataSource.replaceSyncedListByDate(DATE, listOf(itemOf(1L), itemOf(2L), itemOf(3L)))

            // Then: 서버가 준 기록 2는 반영 대상에서 빠진다
            assertThat(captured.captured.map { it.serverId }).containsExactly(1L, 3L)
        }

    @Test
    fun `이미 캐시된 기록은 localId를 이어 쓴다`() =
        runTest {
            // Given
            coEvery { exerciseRecordDao.getSyncedByServerIds(MEMBER_ID, listOf(1L), SyncState.SYNCED) } returns
                listOf(ServerIdLocalId(serverId = 1L, localId = "keep-me"))
            val captured = slot<List<com.project200.data.local.entity.ExerciseListItemEntity>>()
            coEvery { exerciseRecordDao.upsertListItems(capture(captured)) } just Runs

            // When
            dataSource.replaceSyncedListByDate(DATE, listOf(itemOf(1L)))

            // Then
            assertThat(captured.captured.single().localId).isEqualTo("keep-me")
        }

    @Test
    fun `처음 보는 기록은 새 localId를 받는다`() =
        runTest {
            // Given
            val captured = slot<List<com.project200.data.local.entity.ExerciseListItemEntity>>()
            coEvery { exerciseRecordDao.upsertListItems(capture(captured)) } just Runs

            // When
            dataSource.replaceSyncedListByDate(DATE, listOf(itemOf(9L)))

            // Then
            assertThat(captured.captured.single().localId).isNotEmpty()
            assertThat(captured.captured.single().syncState).isEqualTo(SyncState.SYNCED)
        }

    @Test
    fun `서버 목록에서 빠진 기록은 상세도 지운다`() =
        runTest {
            // Given: 캐시에는 1, 2가 있는데 서버는 1만 준다
            coEvery { exerciseRecordDao.getSyncedByDate(MEMBER_ID, DATE, SyncState.SYNCED) } returns
                listOf(ServerIdLocalId(1L, "local-1"), ServerIdLocalId(2L, "local-2"))
            coEvery { exerciseRecordDao.upsertListItems(any()) } just Runs

            // When
            dataSource.replaceSyncedListByDate(DATE, listOf(itemOf(1L)))

            // Then
            coVerify(exactly = 1) { exerciseRecordDao.deleteDetailsByLocalIds(MEMBER_ID, listOf("local-2")) }
        }

    @Test
    fun `회원ID가 없으면 아무것도 쓰지 않는다`() =
        runTest {
            // Given
            every { preferenceManager.getMemberId() } returns null

            // When
            dataSource.replaceSyncedListByDate(DATE, listOf(itemOf(1L)))

            // Then
            coVerify(exactly = 0) { exerciseRecordDao.upsertListItems(any()) }
            coVerify(exactly = 0) { exerciseRecordDao.deleteSyncedListByDate(any(), any(), any()) }
        }

    private fun itemOf(recordId: Long) =
        ExerciseListItem(
            recordId = recordId,
            title = "달리기",
            type = "PERSONAL",
            startTime = LocalDateTime.parse("2026-08-17T10:00:00"),
            endTime = LocalDateTime.parse("2026-08-17T11:00:00"),
            imageUrl = null,
        )

    companion object {
        private const val MEMBER_ID = "member-a"
        private val DATE: LocalDate = LocalDate.parse("2026-08-17")
    }
}
