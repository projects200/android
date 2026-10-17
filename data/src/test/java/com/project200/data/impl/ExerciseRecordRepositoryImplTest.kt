package com.project200.data.impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.NetworkMonitor
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.datasource.FeatureFlagDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.PictureData
import com.project200.data.dto.PostExerciseResponseDTO
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toSyncedEntity
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
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
    private val flags = mockk<FeatureFlagDataSource>()
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
        every { flags.isOfflineCacheEnabled() } returns true
        every { network.isCurrentlyConnected() } returns true
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
        repository =
            ExerciseRecordRepositoryImpl(
                remote,
                local,
                flags,
                network,
                UnconfinedTestDispatcher(),
                RuntimeEnvironment.getApplication(),
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
            assertThat((repository.getExerciseDetail("missing-local") as BaseResult.Error).errorCode).isEqualTo("NETWORK_ERROR")
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
            val networkResult = repository.getExerciseRecordList(date) as BaseResult.Success
            assertThat(networkResult.data.single().recordId).isEqualTo(id)
            assertThat(networkResult.refreshError?.errorCode).isEqualTo("NETWORK_ERROR")

            coEvery { remote.getExerciseList(date) } returns BaseResponse(succeed = false, code = "401", message = "인증 실패")
            val authResult = repository.getExerciseRecordList(date) as BaseResult.Success
            assertThat(authResult.refreshError?.errorCode).isEqualTo("401")
            assertThat(authResult.isCachedSnapshot).isTrue()
            assertThat(authResult.data.single().recordId).isEqualTo(id)
            coEvery { remote.getExerciseList(nextDate) } returns BaseResponse(succeed = false, code = "BUSINESS_ERROR", message = "조회 불가")
            assertThat((repository.getExerciseRecordList(nextDate) as BaseResult.Error).errorCode).isEqualTo("BUSINESS_ERROR")
        }

    @Test
    fun `플래그가 꺼지면 연결 상태나 Room 쓰기에 의존하지 않고 서버 응답과 오류를 반환한다`() =
        runTest {
            every { flags.isOfflineCacheEnabled() } returns false
            every { network.isCurrentlyConnected() } returns false
            val brokenLocal = mockk<ExerciseRecordLocalDataSource>()
            val legacy =
                ExerciseRecordRepositoryImpl(
                    remote,
                    brokenLocal,
                    flags,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
                )
            coEvery { remote.getExerciseCountsByRange(date, nextDate) } returns response(listOf(GetExerciseCountByRangeDTO(date, 1)))
            coEvery { remote.getExerciseList(date) } returns response(listOf(itemDto()))
            coEvery { remote.getExerciseRecordDetail(7L) } returns response(detailDto())
            coEvery { remote.postExerciseRecord(any()) } returns response(PostExerciseResponseDTO(7L, 3))

            assertThat(
                (legacy.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success).data.single().count,
            ).isEqualTo(1)
            assertThat((legacy.getExerciseRecordList(date, refresh = false) as BaseResult.Success).data.single().recordId).isEqualTo("7")
            assertThat((legacy.getExerciseDetail("7") as BaseResult.Success).data.title).isEqualTo("운동")
            val created = legacy.createExerciseRecord(record()) as BaseResult.Success
            assertThat(created.data.recordId).isEqualTo("7")
            assertThat(created.data.earnedPoints).isEqualTo(3)
            assertThat(created.refreshError).isNull()
            assertThat(created.isCachedSnapshot).isFalse()
            coEvery { remote.getExerciseList(date) } throws IOException("연결 없음")
            assertThat((legacy.getExerciseRecordList(date) as BaseResult.Error).errorCode).isEqualTo("NETWORK_ERROR")
            coEvery { remote.getExerciseList(date) } returns BaseResponse(succeed = false, code = "401", message = "인증 실패")
            assertThat((legacy.getExerciseRecordList(date) as BaseResult.Error).errorCode).isEqualTo("401")
            coVerify(exactly = 0) { brokenLocal.replaceSyncedListByDate(any(), any(), any()) }
        }

    @Test
    fun `로컬 전용 생성 대기는 목록 캘린더 상세에 보이지만 서버 쓰기를 하지 않는다`() =
        runTest {
            database.exerciseRecordDao().upsertListItems(
                listOf(
                    ExerciseListItemEntity(
                        memberId,
                        "local-only",
                        null,
                        SyncState.CREATE_PENDING,
                        date,
                        0,
                        "대기 운동",
                        "OTHER",
                        date.atTime(9, 0),
                        date.atTime(10, 0),
                        null,
                    ),
                ),
            )
            database.exerciseRecordDao().upsertDetail(
                record().toSyncedEntity(memberId, "local-only", 7L).copy(serverId = null, syncState = SyncState.CREATE_PENDING),
            )
            every { network.isCurrentlyConnected() } returns false

            val item = (repository.getExerciseRecordList(date) as BaseResult.Success).data.single()
            val count = (repository.getExerciseCountByRange(date, date) as BaseResult.Success).data.single()
            val detail = (repository.getExerciseDetail(item.recordId) as BaseResult.Success).data
            assertThat(item.recordId).isEqualTo("local-only")
            assertThat(item.syncPending).isTrue()
            assertThat(count.count).isEqualTo(1)
            assertThat(count.syncPending).isTrue()
            assertThat(detail.canModify).isFalse()
            assertThat(detail.syncPending).isTrue()
            assertThat(
                (repository.updateExerciseRecord(record(), item.recordId) as BaseResult.Error).errorCode,
            ).isEqualTo("LOCAL_ONLY_RECORD")
            assertThat((repository.deleteExerciseRecord(item.recordId) as BaseResult.Error).errorCode).isEqualTo("LOCAL_ONLY_RECORD")
            assertThat(
                (repository.deleteExerciseRecordImages(item.recordId, listOf(1L)) as BaseResult.Error).errorCode,
            ).isEqualTo("LOCAL_ONLY_RECORD")
            assertThat(
                (repository.uploadExerciseRecordImages(item.recordId, emptyList()) as BaseResult.Error).errorCode,
            ).isEqualTo("LOCAL_ONLY_RECORD")
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
        }

    @Test
    fun `자신의 생성 날짜 이동 삭제는 로컬 재조회와 집계를 함께 갱신한다`() =
        runTest {
            local.replaceCountsByRange(date, nextDate, emptyList())
            local.replaceSyncedListByDate(date, emptyList())
            local.replaceSyncedListByDate(nextDate, emptyList())
            coEvery { remote.postExerciseRecord(any()) } returns response(PostExerciseResponseDTO(7L, 3))
            coEvery { remote.patchExerciseRecord(7L, any()) } returns response(ExerciseIdDto(7L))
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)

            val created = (repository.createExerciseRecord(record()) as BaseResult.Success).data
            val id = created.recordId
            assertThat(id).isNotEqualTo("7")
            assertThat(created.earnedPoints).isEqualTo(3)
            assertThat((repository.getExerciseRecordList(date, refresh = false) as BaseResult.Success).data.single().recordId).isEqualTo(id)
            assertThat(
                (repository.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success).data.sumOf { it.count },
            ).isEqualTo(1)

            repository.updateExerciseRecord(record(nextDate).copy(title = "옮긴 운동"), id)
            assertThat((repository.getExerciseRecordList(date, refresh = false) as BaseResult.Success).data).isEmpty()
            assertThat(
                (repository.getExerciseRecordList(nextDate, refresh = false) as BaseResult.Success).data.single().recordId,
            ).isEqualTo(id)
            assertThat((repository.getExerciseDetail(id, refresh = false) as BaseResult.Success).data.title).isEqualTo("옮긴 운동")
            val movedCounts =
                (repository.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success).data.associate { it.date to it.count }
            assertThat(movedCounts[date]).isEqualTo(0)
            assertThat(movedCounts[nextDate]).isEqualTo(1)

            repository.deleteExerciseRecord(id)
            assertThat((repository.getExerciseRecordList(nextDate, refresh = false) as BaseResult.Success).data).isEmpty()
            assertThat(repository.getExerciseDetail(id, refresh = false)).isInstanceOf(BaseResult.Error::class.java)
            assertThat(
                (repository.getExerciseCountByRange(date, nextDate, refresh = false) as BaseResult.Success).data.sumOf { it.count },
            ).isEqualTo(0)
            coVerify(exactly = 0) { remote.getExerciseList(any()) }
            coVerify(exactly = 0) { remote.getExerciseRecordDetail(any()) }
        }

    @Test
    fun `자신의 이미지 삭제는 썸네일과 상세를 맞추고 업로드 후 조회 실패는 이미지 캐시를 무효화한다`() =
        runTest {
            val id =
                local.reconcileRecord(
                    7L,
                    record().copy(pictures = listOf(ExerciseRecordPicture(1L, "one"), ExerciseRecordPicture(2L, "two"))),
                    memberId,
                )!!
            coEvery { remote.deleteExerciseImages(7L, listOf(1L)) } returns response<Unit?>(null)
            repository.deleteExerciseRecordImages(id, listOf(1L))
            assertThat(local.getListByDate(date).single().imageUrl).containsExactly("two")
            assertThat(local.getDetailByLocalId(id)?.pictures?.map { it.id }).containsExactly(2L)

            coEvery { remote.postExerciseImages(7L, any()) } returns response(ExerciseIdDto(7L))
            coEvery { remote.getExerciseRecordDetail(7L) } throws IOException("후속 상세 실패")
            assertThat(repository.uploadExerciseRecordImages(id, emptyList())).isInstanceOf(BaseResult.Success::class.java)
            assertThat(local.getDetailByLocalId(id)).isNull()
            assertThat(local.getListByDate(date).single().imageUrl).isNull()
        }

    @Test
    fun `이미지 업로드 뒤 실제 상세 응답의 URL을 동일한 로컬 기록에 반영한다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val image = File.createTempFile("exercise-upload-", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
            try {
                val imageBytes = byteArrayOf(1, 2, 3)
                image.writeBytes(imageBytes)
                val parts = slot<List<MultipartBody.Part>>()
                coEvery { remote.postExerciseImages(7L, capture(parts)) } returns response(ExerciseIdDto(7L))
                coEvery { remote.getExerciseRecordDetail(7L) } returns
                    response(
                        detailDto().copy(pictureDataList = listOf(PictureData(11L, "https://server/photo.jpg", "photo", "jpg"))),
                    )

                val result = repository.uploadExerciseRecordImages(id, listOf(Uri.fromFile(image).toString())) as BaseResult.Success
                assertThat(result.data).isEqualTo(id)
                assertThat(result.refreshError).isNull()
                val uploaded = Buffer()
                parts.captured.single().body.writeTo(uploaded)
                assertThat(uploaded.readByteArray()).isEqualTo(imageBytes)
                assertThat(local.getListByDate(date).single().imageUrl).containsExactly("https://server/photo.jpg")
                assertThat(local.getDetailByLocalId(id)?.pictures).containsExactly(ExerciseRecordPicture(11L, "https://server/photo.jpg"))
                assertThat(local.getListByDate(date).single().recordId).isEqualTo(id)
            } finally {
                image.delete()
            }
        }

    @Test
    fun `갱신과 자신의 쓰기가 겹쳐도 오래된 응답이 쓰기 뒤에 반영되지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val entered = CompletableDeferred<Unit>()
            val pendingResponse = CompletableDeferred<BaseResponse<List<GetExerciseRecordListDto>>>()
            coEvery { remote.getExerciseList(date) } coAnswers {
                entered.complete(Unit)
                pendingResponse.await()
            }
            coEvery { remote.patchExerciseRecord(7L, any()) } returns response(ExerciseIdDto(7L))
            val refresh = async { repository.getExerciseRecordList(date) }
            entered.await()
            val edit = async { repository.updateExerciseRecord(record().copy(title = "새 내용"), id) }
            pendingResponse.complete(response(listOf(itemDto())))
            refresh.await()
            edit.await()
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
    fun `플래그 OFF 삭제 뒤 ON 오프라인 조회는 이전 목록과 상세를 반환하지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            local.replaceCountsByRange(date, date, listOf(com.project200.domain.model.ExerciseCount(date, 1)))
            local.replaceSyncedListByDate(date, listOf(itemDto().toModel()))
            every { flags.isOfflineCacheEnabled() } returns false
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)
            assertThat(repository.deleteExerciseRecord("7")).isInstanceOf(BaseResult.Success::class.java)
            assertThat(invalidatedMembers).contains(memberId)

            every { flags.isOfflineCacheEnabled() } returns true
            every { network.isCurrentlyConnected() } returns false
            assertThat(repository.getExerciseRecordList(date, refresh = false)).isInstanceOf(BaseResult.Error::class.java)
            assertThat(repository.getExerciseDetail(id, refresh = false)).isInstanceOf(BaseResult.Error::class.java)
            assertThat(local.getDetailByLocalId(id)).isNull()
            assertThat(local.getCountsSnapshot(date, date, memberId)).isNull()
            assertThat(invalidatedMembers).isEmpty()
        }

    @Test
    fun `쓰기 반영 실패 뒤 다음 조회는 오래된 캐시를 버리고 모든 대기 상태를 보존한다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            local.replaceCountsByRange(date, date, listOf(com.project200.domain.model.ExerciseCount(date, 1)))
            local.replaceSyncedListByDate(date, listOf(itemDto().toModel()))
            val dao = database.exerciseRecordDao()
            dao.upsertListItems(
                listOf(dao.getListItem(memberId, id)!!.copy(memberId = "member-b", localId = "other-member", serverId = 8L)),
            )
            dao.upsertDetail(record().toSyncedEntity("member-b", "other-member", 8L))
            listOf(SyncState.CREATE_PENDING, SyncState.UPDATE_PENDING, SyncState.DELETE_PENDING).forEachIndexed { index, state ->
                val pendingId = "pending-$index"
                dao.upsertListItems(
                    listOf(
                        ExerciseListItemEntity(
                            memberId,
                            pendingId,
                            null,
                            state,
                            date,
                            index,
                            "대기",
                            "OTHER",
                            date.atTime(9, 0),
                            date.atTime(10, 0),
                            null,
                        ),
                    ),
                )
                dao.upsertDetail(record().toSyncedEntity(memberId, pendingId, 20L + index).copy(syncState = state))
            }
            val failingLocal = spyk(local)
            coEvery { failingLocal.reconcileDeletedRecord(7L, memberId) } throws IOException("Room 쓰기 실패")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    flags,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
                )
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)
            val result = writer.deleteExerciseRecord(id) as BaseResult.Success
            assertThat(result.refreshError?.errorCode).isEqualTo("CACHE_ERROR")
            assertThat(invalidatedMembers).contains(memberId)

            every { network.isCurrentlyConnected() } returns false
            val snapshot = repository.getExerciseRecordList(date, refresh = false) as BaseResult.Success
            assertThat(snapshot.data.map { it.recordId }).containsExactly("pending-0", "pending-1")
            assertThat(local.getDetailByLocalId(id)).isNull()
            listOf(SyncState.CREATE_PENDING, SyncState.UPDATE_PENDING, SyncState.DELETE_PENDING).forEachIndexed { index, state ->
                assertThat(dao.getListItem(memberId, "pending-$index")?.syncState).isEqualTo(state)
                assertThat(dao.getDetailByLocalId(memberId, "pending-$index")?.syncState).isEqualTo(state)
            }
            assertThat(dao.hasQuery(memberId, "list", date)).isFalse()
            assertThat(dao.hasCountRange(memberId, date, date, 1)).isFalse()
            assertThat(invalidatedMembers).isEmpty()
            assertThat(dao.getListItem("member-b", "other-member")).isNotNull()
            assertThat(dao.getDetailByLocalId("member-b", "other-member")).isNotNull()
            coVerify(exactly = 1) { remote.deleteExerciseRecord(7L) }
        }

    @Test
    fun `무효 캐시 정리가 실패하면 스냅샷을 읽지 않고 무효 표시를 유지한다`() =
        runTest {
            local.reconcileRecord(7L, record(), memberId)
            invalidatedMembers.add(memberId)
            val failingDao = spyk(database.exerciseRecordDao())
            coEvery { failingDao.deleteSyncedDetails(memberId) } throws IOException("정리 실패")
            val failingLocal = spyk(ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), failingDao, preferences))
            val reader =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    flags,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
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
    fun `플래그 OFF의 모든 서버 쓰기는 무효 표시 저장 실패를 성공 경고로 반환하고 재시도하지 않는다`() =
        runTest {
            every { flags.isOfflineCacheEnabled() } returns false
            val failure = IOException("무효 표시 저장 실패")
            every { preferences.setExerciseCacheInvalidated(memberId, true) } throws failure
            coEvery { remote.postExerciseRecord(any()) } returns response(PostExerciseResponseDTO(7L, 3))
            coEvery { remote.patchExerciseRecord(7L, any()) } returns response(ExerciseIdDto(7L))
            coEvery { remote.postExerciseImages(7L, any()) } returns response(ExerciseIdDto(7L))
            coEvery { remote.deleteExerciseImages(7L, any()) } returns response<Unit?>(null)
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)

            val created = repository.createExerciseRecord(record()) as BaseResult.Success
            val updated = repository.updateExerciseRecord(record(), "7") as BaseResult.Success
            val uploaded = repository.uploadExerciseRecordImages("7", emptyList()) as BaseResult.Success
            val imagesDeleted = repository.deleteExerciseRecordImages("7", listOf(1L)) as BaseResult.Success
            val deleted = repository.deleteExerciseRecord("7") as BaseResult.Success

            listOf(created, updated, uploaded, imagesDeleted, deleted).forEach { result ->
                assertThat(result.refreshError?.errorCode).isEqualTo("CACHE_ERROR")
                assertThat(result.refreshError?.cause).isSameInstanceAs(failure)
                assertThat(result.isCachedSnapshot).isFalse()
            }
            assertThat(created.data.recordId).isEqualTo("7")
            assertThat(created.data.earnedPoints).isEqualTo(3)
            assertThat(updated.data).isEqualTo("7")
            assertThat(uploaded.data).isEqualTo("7")
            assertThat(imagesDeleted.data).isEqualTo(Unit)
            assertThat(deleted.data).isEqualTo(Unit)
            assertThat(invalidatedMembers).isEmpty()
            verify(exactly = 5) { preferences.setExerciseCacheInvalidated(memberId, true) }
            coVerify(exactly = 1) { remote.postExerciseRecord(any()) }
            coVerify(exactly = 1) { remote.patchExerciseRecord(7L, any()) }
            coVerify(exactly = 1) { remote.postExerciseImages(7L, any()) }
            coVerify(exactly = 1) { remote.deleteExerciseImages(7L, listOf(1L)) }
            coVerify(exactly = 1) { remote.deleteExerciseRecord(7L) }
        }

    @Test
    fun `플래그 ON 캐시 반영과 무효 표시 저장이 함께 실패해도 서버 성공과 반영 오류를 반환한다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val failure = IOException("Room 반영 실패")
            val failingLocal = spyk(local)
            coEvery { failingLocal.reconcileDeletedRecord(7L, memberId) } throws failure
            every { preferences.setExerciseCacheInvalidated(memberId, true) } throws IOException("무효 표시 저장 실패")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    flags,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
                )
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)

            val result = writer.deleteExerciseRecord(id) as BaseResult.Success

            assertThat(result.data).isEqualTo(Unit)
            assertThat(result.refreshError?.errorCode).isEqualTo("CACHE_ERROR")
            assertThat(result.refreshError?.cause).isSameInstanceAs(failure)
            assertThat(result.isCachedSnapshot).isFalse()
            assertThat(invalidatedMembers).isEmpty()
            verify(exactly = 1) { preferences.setExerciseCacheInvalidated(memberId, true) }
            coVerify(exactly = 1) { remote.deleteExerciseRecord(7L) }
        }

    @Test
    fun `플래그 OFF 쓰기 중 계정이 바뀌어도 무효 표시 실패는 원래 회원의 경고로 반환한다`() =
        runTest {
            every { flags.isOfflineCacheEnabled() } returns false
            val failure = IOException("원래 회원 무효 표시 저장 실패")
            every { preferences.setExerciseCacheInvalidated("member-a", true) } throws failure
            coEvery { remote.deleteExerciseRecord(7L) } coAnswers {
                memberId = "member-b"
                response<Unit?>(null)
            }

            val result = repository.deleteExerciseRecord("7") as BaseResult.Success

            assertThat(result.refreshError?.errorCode).isEqualTo("CACHE_ERROR")
            assertThat(result.refreshError?.cause).isSameInstanceAs(failure)
            verify(exactly = 1) { preferences.setExerciseCacheInvalidated("member-a", true) }
            verify(exactly = 0) { preferences.setExerciseCacheInvalidated("member-b", any()) }
            assertThat(invalidatedMembers).isEmpty()
            coVerify(exactly = 1) { remote.deleteExerciseRecord(7L) }
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
    fun `플래그 OFF의 모든 서버 쓰기는 회원 캐시를 무효로 표시한다`() =
        runTest {
            every { flags.isOfflineCacheEnabled() } returns false
            coEvery { remote.postExerciseRecord(any()) } returns response(PostExerciseResponseDTO(7L, 3))
            coEvery { remote.patchExerciseRecord(7L, any()) } returns response(ExerciseIdDto(7L))
            coEvery { remote.postExerciseImages(7L, any()) } returns response(ExerciseIdDto(7L))
            coEvery { remote.deleteExerciseImages(7L, any()) } returns response<Unit?>(null)
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)
            val writes: List<suspend () -> BaseResult<*>> =
                listOf(
                    { repository.createExerciseRecord(record()) },
                    { repository.updateExerciseRecord(record(), "7") },
                    { repository.uploadExerciseRecordImages("7", emptyList()) },
                    { repository.deleteExerciseRecordImages("7", listOf(1L)) },
                    { repository.deleteExerciseRecord("7") },
                )
            writes.forEach { write ->
                invalidatedMembers.clear()
                assertThat(write()).isInstanceOf(BaseResult.Success::class.java)
                assertThat(invalidatedMembers).containsExactly(memberId)
            }
        }

    @Test
    fun `플래그 OFF 쓰기 후 무효 표시의 취소는 숨기거나 서버 쓰기를 반복하지 않는다`() =
        runTest {
            every { flags.isOfflineCacheEnabled() } returns false
            every { preferences.setExerciseCacheInvalidated(any(), true) } throws CancellationException("무효 표시 취소")
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)
            try {
                repository.deleteExerciseRecord("7")
                throw AssertionError("취소가 전파되지 않았습니다")
            } catch (expected: CancellationException) {
                assertThat(expected.message).isEqualTo("무효 표시 취소")
            }
            coVerify(exactly = 1) { remote.deleteExerciseRecord(7L) }
        }

    @Test
    fun `플래그 ON 반영 실패 후 무효 표시의 취소는 캐시 경고로 숨기지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val failingLocal = spyk(local)
            coEvery { failingLocal.reconcileDeletedRecord(7L, memberId) } throws IOException("Room 반영 실패")
            every { preferences.setExerciseCacheInvalidated(memberId, true) } throws CancellationException("무효 표시 취소")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    flags,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
                )
            coEvery { remote.deleteExerciseRecord(7L) } returns response<Unit?>(null)

            try {
                writer.deleteExerciseRecord(id)
                throw AssertionError("취소가 전파되지 않았습니다")
            } catch (expected: CancellationException) {
                assertThat(expected.message).isEqualTo("무효 표시 취소")
            }
            coVerify(exactly = 1) { remote.deleteExerciseRecord(7L) }
        }

    @Test
    fun `무효 상세의 온라인 재조회는 열린 화면의 로컬 식별자를 유지한다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            invalidatedMembers.add(memberId)
            coEvery { remote.getExerciseRecordDetail(7L) } returns response(detailDto().copy(exerciseTitle = "새 상세"))
            val result = repository.getExerciseDetail(id, refresh = false) as BaseResult.Success
            assertThat(local.getDetailByLocalId(id)).isEqualTo(result.data)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)?.serverId).isEqualTo(7L)
            coVerify(exactly = 1) { remote.getExerciseRecordDetail(7L) }
        }

    private fun record(day: LocalDate = date) = ExerciseRecord("운동", "본문", "OTHER", day.atTime(9, 0), day.atTime(10, 0), "공원", null)

    private fun itemDto() = GetExerciseRecordListDto(7L, "운동", "OTHER", date.atTime(9, 0), date.atTime(10, 0), null)

    private fun detailDto() = GetExerciseRecordData("운동", "본문", "OTHER", date.atTime(9, 0), date.atTime(10, 0), "공원", null)

    private fun <T> response(data: T): BaseResponse<T> = BaseResponse(succeed = true, code = "200", message = "", data = data)
}
