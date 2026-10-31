package com.project200.data.impl

import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.NetworkMonitor
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toModel
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
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
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.time.LocalDate

/** Room과 로컬 소스는 실제 구현을 사용하여 소비자가 받는 스냅샷을 검증합니다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseRecordRepositoryImplTest {
    private lateinit var database: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource
    private lateinit var repository: ExerciseRecordRepositoryImpl
    private val remote = mockk<ExerciseRecordRemoteDataSource>()
    private val network = mockk<NetworkMonitor>()
    private val preferences = mockk<PreferenceManager>()
    private val date = LocalDate.of(2026, 10, 10)
    private val nextDate = date.plusDays(1)
    private var memberId = "member-a"
    private val invalidatedMembers = mutableSetOf<String>()

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        every { preferences.getMemberId() } answers { memberId }
        every { preferences.isExerciseCacheInvalidated(any()) } answers { firstArg<String>() in invalidatedMembers }
        every { preferences.setExerciseCacheInvalidated(any(), any()) } answers {
            if (secondArg<Boolean>()) invalidatedMembers.add(firstArg()) else invalidatedMembers.remove(firstArg())
            Unit
        }
        every { network.isCurrentlyConnected() } returns true
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
        repository =
            ExerciseRecordRepositoryImpl(
                remote,
                local,
                network,
                UnconfinedTestDispatcher(),
                ExerciseImageFileStore(RuntimeEnvironment.getApplication()),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `온라인 캘린더 목록 상세는 Room을 거쳐 동일한 로컬 식별자로 읽힌다`() =
        runTest {
            coEvery { remote.getExerciseCountsByRange(date, nextDate) } returns response(listOf(GetExerciseCountByRangeDTO(date, 1)))
            coEvery { remote.getExerciseList(date) } returns response(listOf(itemDto()))
            coEvery { remote.getExerciseRecordDetail(7L) } returns response(detailDto())

            val counts = repository.getExerciseCountByRange(date, nextDate) as BaseResult.Success
            val items = repository.getExerciseRecordList(date) as BaseResult.Success
            val id = items.data.single().recordId
            val detail = repository.getExerciseDetail(id) as BaseResult.Success

            assertThat(id).isNotEqualTo("7")
            assertThat(counts.data).isEqualTo(local.getCountsByRange(date, nextDate))
            assertThat(items.data).isEqualTo(local.getListByDate(date))
            assertThat(detail.data).isEqualTo(local.getDetailByLocalId(id))
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)?.serverId).isEqualTo(7L)

            repository.getExerciseRecordList(date)
            assertThat(local.getListByDate(date).single().recordId).isEqualTo(id)
        }

    @Test
    fun `오프라인 적중과 성공한 빈 결과는 읽고 미조회 쿼리는 네트워크 오류다`() =
        runTest {
            local.replaceSyncedListByDate(date, listOf(itemDto().toModel()))
            local.saveSyncedDetail(7L, record())
            local.replaceSyncedListByDate(nextDate, emptyList())
            local.replaceCountsByRange(date, nextDate, emptyList())
            every { network.isCurrentlyConnected() } returns false

            val id = local.getListByDate(date).single().recordId
            val hit = repository.getExerciseRecordList(date) as BaseResult.Success
            assertThat(hit.data.single().recordId).isEqualTo(id)
            assertThat(hit.refreshError).isNull()
            assertThat(hit.isCachedSnapshot).isTrue()
            assertThat((repository.getExerciseDetail(id) as BaseResult.Success).data.title).isEqualTo("운동")
            assertThat((repository.getExerciseRecordList(nextDate) as BaseResult.Success).data).isEmpty()
            assertThat((repository.getExerciseCountByRange(date, nextDate) as BaseResult.Success).data).isEmpty()
            assertThat((repository.getExerciseRecordList(nextDate.plusDays(1)) as BaseResult.Error).errorCode).isEqualTo("NETWORK_ERROR")
            assertThat((repository.getExerciseDetail("missing-local") as BaseResult.Error).errorCode).isEqualTo("LOCAL_ONLY_RECORD")
            assertThat(
                (repository.getExerciseCountByRange(nextDate.plusDays(1), nextDate.plusDays(2)) as BaseResult.Error).errorCode,
            ).isEqualTo("NETWORK_ERROR")
            coVerify(exactly = 0) { remote.getExerciseList(any()) }
            coVerify(exactly = 0) { remote.getExerciseRecordDetail(any()) }
        }

    @Test
    fun `온라인 네트워크 실패와 인증 실패는 캐시와 함께 서로 다른 오류를 전달한다`() =
        runTest {
            local.replaceSyncedListByDate(date, listOf(itemDto().toModel()))
            val id = local.getListByDate(date).single().recordId
            coEvery { remote.getExerciseList(date) } throws IOException("연결 실패")
            val networkResult = repository.getExerciseRecordList(date, refresh = true) as BaseResult.Success
            assertThat(networkResult.data.single().recordId).isEqualTo(id)
            assertThat(networkResult.refreshError?.errorCode).isEqualTo("NETWORK_ERROR")

            coEvery { remote.getExerciseList(date) } returns BaseResponse(succeed = false, code = "401", message = "인증 실패")
            val authResult = repository.getExerciseRecordList(date, refresh = true) as BaseResult.Success
            assertThat(authResult.refreshError?.errorCode).isEqualTo("401")
            assertThat(authResult.isCachedSnapshot).isTrue()
            assertThat(authResult.data.single().recordId).isEqualTo(id)
            coEvery { remote.getExerciseList(nextDate) } returns BaseResponse(succeed = false, code = "BUSINESS_ERROR", message = "조회 불가")
            assertThat((repository.getExerciseRecordList(nextDate) as BaseResult.Error).errorCode).isEqualTo("BUSINESS_ERROR")
        }

    @Test
    fun `로컬 생성 대기는 본문 수정과 삭제가 가능하다`() =
        runTest {
            every { network.isCurrentlyConnected() } returns false
            val saved = repository.createExerciseRecord(ExerciseWriteInput(record())) as BaseResult.Success
            val id = saved.data.recordId
            val item = (repository.getExerciseRecordList(date) as BaseResult.Success).data.single()
            val count = (repository.getExerciseCountByRange(date, date) as BaseResult.Success).data.single()
            val detail = (repository.getExerciseDetail(id) as BaseResult.Success).data
            assertThat(item.recordId).isEqualTo(id)
            assertThat(item.syncPending).isTrue()
            assertThat(count.count).isEqualTo(1)
            assertThat(detail.canModify).isTrue()
            assertThat(detail.syncPending).isTrue()
            assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "수정")), id))
                .isInstanceOf(BaseResult.Success::class.java)
            assertThat(repository.deleteExerciseRecord(id)).isInstanceOf(BaseResult.Success::class.java)
            assertThat(database.exerciseSyncDao().get(memberId, id)).isNull()
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)).isNull()
            assertThat(local.getListSnapshot(date, memberId)).isEmpty()
            coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
        }

    @Test
    fun `자신의 생성 날짜 이동 삭제는 로컬 재조회와 집계를 함께 갱신한다`() =
        runTest {
            local.replaceCountsByRange(date, nextDate, emptyList())
            local.replaceSyncedListByDate(date, emptyList())
            local.replaceSyncedListByDate(nextDate, emptyList())

            val created = (repository.createExerciseRecord(ExerciseWriteInput(record())) as BaseResult.Success).data
            val id = created.recordId
            assertThat(id).isNotEqualTo("7")
            assertThat((repository.getExerciseRecordList(date, refresh = false) as BaseResult.Success).data.single().recordId).isEqualTo(id)
            assertThat(
                (repository.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success).data.sumOf { it.count },
            ).isEqualTo(1)

            repository.updateExerciseRecord(ExerciseWriteInput(record(nextDate).copy(title = "옮긴 운동")), id)
            assertThat((repository.getExerciseRecordList(date, refresh = false) as BaseResult.Success).data).isEmpty()
            assertThat(
                (repository.getExerciseRecordList(nextDate, refresh = false) as BaseResult.Success).data.single().recordId,
            ).isEqualTo(id)
            assertThat((repository.getExerciseDetail(id, refresh = false) as BaseResult.Success).data.title).isEqualTo("옮긴 운동")
            val movedResult = repository.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success
            val movedCounts = movedResult.data.associate { it.date to it.count }
            assertThat(movedCounts[date] ?: 0).isEqualTo(0)
            assertThat(movedCounts[nextDate]).isEqualTo(1)

            repository.deleteExerciseRecord(id)
            assertThat((repository.getExerciseRecordList(nextDate, refresh = false) as BaseResult.Success).data).isEmpty()
            assertThat(repository.getExerciseDetail(id, refresh = false)).isInstanceOf(BaseResult.Error::class.java)
            assertThat(
                (repository.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success).data.sumOf { it.count },
            ).isEqualTo(0)
            coVerify(exactly = 0) { remote.getExerciseList(any()) }
            coVerify(exactly = 0) { remote.getExerciseRecordDetail(any()) }
            coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
        }

    @Test
    fun `숫자 서버 ID로 상세를 읽은 뒤 본문 수정과 삭제는 같은 기존 로컬 ID를 사용한다`() =
        runTest {
            coEvery { remote.getExerciseRecordDetail(7L) } returns response(detailDto())
            assertThat((repository.getExerciseDetail("7") as BaseResult.Success).data.title).isEqualTo("운동")
            val dao = database.exerciseRecordDao()
            val id = dao.findDetailLocalId(memberId, 7L)!!
            assertThat(id).isNotEqualTo("7")

            val updated =
                repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "숫자 ID 수정")), "7") as BaseResult.Success
            assertThat(updated.data.recordId).isEqualTo(id)
            assertThat(dao.getDetailByLocalId(memberId, id)?.title).isEqualTo("숫자 ID 수정")
            val edited =
                repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "로컬 ID 수정")), id) as BaseResult.Success
            assertThat(edited.data.recordId).isEqualTo(id)
            assertThat((repository.getExerciseDetail("7", localOnly = true) as BaseResult.Success).data.title)
                .isEqualTo("로컬 ID 수정")
            assertThat(database.exerciseSyncDao().getByMember(memberId).single().localId).isEqualTo(id)
            assertThat(dao.getDetailByLocalId(memberId, "7")).isNull()

            assertThat(repository.deleteExerciseRecord("7")).isInstanceOf(BaseResult.Success::class.java)
            val deleted = database.exerciseSyncDao().get(memberId, id)!!
            assertThat(deleted.operation).isEqualTo(SyncState.DELETE_PENDING)
            for (key in listOf(id, "7")) {
                assertThat((repository.getExerciseDetail(key, refresh = true) as BaseResult.Error).errorCode).isEqualTo("RECORD_DELETED")
                assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record()), key))
                    .isInstanceOf(BaseResult.Error::class.java)
                assertThat(repository.deleteExerciseRecord(key)).isInstanceOf(BaseResult.Success::class.java)
            }
            assertThat(database.exerciseSyncDao().get(memberId, id)).isEqualTo(deleted)
            coVerify(exactly = 1) { remote.getExerciseRecordDetail(7L) }
            coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
        }

    @Test
    fun `다른 계정과 없는 ID의 로컬 쓰기는 매핑이나 새 기록을 만들지 않는다`() =
        runTest {
            val owner = memberId
            val id = cacheRecord()
            val original = database.exerciseRecordDao().getDetailByLocalId(owner, id)
            memberId = "member-b"
            for (key in listOf(id, "7", "missing-local", "999")) {
                assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record()), key))
                    .isInstanceOf(BaseResult.Error::class.java)
                assertThat(repository.deleteExerciseRecord(key)).isInstanceOf(BaseResult.Error::class.java)
            }
            assertThat(database.exerciseRecordDao().getDetailByLocalId(owner, id)).isEqualTo(original)
            assertThat(database.exerciseSyncDao().getByMember(owner)).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            assertThat(database.exerciseRecordDao().getListByDate(memberId, date)).isEmpty()
            assertThat(database.exerciseRecordDao().findDetailLocalId(memberId, 7L)).isNull()
            assertThat(database.exerciseRecordDao().findDetailLocalId(memberId, 999L)).isNull()
            coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
        }

    @Test
    fun `투영 없는 삭제 저널의 숫자 서버 ID는 기존 tombstone만 다시 찾는다`() =
        runTest {
            val id = "deleted-local"
            val tombstone =
                ExerciseSyncEntity(
                    memberId,
                    id,
                    7L,
                    SyncState.DELETE_PENDING,
                    1L,
                    "delete-operation",
                    null,
                    9L,
                    date,
                    date,
                    ExerciseRecoveryState.READY,
                )
            database.exerciseSyncDao().saveDesired(tombstone)
            assertThat(repository.deleteExerciseRecord("7")).isInstanceOf(BaseResult.Success::class.java)
            assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record()), "7")).isInstanceOf(BaseResult.Error::class.java)
            assertThat(database.exerciseSyncDao().getByMember(memberId)).containsExactly(tombstone)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)).isNull()
            assertThat(database.exerciseRecordDao().getListByDate(memberId, date)).isEmpty()
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
        }

    @Test
    fun `갱신과 자신의 쓰기가 겹쳐도 오래된 응답이 쓰기 뒤에 반영되지 않는다`() =
        runTest {
            val id = cacheRecord()
            val entered = CompletableDeferred<Unit>()
            val pendingResponse = CompletableDeferred<BaseResponse<List<GetExerciseRecordListDto>>>()
            coEvery { remote.getExerciseList(date) } coAnswers {
                entered.complete(Unit)
                pendingResponse.await()
            }
            val refresh = async { repository.getExerciseRecordList(date, refresh = true) }
            entered.await()
            val edit = async { repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "새 내용")), id) }
            assertThat(edit.await()).isInstanceOf(BaseResult.Success::class.java)
            assertThat(refresh.isCompleted).isFalse()
            assertThat((repository.getExerciseRecordList(date) as BaseResult.Success).data.single().title).isEqualTo("새 내용")
            assertThat((repository.getExerciseDetail(id) as BaseResult.Success).data.title).isEqualTo("새 내용")
            pendingResponse.complete(response(listOf(itemDto())))
            val refreshed = refresh.await() as BaseResult.Success
            assertThat(refreshed.data.single().title).isEqualTo("새 내용")
            assertThat(local.getListByDate(date).single().title).isEqualTo("새 내용")
            assertThat(local.getDetailByLocalId(id)?.title).isEqualTo("새 내용")
        }

    @Test
    fun `요청 중 계정이 바뀌면 응답을 다른 계정에 저장하거나 화면에 반환하지 않는다`() =
        runTest {
            coEvery { remote.getExerciseList(date) } coAnswers {
                memberId = "member-b"
                response(listOf(itemDto()))
            }
            assertThat((repository.getExerciseRecordList(date) as BaseResult.Error).errorCode).isEqualTo("ACCOUNT_CHANGED")
            assertThat(local.getListByDate(date)).isEmpty()
            assertThat(database.exerciseRecordDao().getListByDate("member-a", date)).isEmpty()
        }

    @Test
    fun `요청 취소를 캐시 성공이나 오류로 숨기지 않는다`() =
        runTest {
            coEvery { remote.getExerciseList(date) } throws CancellationException("취소")
            try {
                repository.getExerciseRecordList(date)
                throw AssertionError("취소가 전파되지 않았습니다")
            } catch (expected: CancellationException) {
                assertThat(expected.message).isEqualTo("취소")
            }
        }

    @Test
    fun `무효 캐시 정리가 실패하면 스냅샷을 읽지 않고 무효 표시를 유지한다`() =
        runTest {
            cacheRecord()
            invalidatedMembers.add(memberId)
            val failingDao = spyk(database.exerciseRecordDao())
            coEvery { failingDao.deleteSyncedDetails(memberId) } throws IOException("정리 실패")
            val failingLocal = spyk(ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), failingDao, preferences))
            val reader =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    network,
                    UnconfinedTestDispatcher(),
                    ExerciseImageFileStore(RuntimeEnvironment.getApplication()),
                )
            every { network.isCurrentlyConnected() } returns false
            assertThat(reader.getExerciseRecordList(date, refresh = false)).isInstanceOf(BaseResult.Error::class.java)
            assertThat(invalidatedMembers).contains(memberId)
            coVerify(exactly = 0) { failingLocal.getListSnapshot(any(), any()) }
            assertThat(local.getListByDate(date).single().title).isEqualTo("운동")
        }

    @Test
    fun `로컬 우선 조회의 온라인 미적중은 서버로 갱신하지만 복귀 전용 조회는 요청하지 않는다`() =
        runTest {
            coEvery { remote.getExerciseList(date) } returns response(listOf(itemDto()))
            val loaded = repository.getExerciseRecordList(date, refresh = false) as BaseResult.Success
            assertThat(loaded.data.single().title).isEqualTo("운동")
            assertThat((repository.getExerciseRecordList(date, refresh = false) as BaseResult.Success).data).isEqualTo(loaded.data)
            assertThat(
                repository.getExerciseRecordList(nextDate, refresh = false, localOnly = true),
            ).isInstanceOf(BaseResult.Error::class.java)
            coVerify(exactly = 1) { remote.getExerciseList(date) }
            coVerify(exactly = 0) { remote.getExerciseList(nextDate) }
        }

    @Test
    fun `복귀 전용 미적중은 온라인에서도 캘린더와 상세 서버를 요청하지 않는다`() =
        runTest {
            assertThat(
                repository.getExerciseCountByRange(date, nextDate, refresh = false, localOnly = true),
            ).isInstanceOf(BaseResult.Error::class.java)
            assertThat(repository.getExerciseDetail("7", refresh = false, localOnly = true)).isInstanceOf(BaseResult.Error::class.java)
            coVerify(exactly = 0) { remote.getExerciseCountsByRange(any(), any()) }
            coVerify(exactly = 0) { remote.getExerciseRecordDetail(any()) }
        }

    @Test
    fun `온라인 로컬 우선 캘린더와 상세 미적중은 서버 결과를 Room으로 읽는다`() =
        runTest {
            coEvery { remote.getExerciseCountsByRange(date, nextDate) } returns response(listOf(GetExerciseCountByRangeDTO(date, 1)))
            coEvery { remote.getExerciseRecordDetail(7L) } returns response(detailDto())
            assertThat(
                (repository.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success).data.single().count,
            ).isEqualTo(1)
            assertThat(
                (repository.getExerciseDetail("7", refresh = false) as BaseResult.Success).data,
            ).isEqualTo(local.getDetailByServerId(7L))
            coVerify(exactly = 1) { remote.getExerciseCountsByRange(date, nextDate) }
            coVerify(exactly = 1) { remote.getExerciseRecordDetail(7L) }
        }

    @Test
    fun `무효 상세의 온라인 재조회는 열린 화면의 로컬 식별자를 유지한다`() =
        runTest {
            val id = cacheRecord()
            invalidatedMembers.add(memberId)
            coEvery { remote.getExerciseRecordDetail(7L) } returns response(detailDto().copy(exerciseTitle = "새 상세"))
            val result = repository.getExerciseDetail(id, refresh = false) as BaseResult.Success
            assertThat(local.getDetailByLocalId(id)).isEqualTo(result.data)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)?.serverId).isEqualTo(7L)
            coVerify(exactly = 1) { remote.getExerciseRecordDetail(7L) }
        }

    @Test
    fun `캐시 집계와 상세는 서버 갱신이 대기하는 동안 즉시 읽을 수 있다`() =
        runTest {
            val id = cacheRecord()
            local.replaceCountsByRange(date, nextDate, listOf(com.project200.domain.model.ExerciseCount(date, 1)))
            val entered = CompletableDeferred<Unit>()
            val countsResponse = CompletableDeferred<BaseResponse<List<GetExerciseCountByRangeDTO>>>()
            coEvery { remote.getExerciseCountsByRange(date, nextDate) } coAnswers {
                entered.complete(Unit)
                countsResponse.await()
            }
            val refreshCounts = async { repository.getExerciseCountByRange(date, nextDate, refresh = true) }
            entered.await()
            val counts = repository.getExerciseCountByRange(date, nextDate) as BaseResult.Success
            assertThat(counts.isCachedSnapshot).isTrue()
            assertThat(counts.data.single().count).isEqualTo(1)
            assertThat((repository.getExerciseDetail(id) as BaseResult.Success).data.title).isEqualTo("운동")
            assertThat(refreshCounts.isCompleted).isFalse()
            countsResponse.complete(response(emptyList()))
            assertThat((refreshCounts.await() as BaseResult.Success).data).isEmpty()

            val detailEntered = CompletableDeferred<Unit>()
            val detailResponse = CompletableDeferred<BaseResponse<GetExerciseRecordData>>()
            coEvery { remote.getExerciseRecordDetail(7L) } coAnswers {
                detailEntered.complete(Unit)
                detailResponse.await()
            }
            val refreshDetail = async { repository.getExerciseDetail(id, refresh = true) }
            detailEntered.await()
            val detail = repository.getExerciseDetail(id) as BaseResult.Success
            assertThat(detail.isCachedSnapshot).isTrue()
            assertThat(detail.data.title).isEqualTo("운동")
            assertThat((repository.getExerciseCountByRange(date, nextDate) as BaseResult.Success).data).isEmpty()
            assertThat(refreshDetail.isCompleted).isFalse()
            detailResponse.complete(response(detailDto().copy(exerciseTitle = "새 상세")))
            assertThat((refreshDetail.await() as BaseResult.Success).data.title).isEqualTo("새 상세")
        }

    @Test
    fun `서버 기록의 본문 수정과 삭제는 오프라인에서도 저널만 저장하고 원격 호출하지 않는다`() =
        runTest {
            val id = cacheRecord()
            every { network.isCurrentlyConnected() } returns false
            val result = repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "대기 수정")), id)
            assertThat((result as BaseResult.Success).data.recordId).isEqualTo(id)
            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(local.getDetailByLocalId(id)?.canModify).isTrue()
            assertThat(repository.deleteExerciseRecord(id)).isInstanceOf(BaseResult.Success::class.java)
            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(local.getListSnapshot(date, memberId)).isEmpty()
            assertThat(local.getDetailByLocalId(id)).isNull()
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
        }

    @Test
    fun `로컬 트랜잭션 실패는 저장 성공이나 서버 재시도로 바꾸지 않는다`() =
        runTest {
            val failingLocal = spyk(local)
            coEvery { failingLocal.createRecord(any(), memberId, any()) } throws IOException("Room 저장 실패")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    network,
                    UnconfinedTestDispatcher(),
                    ExerciseImageFileStore(RuntimeEnvironment.getApplication()),
                )
            assertThat(writer.createExerciseRecord(ExerciseWriteInput(record()))).isInstanceOf(BaseResult.Error::class.java)
            assertThat(local.getListByDate(date)).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
        }

    @Test
    fun `기록 생성은 기존 사진 삭제를 허용하지 않고 잘못된 삭제 ID도 빈 성공으로 바꾸지 않는다`() =
        runTest {
            assertThat(repository.createExerciseRecord(ExerciseWriteInput(record(), removedImageLocalIds = setOf("server:1"))))
                .isInstanceOf(BaseResult.Error::class.java)
            assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record(), removedImageLocalIds = setOf("missing")), "7"))
                .isInstanceOf(BaseResult.Error::class.java)
            coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseImages(any(), any()) }
        }

    @Test
    fun `삭제 tombstone은 로컬 서버 ID와 강제 갱신 상세 및 목록에 다시 나타나지 않는다`() =
        runTest {
            local.replaceSyncedListByDate(date, listOf(itemDto().toModel()))
            val id = local.saveSyncedDetail(7, record())!!
            repository.deleteExerciseRecord(id)
            for (key in listOf(id, "7")) {
                for (refresh in listOf(false, true)) {
                    val result = repository.getExerciseDetail(key, refresh) as BaseResult.Error
                    assertThat(result.errorCode).isEqualTo("RECORD_DELETED")
                }
            }
            coEvery { remote.getExerciseList(date) } returns response(listOf(itemDto()))
            assertThat((repository.getExerciseRecordList(date, refresh = true) as BaseResult.Success).data).isEmpty()
            coVerify(exactly = 0) { remote.getExerciseRecordDetail(any()) }
            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.DELETE_PENDING)
        }

    @Test
    fun `이미 시작한 서버 ID 상세 갱신도 그 사이 생긴 삭제 tombstone을 부활시키지 않는다`() =
        runTest {
            val id = cacheRecord()
            val entered = CompletableDeferred<Unit>()
            val pending = CompletableDeferred<BaseResponse<GetExerciseRecordData>>()
            coEvery { remote.getExerciseRecordDetail(7) } coAnswers {
                entered.complete(Unit)
                pending.await()
            }
            val refresh = async { repository.getExerciseDetail("7", refresh = true) }
            entered.await()
            repository.deleteExerciseRecord(id)
            pending.complete(response(detailDto().copy(exerciseTitle = "늦은 서버 응답")))
            assertThat(refresh.await()).isInstanceOf(BaseResult.Error::class.java)
            assertThat(local.getDetailByServerId(7)).isNull()
            assertThat(local.getDetailByLocalId(id)).isNull()
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)?.syncState).isEqualTo(SyncState.DELETE_PENDING)
        }

    private suspend fun cacheRecord(): String {
        local.replaceSyncedListByDate(date, listOf(itemDto().toModel()))
        return local.saveSyncedDetail(7L, record())!!
    }

    private fun record(day: LocalDate = date) = ExerciseRecord("운동", "본문", "OTHER", day.atTime(9, 0), day.atTime(10, 0), "공원", null)

    private fun itemDto() = GetExerciseRecordListDto(7L, "운동", "OTHER", date.atTime(9, 0), date.atTime(10, 0), null)

    private fun detailDto() = GetExerciseRecordData("운동", "본문", "OTHER", date.atTime(9, 0), date.atTime(10, 0), "공원", null)

    private fun <T> response(data: T): BaseResponse<T> = BaseResponse(succeed = true, code = "200", message = "", data = data)
}
