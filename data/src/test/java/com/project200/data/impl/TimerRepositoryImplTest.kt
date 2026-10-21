package com.project200.data.impl

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ServerCustomTimer
import com.project200.data.datasource.ServerSimpleTimer
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.datasource.TimerSyncSnapshot
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.CustomTimerDetailStepDTO
import com.project200.data.dto.CustomTimerSummaryDTO
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerDTO
import com.project200.data.dto.SimpleTimerIdDTO
import com.project200.data.dto.SimpleTimerRequest
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.CustomTimerEntity
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toModel
import com.project200.domain.manager.TimerSyncScheduler
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Step
import com.project200.domain.model.TimerSyncResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

/**
 * 서버 반영 3경로(성공, NETWORK_ERROR 로컬 폴백, 그 밖 오류 전달)와 쓰기 위임을 검증합니다.
 *
 * ApiService와 TimerLocalDataSource를 모두 목으로 대신해 저장층 세부 구현과 분리합니다
 */
@ExperimentalCoroutinesApi
class TimerRepositoryImplTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var apiService: ApiService

    @MockK
    private lateinit var timerSyncScheduler: TimerSyncScheduler

    @MockK
    private lateinit var localDataSource: TimerLocalDataSource

    private lateinit var repository: TimerRepositoryImpl

    @Before
    fun setUp() {
        every { timerSyncScheduler.schedule() } just Runs
        every { localDataSource.currentMemberId() } returns MEMBER_ID
        every { localDataSource.isCurrentMember(MEMBER_ID) } returns true
        coEvery { localDataSource.getPendingSimpleTimers() } returns emptyList()
        coEvery { localDataSource.getPendingCustomTimers() } returns emptyList()
        repository = TimerRepositoryImpl(apiService, localDataSource, UnconfinedTestDispatcher(), timerSyncScheduler)
    }

    private fun simpleEntity(
        localId: String,
        serverId: Long?,
        time: Int,
        syncState: SyncState = SyncState.SYNCED,
    ) = SimpleTimerEntity(
        memberId = MEMBER_ID,
        localId = localId,
        serverId = serverId,
        syncState = syncState,
        time = time,
        createRequestId = "req",
        pendingEditId = null,
        editedAt = 0L,
    )

    private fun customEntity(
        localId: String,
        serverId: Long?,
        name: String,
        steps: List<CachedTimerStep>,
        syncState: SyncState = SyncState.SYNCED,
    ) = CustomTimerEntity(
        memberId = MEMBER_ID,
        localId = localId,
        serverId = serverId,
        syncState = syncState,
        name = name,
        steps = steps,
        createRequestId = "req",
        pendingEditId = null,
        editedAt = 0L,
    )

    // ── 심플 타이머 조회 ────────────────────────────────

    @Test
    fun `심플 목록 조회 - 전송 대기 행을 먼저 올린 뒤 서버 목록을 반영한다`() =
        runTest {
            // Given
            val createRequestId = "00000000-0000-0000-0000-000000000001"
            val pending =
                simpleEntity("local-1", null, 60, SyncState.CREATE_PENDING)
                    .copy(createRequestId = createRequestId)
            val snapshot =
                TimerSyncSnapshot.Simple(
                    localId = "local-1",
                    serverId = null,
                    state = SyncState.CREATE_PENDING,
                    editId = null,
                    editedAt = 123L,
                    createRequestId = createRequestId,
                    time = 60,
                )
            coEvery { localDataSource.getPendingSimpleTimers() } returns listOf(pending)
            coEvery { localDataSource.claimSimpleTimer(MEMBER_ID, "local-1") } returns snapshot
            coEvery { apiService.postSimpleTimer(any()) } returns
                BaseResponse(
                    succeed = true,
                    code = "200",
                    message = "",
                    data = SimpleTimerIdDTO(7L),
                )
            coEvery { localDataSource.completeSimple(snapshot, 7L) } returns Unit
            coEvery { apiService.getSimpleTimers() } returns
                BaseResponse(
                    succeed = true,
                    code = "200",
                    message = "",
                    data = GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(8L, 90))),
                )
            coEvery { localDataSource.replaceSyncedSimpleTimers(any()) } returns Unit
            coEvery { localDataSource.getSimpleTimers() } returns listOf(pending.copy(serverId = 7L))

            // When
            repository.getSimpleTimers()

            // Then
            coVerifyOrder {
                localDataSource.claimSimpleTimer(MEMBER_ID, "local-1")
                apiService.postSimpleTimer(
                    SimpleTimerRequest(
                        time = 60,
                        createRequestId = UUID.fromString(createRequestId),
                        editedAt = 123L,
                    ),
                )
                localDataSource.completeSimple(snapshot, 7L)
                apiService.getSimpleTimers()
                localDataSource.replaceSyncedSimpleTimers(listOf(ServerSimpleTimer(8L, 90)))
            }
        }

    @Test
    fun `심플 목록 조회 - 성공하면 서버 값을 반영한 뒤 로컬 스냅샷을 돌려준다`() =
        runTest {
            // Given
            coEvery { apiService.getSimpleTimers() } returns
                BaseResponse(succeed = true, code = "200", message = "", data = GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(7L, 60))))
            coEvery { localDataSource.replaceSyncedSimpleTimers(any()) } returns Unit
            val localSnapshot = listOf(simpleEntity("local-1", 7L, 60))
            coEvery { localDataSource.getSimpleTimers() } returns localSnapshot

            // When
            val result = repository.getSimpleTimers()

            // Then
            coVerify(exactly = 1) { localDataSource.replaceSyncedSimpleTimers(listOf(ServerSimpleTimer(7L, 60))) }
            assertThat(result).isEqualTo(BaseResult.Success(localSnapshot.map { it.toModel() }))
        }

    @Test
    fun `심플 목록 조회 - NETWORK_ERROR면 로컬 스냅샷을 성공으로 돌려준다`() =
        runTest {
            // Given
            coEvery { apiService.getSimpleTimers() } throws IOException("오프라인")
            val localSnapshot = listOf(simpleEntity("local-1", null, 30, SyncState.CREATE_PENDING))
            coEvery { localDataSource.getSimpleTimers() } returns localSnapshot

            // When
            val result = repository.getSimpleTimers()

            // Then
            coVerify(exactly = 0) { localDataSource.replaceSyncedSimpleTimers(any()) }
            assertThat(result).isEqualTo(BaseResult.Success(localSnapshot.map { it.toModel() }))
        }

    @Test
    fun `심플 목록 조회 - 그 밖 오류는 그대로 전달한다`() =
        runTest {
            // Given
            coEvery { apiService.getSimpleTimers() } returns
                BaseResponse(succeed = false, code = "SERVER_ERROR", message = "서버 오류")

            // When
            val result = repository.getSimpleTimers()

            // Then
            coVerify(exactly = 0) { localDataSource.replaceSyncedSimpleTimers(any()) }
            assertThat(result).isEqualTo(BaseResult.Error(errorCode = "SERVER_ERROR", message = "서버 오류"))
        }

    @Test
    fun `심플 목록 조회 - 로컬 전용 조회는 서버를 보지 않는다`() =
        runTest {
            // Given
            val localSnapshot = listOf(simpleEntity("local-1", 7L, 60))
            coEvery { localDataSource.getSimpleTimers() } returns localSnapshot

            // When
            val result = repository.getLocalSimpleTimers()

            // Then
            coVerify(exactly = 0) { apiService.getSimpleTimers() }
            assertThat(result).isEqualTo(BaseResult.Success(localSnapshot.map { it.toModel() }))
        }

    @Test
    fun `동기화 성공 뒤 남은 pending은 성공으로 종료하지 않는다`() =
        runTest {
            // Given
            val pending = simpleEntity("local-1", null, 60, SyncState.CREATE_PENDING)
            val snapshot =
                TimerSyncSnapshot.Simple(
                    localId = "local-1",
                    serverId = null,
                    state = SyncState.CREATE_PENDING,
                    editId = null,
                    editedAt = 0L,
                    createRequestId = pending.createRequestId,
                    time = 60,
                )
            every { localDataSource.isCurrentMember(MEMBER_ID) } returns true
            coEvery { localDataSource.getPendingSimpleTimers() } returnsMany
                listOf(listOf(pending), emptyList(), emptyList(), listOf(pending))
            coEvery { localDataSource.getPendingCustomTimers() } returns emptyList()
            coEvery { localDataSource.claimSimpleTimer(MEMBER_ID, "local-1") } returns snapshot
            coEvery { apiService.postSimpleTimer(any()) } returns
                BaseResponse(
                    succeed = true,
                    code = "200",
                    message = "",
                    data = SimpleTimerIdDTO(7L),
                )
            coEvery { localDataSource.completeSimple(snapshot, 7L) } returns Unit

            // When
            val result = repository.syncPendingTimers(MEMBER_ID)

            // Then
            assertThat(result).isEqualTo(TimerSyncResult.Retry)
        }
    // ── 심플 타이머 쓰기 ────────────────────────────────

    @Test
    fun `심플 추가 - localId를 돌려준다`() =
        runTest {
            // Given
            coEvery { localDataSource.createSimpleTimer(60) } returns "new-local-id"

            // When
            val result = repository.addSimpleTimer(60)

            // Then
            assertThat(result).isEqualTo(BaseResult.Success("new-local-id"))
        }

    @Test
    fun `심플 추가 - 회원ID가 없으면 NO_MEMBER_ID를 돌려준다`() =
        runTest {
            // Given
            coEvery { localDataSource.createSimpleTimer(60) } returns null

            // When
            val result = repository.addSimpleTimer(60) as BaseResult.Error

            // Then
            assertThat(result.errorCode).isEqualTo("NO_MEMBER_ID")
        }

    @Test
    fun `심플 수정 - 로컬에 위임하고 성공을 돌려준다`() =
        runTest {
            // Given
            coEvery { localDataSource.updateSimpleTimer("local-1", 90) } returns Unit

            // When
            val result = repository.editSimpleTimer("local-1", 90)

            // Then
            coVerify(exactly = 1) { localDataSource.updateSimpleTimer("local-1", 90) }
            assertThat(result).isEqualTo(BaseResult.Success(Unit))
        }

    @Test
    fun `심플 삭제 - 로컬에 위임하고 성공을 돌려준다`() =
        runTest {
            // Given
            coEvery { localDataSource.deleteSimpleTimer("local-1") } returns Unit

            // When
            val result = repository.deleteSimpleTimer("local-1")

            // Then
            coVerify(exactly = 1) { localDataSource.deleteSimpleTimer("local-1") }
            assertThat(result).isEqualTo(BaseResult.Success(Unit))
        }

    // ── 커스텀 타이머 조회 ────────────────────────────────

    @Test
    fun `커스텀 목록 조회 - 성공하면 상세를 보완해 반영한 뒤 로컬 스냅샷을 돌려준다`() =
        runTest {
            // Given: 목록은 스텝이 없고 상세 조회로 채운다
            coEvery { apiService.getCustomTimerList() } returns
                BaseResponse(
                    succeed = true,
                    code = "200",
                    message = "",
                    data = GetCustomTimerListDTO(1, listOf(CustomTimerSummaryDTO(5L, "8세트"))),
                )
            val stepDto = CustomTimerDetailStepDTO(1L, "운동", 1, 30)
            coEvery { apiService.getCustomTimer(5L) } returns
                BaseResponse(
                    succeed = true,
                    code = "200",
                    message = "",
                    data = GetCustomTimerDetailDTO(5L, "8세트", 1, listOf(stepDto)),
                )
            coEvery { localDataSource.replaceSyncedCustomTimers(any()) } returns Unit
            val localSnapshot =
                listOf(customEntity("local-1", 5L, "8세트", listOf(CachedTimerStep(1, "운동", 30))))
            coEvery { localDataSource.getCustomTimers() } returnsMany listOf(emptyList(), localSnapshot)

            // When
            val result = repository.getCustomTimerList()

            // Then
            coVerify(exactly = 1) {
                localDataSource.replaceSyncedCustomTimers(
                    listOf(ServerCustomTimer(5L, "8세트", listOf(CachedTimerStep(1, "운동", 30)))),
                )
            }
            assertThat(result).isEqualTo(BaseResult.Success(localSnapshot.map { it.toModel() }))
        }

    @Test
    fun `커스텀 목록 조회 - 상세 조회 실패 시 캐시가 있으면 살리고 없으면 뺀다`() =
        runTest {
            // Given: 두 항목 모두 상세 조회가 실패한다
            coEvery { apiService.getCustomTimerList() } returns
                BaseResponse(
                    succeed = true,
                    code = "200",
                    message = "",
                    data =
                        GetCustomTimerListDTO(
                            2,
                            listOf(CustomTimerSummaryDTO(5L, "캐시 있음"), CustomTimerSummaryDTO(6L, "캐시 없음")),
                        ),
                )
            coEvery { apiService.getCustomTimer(5L) } throws IOException("오프라인")
            coEvery { apiService.getCustomTimer(6L) } throws IOException("오프라인")
            val cached = customEntity("local-cached", 5L, "캐시 있음", listOf(CachedTimerStep(1, "운동", 30)))
            coEvery { localDataSource.getCustomTimers() } returns listOf(cached)
            coEvery { localDataSource.replaceSyncedCustomTimers(any()) } returns Unit

            // When
            repository.getCustomTimerList()

            // Then: serverId 6L은 캐시가 없어 반영 목록에서 빠진다
            coVerify(exactly = 1) {
                localDataSource.replaceSyncedCustomTimers(
                    listOf(ServerCustomTimer(5L, "캐시 있음", listOf(CachedTimerStep(1, "운동", 30)))),
                )
            }
        }

    @Test
    fun `커스텀 목록 조회 - NETWORK_ERROR면 로컬 스냅샷을 성공으로 돌려준다`() =
        runTest {
            // Given
            coEvery { apiService.getCustomTimerList() } throws IOException("오프라인")
            val localSnapshot = listOf(customEntity("local-1", null, "8세트", emptyList(), SyncState.CREATE_PENDING))
            coEvery { localDataSource.getCustomTimers() } returns localSnapshot

            // When
            val result = repository.getCustomTimerList()

            // Then
            coVerify(exactly = 0) { localDataSource.replaceSyncedCustomTimers(any()) }
            assertThat(result).isEqualTo(BaseResult.Success(localSnapshot.map { it.toModel() }))
        }

    @Test
    fun `커스텀 목록 조회 - 그 밖 오류는 그대로 전달한다`() =
        runTest {
            // Given
            coEvery { apiService.getCustomTimerList() } returns
                BaseResponse(succeed = false, code = "SERVER_ERROR", message = "서버 오류")

            // When
            val result = repository.getCustomTimerList()

            // Then
            coVerify(exactly = 0) { localDataSource.replaceSyncedCustomTimers(any()) }
            assertThat(result).isEqualTo(BaseResult.Error(errorCode = "SERVER_ERROR", message = "서버 오류"))
        }

    @Test
    fun `커스텀 목록 조회 - 로컬 전용 조회는 서버를 보지 않는다`() =
        runTest {
            // Given
            val localSnapshot = listOf(customEntity("local-1", 5L, "8세트", emptyList()))
            coEvery { localDataSource.getCustomTimers() } returns localSnapshot

            // When
            val result = repository.getLocalCustomTimerList()

            // Then
            coVerify(exactly = 0) { apiService.getCustomTimerList() }
            assertThat(result).isEqualTo(BaseResult.Success(localSnapshot.map { it.toModel() }))
        }

    @Test
    fun `커스텀 상세 조회 - 로컬에 있으면 성공을 돌려준다`() =
        runTest {
            // Given
            val entity = customEntity("local-1", 5L, "8세트", emptyList())
            coEvery { localDataSource.getCustomTimer("local-1") } returns entity

            // When
            val result = repository.getCustomTimer("local-1")

            // Then
            assertThat(result).isEqualTo(BaseResult.Success(entity.toModel()))
        }

    @Test
    fun `커스텀 상세 조회 - 로컬에 없으면 NOT_FOUND를 돌려준다`() =
        runTest {
            // Given
            coEvery { localDataSource.getCustomTimer("missing") } returns null

            // When
            val result = repository.getCustomTimer("missing") as BaseResult.Error

            // Then
            assertThat(result.errorCode).isEqualTo("NOT_FOUND")
        }

    // ── 커스텀 타이머 쓰기 ────────────────────────────────

    @Test
    fun `커스텀 생성 - localId를 돌려준다`() =
        runTest {
            // Given
            val steps = listOf(Step(order = 1, time = 30, name = "운동"))
            coEvery { localDataSource.createCustomTimer("8세트", steps.map { CachedTimerStep(it.order, it.name, it.time) }) } returns
                "new-local-id"

            // When
            val result = repository.createCustomTimer("8세트", steps)

            // Then
            assertThat(result).isEqualTo(BaseResult.Success("new-local-id"))
        }

    @Test
    fun `커스텀 생성 - 회원ID가 없으면 NO_MEMBER_ID를 돌려준다`() =
        runTest {
            // Given
            coEvery { localDataSource.createCustomTimer(any(), any()) } returns null

            // When
            val result = repository.createCustomTimer("8세트", emptyList()) as BaseResult.Error

            // Then
            assertThat(result.errorCode).isEqualTo("NO_MEMBER_ID")
        }

    @Test
    fun `커스텀 수정 - 로컬에 위임하고 성공을 돌려준다`() =
        runTest {
            // Given
            val steps = listOf(Step(order = 1, time = 20, name = "전력"))
            coEvery {
                localDataSource.updateCustomTimer(
                    "local-1",
                    "10세트",
                    steps.map { CachedTimerStep(it.order, it.name, it.time) },
                )
            } returns
                Unit

            // When
            val result = repository.editCustomTimer("local-1", "10세트", steps)

            // Then
            assertThat(result).isEqualTo(BaseResult.Success(Unit))
        }

    @Test
    fun `커스텀 삭제 - 로컬에 위임하고 성공을 돌려준다`() =
        runTest {
            // Given
            coEvery { localDataSource.deleteCustomTimer("local-1") } returns Unit

            // When
            val result = repository.deleteCustomTimer("local-1")

            // Then
            coVerify(exactly = 1) { localDataSource.deleteCustomTimer("local-1") }
            assertThat(result).isEqualTo(BaseResult.Success(Unit))
        }

    companion object {
        private const val MEMBER_ID = "member-a"
    }
}
