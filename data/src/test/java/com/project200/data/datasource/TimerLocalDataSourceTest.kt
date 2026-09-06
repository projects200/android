package com.project200.data.datasource

import com.google.common.truth.Truth.assertThat
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.SyncState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 타이머 저장층의 상태 전이를 메모리 DB로 검증합니다.
 *
 * 전이를 틀리면 서버에 없는 타이머에 수정 요청이 나가거나 삭제가 서버에 전달되지 않습니다
 */
@RunWith(RobolectricTestRunner::class)
class TimerLocalDataSourceTest {
    private lateinit var database: UndabangDatabase
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var dataSource: TimerLocalDataSource

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        preferenceManager = mockk()
        every { preferenceManager.getMemberId() } returns MEMBER_ID
        dataSource = TimerLocalDataSource(database, database.timerDao(), preferenceManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private val steps =
        listOf(
            CachedTimerStep(order = 1, name = "준비", time = 30),
            CachedTimerStep(order = 2, name = "운동", time = 60),
        )

    private suspend fun markSynced(
        localId: String,
        serverId: Long,
    ) {
        val timer = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
        database.timerDao().upsertSimpleTimer(
            timer.copy(serverId = serverId, syncState = SyncState.SYNCED, pendingEditId = null),
        )
    }

    private suspend fun markCustomSynced(
        localId: String,
        serverId: Long,
    ) {
        val timer = database.timerDao().getCustomTimer(MEMBER_ID, localId)!!
        database.timerDao().upsertCustomTimer(
            timer.copy(serverId = serverId, syncState = SyncState.SYNCED, pendingEditId = null),
        )
    }

    // ── 심플 타이머 ────────────────────────────────

    @Test
    fun `생성 - 생성 대기 상태와 요청 식별자를 남긴다`() =
        runTest {
            // When
            val localId = dataSource.createSimpleTimer(time = 60)!!

            // Then
            val saved = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(saved.syncState).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(saved.serverId).isNull()
            assertThat(saved.createRequestId).isNotEmpty()
            assertThat(saved.editedAt).isGreaterThan(0L)
        }

    @Test
    fun `수정 - 생성 대기 행은 생성 대기를 유지한다`() =
        runTest {
            // Given: 아직 서버에 없는 타이머
            val localId = dataSource.createSimpleTimer(time = 60)!!
            val created = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!

            // When
            dataSource.updateSimpleTimer(localId, time = 90)

            // Then: 수정 대기로 바꾸면 서버에 없는 항목에 수정 요청이 나간다
            val updated = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(updated.syncState).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(updated.time).isEqualTo(90)
            assertThat(updated.createRequestId).isEqualTo(created.createRequestId)
            assertThat(updated.pendingEditId).isNotNull()
        }

    @Test
    fun `수정 - 서버에 있는 행은 수정 대기가 된다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)

            // When
            dataSource.updateSimpleTimer(localId, time = 90)

            // Then
            val updated = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(updated.syncState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(updated.serverId).isEqualTo(7L)
            assertThat(updated.pendingEditId).isNotNull()
        }

    @Test
    fun `삭제 - 서버에 없는 행은 즉시 지운다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!

            // When
            dataSource.deleteSimpleTimer(localId)

            // Then: 서버가 모르는 행이라 삭제를 알릴 필요가 없다
            assertThat(database.timerDao().getSimpleTimer(MEMBER_ID, localId)).isNull()
        }

