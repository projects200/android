package com.project200.data.impl

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ServerCustomTimer
import com.project200.data.datasource.ServerSimpleTimer
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetCustomTimerListDTO
import com.project200.data.dto.GetSimpleTimersDTO
import com.project200.data.dto.SimpleTimerDTO
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.CachedTimerStep
import com.project200.data.local.entity.SyncState
import com.project200.domain.manager.TimerSyncScheduler
import com.project200.domain.model.BaseResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TimerLocalFirstReadTest {
    private lateinit var database: UndabangDatabase
    private lateinit var local: TimerLocalDataSource
    private lateinit var repository: TimerRepositoryImpl
    private val api = mockk<ApiService>()
    private val preferences = mockk<PreferenceManager>()
    private val scheduler = mockk<TimerSyncScheduler>(relaxed = true)
    private var memberId = "member-a"

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        every { preferences.getMemberId() } answers { memberId }
        local = TimerLocalDataSource(database, database.timerDao(), preferences)
        repository = TimerRepositoryImpl(api, local, UnconfinedTestDispatcher(), scheduler)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `심플 캐시는 대기 전송 없이 즉시 읽고 늦은 GET은 로컬 편집과 삭제를 되돌리지 않는다`() =
        runTest {
            local.replaceSyncedSimpleTimers(listOf(ServerSimpleTimer(7L, 60), ServerSimpleTimer(8L, 90)), memberId, 0L)
            val cached = repository.getSimpleTimers() as BaseResult.Success
            assertThat(cached.isCachedSnapshot).isTrue()
            coVerify(exactly = 0) { api.getSimpleTimers() }
            val entered = CompletableDeferred<Unit>()
            val response = CompletableDeferred<BaseResponse<GetSimpleTimersDTO>>()
            coEvery { api.getSimpleTimers() } coAnswers {
                entered.complete(Unit)
                response.await()
            }
            val refresh = async { repository.getSimpleTimers(refresh = true) }
            entered.await()
            val editedId = cached.data.first { it.time == 60 }.localId
            val deletedId = cached.data.first { it.time == 90 }.localId
            assertThat(repository.editSimpleTimer(editedId, 120)).isInstanceOf(BaseResult.Success::class.java)
            assertThat(repository.deleteSimpleTimer(deletedId)).isInstanceOf(BaseResult.Success::class.java)
            assertThat((repository.getSimpleTimers() as BaseResult.Success).data.single().time).isEqualTo(120)
            assertThat((repository.getLocalSimpleTimers() as BaseResult.Success).data.single().localId).isEqualTo(editedId)
            assertThat(refresh.isCompleted).isFalse()
            response.complete(success(GetSimpleTimersDTO(2, listOf(SimpleTimerDTO(7L, 60), SimpleTimerDTO(8L, 90)))))
            assertThat((refresh.await() as BaseResult.Success).data.single().time).isEqualTo(120)
            assertThat(database.timerDao().getSimpleTimer(memberId, editedId)?.syncState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(database.timerDao().getSimpleTimer(memberId, deletedId)?.syncState).isEqualTo(SyncState.DELETE_PENDING)
        }

    @Test
    fun `커스텀 캐시는 느린 목록 갱신 중에도 읽고 마지막 로컬 삭제는 빈 스냅샷을 유지한다`() =
        runTest {
            local.replaceSyncedCustomTimers(
                listOf(ServerCustomTimer(7L, "저장됨", listOf(CachedTimerStep(0, "운동", 60)))),
                memberId,
                0L,
            )
            val cached = repository.getCustomTimerList() as BaseResult.Success
            assertThat(cached.isCachedSnapshot).isTrue()
            val entered = CompletableDeferred<Unit>()
            val response = CompletableDeferred<BaseResponse<GetCustomTimerListDTO>>()
            coEvery { api.getCustomTimerList() } coAnswers {
                entered.complete(Unit)
                response.await()
            }
            val refresh = async { repository.getCustomTimerList(refresh = true) }
            entered.await()
            repository.deleteCustomTimer(cached.data.single().localId)
            val draft = (repository.createCustomTimer("미전송", emptyList()) as BaseResult.Success).data
            repository.deleteCustomTimer(draft)
            val empty = repository.getCustomTimerList() as BaseResult.Success
            assertThat(empty.data).isEmpty()
            assertThat(empty.isCachedSnapshot).isTrue()
            assertThat(refresh.isCompleted).isFalse()
            response.complete(success(GetCustomTimerListDTO(0, emptyList())))
            assertThat((refresh.await() as BaseResult.Success).data).isEmpty()
            assertThat(local.getPendingCustomTimers()).hasSize(1)
        }

    @Test
    fun `GET 중 전송 완료된 편집은 이전 서버 응답보다 우선한다`() =
        runTest {
            local.replaceSyncedSimpleTimers(listOf(ServerSimpleTimer(7L, 60)), memberId, 0L)
            val id = local.getSimpleTimers().single().localId
            val entered = CompletableDeferred<Unit>()
            val response = CompletableDeferred<BaseResponse<GetSimpleTimersDTO>>()
            coEvery { api.getSimpleTimers() } coAnswers {
                entered.complete(Unit)
                response.await()
            }
            val refresh = async { repository.getSimpleTimers(refresh = true) }
            entered.await()
            local.updateSimpleTimer(id, 120)
            val receipt = local.claimSimpleTimer(memberId, id)!!
            local.completeSimple(receipt, 7L)
            response.complete(success(GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(7L, 60)))))
            assertThat((refresh.await() as BaseResult.Success).data.single().time).isEqualTo(120)
            assertThat(local.getSimpleTimer(id)?.syncState).isEqualTo(SyncState.SYNCED)
        }

    @Test
    fun `미조회는 오프라인 오류이고 서버 빈 결과와 마지막 미전송 삭제는 캐시 적중이다`() =
        runTest {
            coEvery { api.getSimpleTimers() } throws IOException("오프라인")
            assertThat(repository.getSimpleTimers()).isInstanceOf(BaseResult.Error::class.java)
            coEvery { api.getSimpleTimers() } returns success(GetSimpleTimersDTO(0, emptyList()))
            assertThat((repository.getSimpleTimers() as BaseResult.Success).isCachedSnapshot).isFalse()
            val id = (repository.addSimpleTimer(60) as BaseResult.Success).data
            repository.deleteSimpleTimer(id)
            val result = repository.getSimpleTimers() as BaseResult.Success
            assertThat(result.data).isEmpty()
            assertThat(result.isCachedSnapshot).isTrue()
            coVerify(exactly = 2) { api.getSimpleTimers() }
        }

    @Test
    fun `요청 중 계정 변경은 이전 응답을 저장하거나 반환하지 않는다`() =
        runTest {
            coEvery { api.getSimpleTimers() } coAnswers {
                memberId = "member-b"
                success(GetSimpleTimersDTO(1, listOf(SimpleTimerDTO(7L, 60))))
            }
            assertThat((repository.getSimpleTimers() as BaseResult.Error).errorCode).isEqualTo("ACCOUNT_CHANGED")
            assertThat(database.timerDao().getSimpleTimers("member-a")).isEmpty()
            assertThat(database.timerDao().getSimpleTimers("member-b")).isEmpty()
            assertThat(database.timerDao().getSnapshot("member-b", "simple")).isNull()
        }

    @Test
    fun `취소는 빈 캐시 성공으로 바꾸지 않는다`() =
        runTest {
            coEvery { api.getSimpleTimers() } throws CancellationException("취소")
            try {
                repository.getSimpleTimers()
                throw AssertionError("취소가 전파되지 않았습니다")
            } catch (expected: CancellationException) {
                assertThat(expected.message).isEqualTo("취소")
            }
            assertThat(local.getSimpleSnapshot(memberId)).isNull()
        }

    private fun <T> success(data: T) = BaseResponse(true, "200", "", data)
}
