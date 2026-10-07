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

    // ── 전송 상태 전이 ────────────────────────────────

    @Test
    fun `삭제 - 전송 중인 생성은 즉시 지우지 않고 삭제 대기로 둔다`() =
        runTest {
            // Given: 생성 전송이 시작돼 서버에 행이 생길 수 있는 상태
            val localId = dataSource.createSimpleTimer(time = 60)!!
            dataSource.startSimpleTimerSync(localId)

            // When
            dataSource.deleteSimpleTimer(localId)

            // Then: 즉시 지우면 생성이 성공했을 때 서버에 고아 행이 남는다
            val pending = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(pending.syncState).isEqualTo(SyncState.DELETE_PENDING)
        }

    @Test
    fun `삭제 - 전송 중인 생성을 지운 뒤 생성이 성공하면 serverId가 붙고 삭제 대기가 남는다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            dataSource.startSimpleTimerSync(localId)
            dataSource.deleteSimpleTimer(localId)

            // When: 진행 중이던 생성이 서버에서 성공한다
            dataSource.finishSimpleTimerSync(localId, serverId = 7L)

            // Then: 다음 전송이 삭제를 보낼 수 있어야 한다
            val row = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(row.serverId).isEqualTo(7L)
            assertThat(row.syncState).isEqualTo(SyncState.DELETE_PENDING)
        }

    @Test
    fun `삭제 - 전송 전인 생성 대기 행은 즉시 지운다`() =
        runTest {
            // Given: 서버가 모르는 행이라 삭제를 알릴 필요가 없다
            val localId = dataSource.createSimpleTimer(time = 60)!!

            // When
            dataSource.deleteSimpleTimer(localId)

            // Then
            assertThat(database.timerDao().getSimpleTimer(MEMBER_ID, localId)).isNull()
        }

    @Test
    fun `전송 시작 - 대기 행의 현재 값이 전송 중 칸에 복사된다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            val before = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!

            // When
            val started = dataSource.startSimpleTimerSync(localId)!!

            // Then
            assertThat(started.inFlightEditId).isEqualTo(before.pendingEditId)
            assertThat(started.inFlightEditedAt).isEqualTo(before.editedAt)
            assertThat(started.inFlightOperation).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(started.inFlightTime).isEqualTo(60)
            assertThat(started.lastSyncError).isNull()
        }

    @Test
    fun `전송 시작 - 동기화 완료 행은 null을 돌린다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)

            // When & Then: 보낼 것이 없다
            assertThat(dataSource.startSimpleTimerSync(localId)).isNull()
        }

    @Test
    fun `전송 시작 - 이미 전송 중인 행은 다시 복사하지 않고 고정한 값을 돌린다`() =
        runTest {
            // Given: 서버에 있는 행을 수정해 전송을 한 번 시작한다
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)
            dataSource.updateSimpleTimer(localId, time = 90)
            val started = dataSource.startSimpleTimerSync(localId)!!

            // When: 전송 중에 사용자가 또 수정하고 다시 전송을 시작한다
            dataSource.updateSimpleTimer(localId, time = 120)
            val retried = dataSource.startSimpleTimerSync(localId)!!

            // Then: 고정된 전송 중 칸은 그대로고, 새 수정은 pendingEditId에만 반영된다
            assertThat(retried.inFlightTime).isEqualTo(started.inFlightTime)
            assertThat(retried.inFlightEditId).isEqualTo(started.inFlightEditId)
            assertThat(retried.time).isEqualTo(120)
            assertThat(retried.pendingEditId).isNotEqualTo(retried.inFlightEditId)
        }

    @Test
    fun `전송 성공 - 생성이면 serverId를 기록하고 동기화 완료로 바뀐다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            dataSource.startSimpleTimerSync(localId)

            // When
            dataSource.finishSimpleTimerSync(localId, serverId = 7L)

            // Then
            val finished = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(finished.serverId).isEqualTo(7L)
            assertThat(finished.syncState).isEqualTo(SyncState.SYNCED)
            assertThat(finished.inFlightEditId).isNull()
            assertThat(finished.inFlightOperation).isNull()
            assertThat(finished.inFlightTime).isNull()
        }

    @Test
    fun `전송 성공 - 삭제면 행을 지운다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)
            dataSource.deleteSimpleTimer(localId)
            dataSource.startSimpleTimerSync(localId)

            // When
            dataSource.finishSimpleTimerSync(localId, serverId = null)

            // Then
            assertThat(database.timerDao().getSimpleTimer(MEMBER_ID, localId)).isNull()
        }

    @Test
    fun `전송 성공 - 전송 중에 사용자가 다시 수정하면 대기를 유지하고 전송 중 칸만 버린다`() =
        runTest {
            // Given: 서버에 있는 행을 수정해 전송을 시작한 뒤, 전송 중에 또 수정한다
            val localId = dataSource.createSimpleTimer(time = 60)!!
            markSynced(localId, serverId = 7L)
            dataSource.updateSimpleTimer(localId, time = 90)
            dataSource.startSimpleTimerSync(localId)
            dataSource.updateSimpleTimer(localId, time = 120)

            // When: 먼저 시작한 전송(90)이 성공한다
            dataSource.finishSimpleTimerSync(localId, serverId = null)

            // Then: 이어서 보낼 편집(120)이 남아 대기 상태를 유지한다
            val result = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(result.syncState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(result.time).isEqualTo(120)
            assertThat(result.inFlightEditId).isNull()
            assertThat(result.inFlightOperation).isNull()
        }

    @Test
    fun `전송 성공 - 생성 직후 수정이 들어오면 serverId는 기록되고 수정 대기로 남는다`() =
        runTest {
            // Given: 생성 대기 행의 전송을 시작한 뒤, 서버 응답을 받기 전에 사용자가 다시 수정한다
            val localId = dataSource.createSimpleTimer(time = 60)!!
            dataSource.startSimpleTimerSync(localId)
            dataSource.updateSimpleTimer(localId, time = 90)

            // When: 먼저 시작한 생성 요청이 serverId를 들고 성공한다
            dataSource.finishSimpleTimerSync(localId, serverId = 7L)

            // Then: serverId가 생겨 더는 생성 대기가 아니므로 수정 대기로 올라간다.
            // updateSimpleTimer 시점에는 서버가 이 행을 몰라 생성 대기를 유지했지만(nextStateOnEdit),
            // 전송 성공으로 serverId가 확정되면 다음 전송은 생성이 아니라 수정이어야 하므로
            // finishSimpleTimerSync가 이 승격을 책임진다
            val result = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(result.serverId).isEqualTo(7L)
            assertThat(result.syncState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(result.time).isEqualTo(90)
        }

    @Test
    fun `전송 실패 - lastSyncError가 기록되고 전송 중 칸은 유지된다`() =
        runTest {
            // Given
            val localId = dataSource.createSimpleTimer(time = 60)!!
            val started = dataSource.startSimpleTimerSync(localId)!!

            // When
            dataSource.recordSimpleTimerSyncError(localId, "네트워크 오류")

            // Then
            val result = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(result.lastSyncError).isEqualTo("네트워크 오류")
            assertThat(result.inFlightEditId).isEqualTo(started.inFlightEditId)
            assertThat(result.inFlightOperation).isEqualTo(started.inFlightOperation)
            assertThat(result.inFlightTime).isEqualTo(started.inFlightTime)
        }

    @Test
    fun `커스텀 전송 시작 - 대기 행의 현재 값이 전송 중 칸에 복사된다`() =
        runTest {
            // Given
            val localId = dataSource.createCustomTimer(name = "8세트", steps = steps)!!

            // When
            val started = dataSource.startCustomTimerSync(localId)!!

            // Then
            assertThat(started.inFlightOperation).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(started.inFlightName).isEqualTo("8세트")
            assertThat(started.inFlightSteps).isEqualTo(steps)
        }

    @Test
    fun `커스텀 전송 성공 - 생성이면 serverId를 기록하고 동기화 완료로 바뀐다`() =
        runTest {
            // Given
            val localId = dataSource.createCustomTimer(name = "8세트", steps = steps)!!
            dataSource.startCustomTimerSync(localId)

            // When
            dataSource.finishCustomTimerSync(localId, serverId = 3L)

            // Then
            val finished = database.timerDao().getCustomTimer(MEMBER_ID, localId)!!
            assertThat(finished.serverId).isEqualTo(3L)
            assertThat(finished.syncState).isEqualTo(SyncState.SYNCED)
            assertThat(finished.inFlightOperation).isNull()
            assertThat(finished.inFlightName).isNull()
            assertThat(finished.inFlightSteps).isNull()
        }

    @Test
    fun `커스텀 전송 실패 - lastSyncError가 기록되고 전송 중 칸은 유지된다`() =
        runTest {
            // Given
            val localId = dataSource.createCustomTimer(name = "8세트", steps = steps)!!
            val started = dataSource.startCustomTimerSync(localId)!!

            // When
            dataSource.recordCustomTimerSyncError(localId, "네트워크 오류")

            // Then
            val result = database.timerDao().getCustomTimer(MEMBER_ID, localId)!!
            assertThat(result.lastSyncError).isEqualTo("네트워크 오류")
            assertThat(result.inFlightName).isEqualTo(started.inFlightName)
            assertThat(result.inFlightSteps).isEqualTo(started.inFlightSteps)
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

    @Test
    fun `계정 경계 - 다른 계정의 행은 전송 시작도 성공 처리도 되지 않는다`() =
        runTest {
            // Given: member-a로 만든 행이 있다
            val localId = dataSource.createSimpleTimer(time = 60)!!
            every { preferenceManager.getMemberId() } returns "member-b"

            // When: member-b로 전송을 시작하고 끝낸다
            val started = dataSource.startSimpleTimerSync(localId)
            dataSource.finishSimpleTimerSync(localId, serverId = 7L)

            // Then: 다른 계정의 행이라 아무 일도 일어나지 않는다
            assertThat(started).isNull()
            every { preferenceManager.getMemberId() } returns MEMBER_ID
            val untouched = database.timerDao().getSimpleTimer(MEMBER_ID, localId)!!
            assertThat(untouched.syncState).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(untouched.serverId).isNull()
            assertThat(untouched.inFlightOperation).isNull()
        }

    companion object {
        private const val MEMBER_ID = "member-a"
    }
}