    @Test
    fun `삭제 - 서버에 있는 행은 삭제 대기로 남는다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)

            // When
            dataSource.deleteSimpleTimer(localId)

            // Then
            val deleted = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(deleted.syncState).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(dataSource.getSimpleTimers()).isEmpty()
        }

    @Test
    fun `개수 - 삭제 대기 행은 세지 않는다`() =
        runTest {
            // Given
            dataSource.createSimpleTimer(time = 30)
            val toDelete = dataSource.createSimpleTimer(time = 60)!!
            markSynced(toDelete, serverId = 7L)
            dataSource.deleteSimpleTimer(toDelete)

            // When & Then: 최대 6개 판정에 삭제 대기가 끼면 안 된다
            assertThat(dataSource.countSimpleTimers()).isEqualTo(1)
        }

    @Test
    fun `목록 - 시간 오름차순으로 나온다`() =
        runTest {
            // Given
            dataSource.createSimpleTimer(time = 90)
            dataSource.createSimpleTimer(time = 30)

            // When & Then
            assertThat(dataSource.getSimpleTimers().map { it.time }).containsExactly(30, 90).inOrder()
        }

    // ── 커스텀 타이머 ────────────────────────────────

    @Test
    fun `커스텀 생성 - 스텝 순서가 유지된다`() =
        runTest {
            // When
            val localId = dataSource.createCustomTimer(name = "8세트", steps = steps)!!

            // Then
            val saved = dataSource.getCustomTimer(localId)!!
            assertThat(saved.syncState).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(saved.steps.map { it.name }).containsExactly("준비", "운동").inOrder()
        }

    @Test
    fun `커스텀 수정 - 스텝을 통째로 교체한다`() =
        runTest {
            // Given
            val localId = dataSource.createCustomTimer(name = "8세트", steps = steps)!!

            // When
            dataSource.updateCustomTimer(
                localId,
                name = "10세트",
                steps = listOf(CachedTimerStep(order = 1, name = "전력", time = 20)),
            )

            // Then: 이름과 스텝 목록이 판정 단위라 항목 단위로 병합하지 않는다
            val updated = dataSource.getCustomTimer(localId)!!
            assertThat(updated.name).isEqualTo("10세트")
            assertThat(updated.steps).hasSize(1)
        }

    @Test
    fun `대기 행 조회 - 동기화 완료 행은 빠진다`() =
        runTest {
            // Given
            val pending = dataSource.createSimpleTimer(time = 30)!!
            val synced = dataSource.createSimpleTimer(time = 60)!!
            markSynced(synced, serverId = 7L)

            // When & Then
            assertThat(dataSource.getPendingSimpleTimers().map { it.localId }).containsExactly(pending)
        }

    // ── 서버 목록 반영 ────────────────────────────────

    @Test
    fun `심플 서버 반영 - 대기 행은 서버 값으로 덮이지 않는다`() =
        runTest {
            // Given: 서버에도 있는 행인데 로컬에서 아직 못 올린 수정이 걸려 있다
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)
            dataSource.updateSimpleTimer(localId, time = 999)

            // When: 서버는 옛 값을 그대로 들고 있다
            dataSource.replaceSyncedSimpleTimers(listOf(ServerSimpleTimer(serverId = 7L, time = 60)))

            // Then
            val kept = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(kept.syncState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(kept.time).isEqualTo(999)
        }

    @Test
    fun `심플 서버 반영 - 동기화 완료 행은 localId를 이어 쓴다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)

            // When: 서버 값이 바뀌어 내려온다
            dataSource.replaceSyncedSimpleTimers(listOf(ServerSimpleTimer(serverId = 7L, time = 120)))

            // Then
            val updated = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(updated.time).isEqualTo(120)
            assertThat(updated.syncState).isEqualTo(SyncState.SYNCED)
            assertThat(dataSource.getSimpleTimers()).hasSize(1)
        }

    @Test
    fun `심플 서버 반영 - 서버 목록에서 사라진 동기화 완료 행은 지운다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)

            // When: 서버 목록이 비어 있다
            dataSource.replaceSyncedSimpleTimers(emptyList())

            // Then
            assertThat(database.timerDao().getSimpleTimer(MEMBER_ID, localId)).isNull()
        }

    @Test
    fun `심플 서버 반영 - 새 서버 행은 새 localId로 추가된다`() =
        runTest {
            // When
            dataSource.replaceSyncedSimpleTimers(listOf(ServerSimpleTimer(serverId = 9L, time = 45)))

            // Then
            val saved = dataSource.getSimpleTimers().single()
            assertThat(saved.serverId).isEqualTo(9L)
            assertThat(saved.time).isEqualTo(45)
            assertThat(saved.syncState).isEqualTo(SyncState.SYNCED)
        }

    @Test
    fun `커스텀 서버 반영 - 대기 행을 지키고 새 행은 스텝과 함께 들어온다`() =
        runTest {
            // Given: 삭제 대기 중인 행은 서버 목록에서 빠져도 지워지면 안 된다
            val pendingLocalId = dataSource.createCustomTimer(name = "8세트", steps = steps)!!
            markCustomSynced(pendingLocalId, serverId = 3L)
            dataSource.deleteCustomTimer(pendingLocalId)
            val newSteps = listOf(CachedTimerStep(order = 1, name = "전력", time = 20))

            // When
            dataSource.replaceSyncedCustomTimers(
                listOf(ServerCustomTimer(serverId = 10L, name = "새 타이머", steps = newSteps)),
            )

            // Then: 삭제 대기 행은 그대로, 새 행은 스텝까지 반영된다
            val kept = database.timerDao().getCustomTimer(MEMBER_ID, pendingLocalId)!!
            assertThat(kept.syncState).isEqualTo(SyncState.DELETE_PENDING)
            val inserted = dataSource.getCustomTimers().single { it.serverId == 10L }
            assertThat(inserted.name).isEqualTo("새 타이머")
            assertThat(inserted.steps).isEqualTo(newSteps)
            assertThat(inserted.syncState).isEqualTo(SyncState.SYNCED)
        }

    // ── 계정 경계 ────────────────────────────────

    @Test
    fun `회원ID가 없으면 쓰지 않고 읽으면 빈 값이다`() =
        runTest {
            // Given
            every { preferenceManager.getMemberId() } returns null

            // When
            val localId = dataSource.createSimpleTimer(time = 60)

            // Then
            assertThat(localId).isNull()
            assertThat(dataSource.getSimpleTimers()).isEmpty()
            assertThat(dataSource.countSimpleTimers()).isEqualTo(0)
        }

    @Test
    fun `다른 계정의 타이머는 보이지 않는다`() =
        runTest {
            // Given
            dataSource.createSimpleTimer(time = 60)
            every { preferenceManager.getMemberId() } returns "member-b"

            // When & Then
            assertThat(dataSource.getSimpleTimers()).isEmpty()
        }

    companion object {
        private const val MEMBER_ID = "member-a"
    }
}
