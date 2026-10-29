package com.project200.data.impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.NetworkMonitor
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
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
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncEntity
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toSyncedEntity
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.model.ExerciseWriteInput
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
import kotlinx.coroutines.cancelAndJoin
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
    fun `로컬 생성 대기는 본문 수정 삭제가 가능하고 사진 변경만 거절한다`() =
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
            assertThat((repository.updateExerciseRecord(ExerciseWriteInput(record(), listOf("photo")), id) as BaseResult.Error).errorCode)
                .isEqualTo("PHOTO_NOT_READY")
            assertThat((repository.deleteExerciseRecordImages(id, listOf(1L)) as BaseResult.Error).errorCode).isEqualTo("PHOTO_NOT_READY")
            assertThat((repository.uploadExerciseRecordImages(id, emptyList()) as BaseResult.Error).errorCode).isEqualTo("PHOTO_NOT_READY")
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
            val id = local.reconcileRecord(7L, record(), owner)!!
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
                    memberId, id, 7L, SyncState.DELETE_PENDING, 1L, "delete-operation", null, 9L, date, date,
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
            coEvery { failingLocal.reconcileRecord(7L, any(), memberId) } throws IOException("Room 쓰기 실패")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
                )
            coEvery { remote.deleteExerciseImages(7L, listOf(1L)) } returns response<Unit?>(null)
            val result = writer.deleteExerciseRecordImages(id, listOf(1L)) as BaseResult.Success
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
            coVerify(exactly = 1) { remote.deleteExerciseImages(7L, listOf(1L)) }
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
    fun `캐시 반영과 무효 표시 저장이 함께 실패해도 서버 성공과 반영 오류를 반환한다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val failure = IOException("Room 반영 실패")
            val failingLocal = spyk(local)
            coEvery { failingLocal.reconcileRecord(7L, any(), memberId) } throws failure
            every { preferences.setExerciseCacheInvalidated(memberId, true) } throws IOException("무효 표시 저장 실패")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
                )
            coEvery { remote.deleteExerciseImages(7L, listOf(1L)) } returns response<Unit?>(null)

            val result = writer.deleteExerciseRecordImages(id, listOf(1L)) as BaseResult.Success

            assertThat(result.data).isEqualTo(Unit)
            assertThat(result.refreshError?.errorCode).isEqualTo("CACHE_ERROR")
            assertThat(result.refreshError?.cause).isSameInstanceAs(failure)
            assertThat(result.isCachedSnapshot).isFalse()
            assertThat(invalidatedMembers).isEmpty()
            verify(exactly = 1) { preferences.setExerciseCacheInvalidated(memberId, true) }
            coVerify(exactly = 1) { remote.deleteExerciseImages(7L, listOf(1L)) }
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
    fun `반영 실패 후 무효 표시의 취소는 캐시 경고로 숨기지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val failingLocal = spyk(local)
            coEvery { failingLocal.reconcileRecord(7L, any(), memberId) } throws IOException("Room 반영 실패")
            every { preferences.setExerciseCacheInvalidated(memberId, true) } throws CancellationException("무효 표시 취소")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote,
                    failingLocal,
                    network,
                    UnconfinedTestDispatcher(),
                    RuntimeEnvironment.getApplication(),
                )
            coEvery { remote.deleteExerciseImages(7L, listOf(1L)) } returns response<Unit?>(null)

            try {
                writer.deleteExerciseRecordImages(id, listOf(1L))
                throw AssertionError("취소가 전파되지 않았습니다")
            } catch (expected: CancellationException) {
                assertThat(expected.message).isEqualTo("무효 표시 취소")
            }
            coVerify(exactly = 1) { remote.deleteExerciseImages(7L, listOf(1L)) }
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

    @Test
    fun `캐시 집계와 상세는 서버 갱신이 대기하는 동안 즉시 읽을 수 있다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
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
    fun `진행 중 쓰기와 겹친 조회는 쓰기 완료 전에도 오래된 응답을 반영하지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val entered = CompletableDeferred<Unit>()
            val writeResponse = CompletableDeferred<BaseResponse<ExerciseIdDto>>()
            coEvery { remote.patchExerciseRecord(7L, any()) } coAnswers {
                entered.complete(Unit)
                writeResponse.await()
            }
            val edit =
                async { repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "내 수정"), listOf("photo")), id) }
            entered.await()
            coEvery { remote.getExerciseList(date) } returns response(listOf(itemDto().copy(exerciseTitle = "쓰기 전 서버")))
            val result = repository.getExerciseRecordList(date, refresh = true) as BaseResult.Success
            assertThat(result.data.single().title).isEqualTo("운동")
            assertThat(result.isCachedSnapshot).isTrue()
            assertThat(edit.isCompleted).isFalse()
            writeResponse.complete(response(ExerciseIdDto(7L)))
            assertThat(edit.await()).isInstanceOf(BaseResult.Success::class.java)
            assertThat(local.getListByDate(date).single().title).isEqualTo("내 수정")
        }

    @Test
    fun `실패하거나 취소한 쓰기도 이전 GET을 무효화하고 다음 갱신을 막지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            for (cancelWrite in listOf(false, true)) {
                val entered = CompletableDeferred<Unit>()
                val pendingResponse = CompletableDeferred<BaseResponse<List<GetExerciseRecordListDto>>>()
                coEvery { remote.getExerciseList(date) } coAnswers {
                    entered.complete(Unit)
                    pendingResponse.await()
                }
                val refresh = async { repository.getExerciseRecordList(date, refresh = true) }
                entered.await()
                if (cancelWrite) {
                    val writeEntered = CompletableDeferred<Unit>()
                    val blocked = CompletableDeferred<BaseResponse<ExerciseIdDto>>()
                    coEvery { remote.patchExerciseRecord(7L, any()) } coAnswers {
                        writeEntered.complete(Unit)
                        blocked.await()
                    }
                    val edit = async { repository.updateExerciseRecord(ExerciseWriteInput(record(), listOf("photo")), id) }
                    writeEntered.await()
                    edit.cancelAndJoin()
                } else {
                    coEvery { remote.patchExerciseRecord(7L, any()) } throws IOException("쓰기 실패")
                    assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record(), listOf("photo")), id))
                        .isInstanceOf(BaseResult.Error::class.java)
                }
                pendingResponse.complete(response(listOf(itemDto().copy(exerciseTitle = "오래된 응답"))))
                assertThat((refresh.await() as BaseResult.Success).data.single().title).isEqualTo("운동")
                assertThat(local.getDetailByLocalId(id)?.title).isEqualTo("운동")
            }
            coEvery { remote.getExerciseList(date) } returns response(listOf(itemDto().copy(exerciseTitle = "명시 갱신")))
            val result = repository.getExerciseRecordList(date, refresh = true) as BaseResult.Success
            assertThat(result.data.single().title).isEqualTo("명시 갱신")
        }

    @Test
    fun `쓰기 응답 중 계정 변경은 다른 회원 캐시나 성공 결과를 만들지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            coEvery { remote.patchExerciseRecord(7L, any()) } coAnswers {
                memberId = "member-b"
                response(ExerciseIdDto(7L))
            }
            val result =
                repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "수정"), listOf("photo")), id) as BaseResult.Error
            assertThat(result.errorCode).isEqualTo("ACCOUNT_CHANGED")
            assertThat(database.exerciseRecordDao().getListByDate("member-b", date)).isEmpty()
            assertThat(database.exerciseRecordDao().getDetailByLocalId("member-a", id)?.title).isEqualTo("운동")
        }

    @Test
    fun `서버 기록의 본문 수정과 삭제는 오프라인에서도 저널만 저장하고 원격 호출하지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
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
    fun `사진이 있는 생성은 온라인 본문 경로를 사용하고 점수 없는 저장 결과만 반환한다`() =
        runTest {
            coEvery { remote.postExerciseRecord(any()) } returns response(PostExerciseResponseDTO(7L, 3))
            val result = repository.createExerciseRecord(ExerciseWriteInput(record(), listOf("photo"))) as BaseResult.Success
            val id = result.data.recordId
            assertThat(id).isNotEqualTo("7")
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)?.serverId).isEqualTo(7L)
            assertThat(database.exerciseSyncDao().get(memberId, id)).isNull()
            coVerify(exactly = 1) { remote.postExerciseRecord(any()) }
        }

    @Test
    fun `사진 온라인 삭제 대기 중 로컬 본문 수정은 즉시 저장하고 늦은 응답은 복구 상태만 남긴다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            val entered = CompletableDeferred<Unit>()
            val pending = CompletableDeferred<BaseResponse<Unit?>>()
            coEvery { remote.deleteExerciseImages(7L, listOf(1L)) } coAnswers {
                entered.complete(Unit)
                pending.await()
            }
            val photoWrite = async { repository.deleteExerciseRecordImages(id, listOf(1L)) }
            entered.await()
            val result = repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "즉시 저장")), id)
            assertThat(result).isInstanceOf(BaseResult.Success::class.java)
            val original = database.exerciseSyncDao().get(memberId, id)!!
            assertThat(photoWrite.isCompleted).isFalse()
            pending.complete(response<Unit?>(null))
            val finished = photoWrite.await() as BaseResult.Success
            assertThat(finished.refreshError?.errorCode).isEqualTo("CACHE_ERROR")
            assertThat(local.getDetailByLocalId(id)?.title).isEqualTo("즉시 저장")
            val latest = database.exerciseSyncDao().get(memberId, id)!!
            assertThat(latest).isEqualTo(
                original.copy(recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED, lastError = ExerciseSyncError.CONFLICT),
            )
        }

    @Test
    fun `로컬 트랜잭션 실패는 저장 성공이나 서버 재시도로 바꾸지 않는다`() =
        runTest {
            val failingLocal = spyk(local)
            coEvery { failingLocal.createRecord(any(), memberId) } throws IOException("Room 저장 실패")
            val writer =
                ExerciseRecordRepositoryImpl(
                    remote, failingLocal, network, UnconfinedTestDispatcher(), RuntimeEnvironment.getApplication(),
                )
            assertThat(writer.createExerciseRecord(ExerciseWriteInput(record()))).isInstanceOf(BaseResult.Error::class.java)
            assertThat(local.getListByDate(date)).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
        }

    @Test
    fun `수정 대기 기록의 사진 단독 및 본문 동반 변경은 원격 부작용 전에 거절한다`() =
        runTest {
            val id = local.reconcileRecord(7L, record(), memberId)!!
            repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "미전송 본문")), id)
            val journal = database.exerciseSyncDao().get(memberId, id)!!
            val item = database.exerciseRecordDao().getListItem(memberId, id)
            val detail = database.exerciseRecordDao().getDetailByLocalId(memberId, id)
            val update = repository.updateExerciseRecord(ExerciseWriteInput(record(), listOf("photo")), id) as BaseResult.Error
            val upload = repository.uploadExerciseRecordImages(id, listOf("photo")) as BaseResult.Error
            val delete = repository.deleteExerciseRecordImages(id, listOf(1L)) as BaseResult.Error
            assertThat(update.errorCode).isEqualTo("PHOTO_NOT_READY")
            assertThat(upload.errorCode).isEqualTo("PHOTO_NOT_READY")
            assertThat(delete.errorCode).isEqualTo("PHOTO_NOT_READY")
            assertThat(database.exerciseSyncDao().get(memberId, id)).isEqualTo(journal)
            assertThat(database.exerciseRecordDao().getListItem(memberId, id)).isEqualTo(item)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)).isEqualTo(detail)
            assertThat((repository.getExerciseDetail("missing-local") as BaseResult.Error).errorCode).isEqualTo("LOCAL_ONLY_RECORD")
            coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
            coVerify(exactly = 0) { remote.postExerciseImages(any(), any()) }
            coVerify(exactly = 0) { remote.deleteExerciseImages(any(), any()) }
        }

    @Test
    fun `기록 생성은 기존 사진 삭제를 허용하지 않고 잘못된 삭제 ID도 빈 성공으로 바꾸지 않는다`() =
        runTest {
            assertThat(repository.createExerciseRecord(ExerciseWriteInput(record(), removedImageIds = setOf(1))))
                .isInstanceOf(BaseResult.Error::class.java)
            assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record(), removedImageIds = setOf(-1)), "7"))
                .isInstanceOf(BaseResult.Error::class.java)
            assertThat(repository.deleteExerciseRecordImages("7", emptyList())).isInstanceOf(BaseResult.Error::class.java)
            assertThat(repository.deleteExerciseRecordImages("7", listOf(-1))).isInstanceOf(BaseResult.Error::class.java)
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
    fun `온라인 사진 동반 본문 응답은 더 최신인 날짜 이동이나 삭제를 완료 처리하지 않는다`() =
        runTest {
            for (deleteLocal in listOf(false, true)) {
                val server = if (deleteLocal) 8L else 7L
                val id = local.reconcileRecord(server, record(), memberId)!!
                val entered = CompletableDeferred<Unit>()
                val pending = CompletableDeferred<BaseResponse<ExerciseIdDto>>()
                coEvery { remote.patchExerciseRecord(server, any()) } coAnswers {
                    entered.complete(Unit)
                    pending.await()
                }
                val online = async {
                    repository.updateExerciseRecord(ExerciseWriteInput(record(nextDate), listOf("photo")), id)
                }
                entered.await()
                repository.updateExerciseRecord(ExerciseWriteInput(record().copy(title = "최신 로컬")), id)
                if (deleteLocal) repository.deleteExerciseRecord(id)
                val original = database.exerciseSyncDao().get(memberId, id)!!
                pending.complete(response(ExerciseIdDto(server)))
                assertThat((online.await() as BaseResult.Success).refreshError?.errorCode).isEqualTo("CACHE_ERROR")
                assertThat(database.exerciseSyncDao().get(memberId, id)).isEqualTo(
                    original.copy(recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED, lastError = ExerciseSyncError.CONFLICT),
                )
                assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)?.title).isEqualTo("최신 로컬")
                assertThat(local.getDetailByLocalId(id)?.title).isEqualTo(if (deleteLocal) null else "최신 로컬")
                if (deleteLocal) assertThat(local.getDetailByServerId(server)).isNull()
            }
        }

    @Test
    fun `이미 시작한 서버 ID 상세 갱신도 그 사이 생긴 삭제 tombstone을 부활시키지 않는다`() =
        runTest {
            val id = local.reconcileRecord(7, record(), memberId)!!
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

    @Test
    fun `사진 업로드와 후속 상세 응답은 새 로컬 삭제를 덮지 않고 원래 저널을 보존한다`() =
        runTest {
            val id = local.reconcileRecord(7, record(), memberId)!!
            val image = File.createTempFile("exercise-race-", ".jpg", RuntimeEnvironment.getApplication().cacheDir)
            try {
                image.writeBytes(byteArrayOf(1, 2, 3))
                val entered = CompletableDeferred<Unit>()
                val pending = CompletableDeferred<BaseResponse<ExerciseIdDto>>()
                coEvery { remote.postExerciseImages(7, any()) } coAnswers {
                    entered.complete(Unit)
                    pending.await()
                }
                coEvery { remote.getExerciseRecordDetail(7) } returns
                    response(detailDto().copy(pictureDataList = listOf(PictureData(11, "https://server/photo.jpg", "photo", "jpg"))))
                val upload = async { repository.uploadExerciseRecordImages(id, listOf(Uri.fromFile(image).toString())) }
                entered.await()
                repository.deleteExerciseRecord(id)
                val original = database.exerciseSyncDao().get(memberId, id)!!
                pending.complete(response(ExerciseIdDto(7)))
                assertThat((upload.await() as BaseResult.Success).refreshError?.errorCode).isEqualTo("CACHE_ERROR")
                assertThat(local.getDetailByLocalId(id)).isNull()
                assertThat(local.getListByDate(date)).isEmpty()
                assertThat(database.exerciseSyncDao().get(memberId, id)).isEqualTo(
                    original.copy(recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED, lastError = ExerciseSyncError.CONFLICT),
                )
            } finally {
                image.delete()
            }
        }

    @Test
    fun `사진 동반 온라인 본문 ACK에는 새 기준이 없으므로 이전 버전과 날짜를 재사용하지 않는다`() =
        runTest {
            local.replaceSyncedListByDate(date, listOf(itemDto().toModel()))
            val id = local.saveSyncedDetail(7, record())!!
            val dao = database.exerciseRecordDao()
            dao.upsertDetail(dao.getDetailByLocalId(memberId, id)!!.copy(serverVersion = 42))
            coEvery { remote.patchExerciseRecord(7, any()) } returns response(ExerciseIdDto(7))
            val saved = repository.updateExerciseRecord(ExerciseWriteInput(record(nextDate), listOf("photo")), id) as BaseResult.Success
            assertThat(saved.refreshError).isNull()
            assertThat(dao.getListItem(memberId, id)?.confirmedDate).isNull()
            assertThat(dao.getDetailByLocalId(memberId, id)?.serverVersion).isNull()
            repository.updateExerciseRecord(ExerciseWriteInput(record(nextDate).copy(title = "다음 로컬 수정")), id)
            val journal = database.exerciseSyncDao().get(memberId, id)!!
            assertThat(journal.confirmedDate).isNull()
            assertThat(journal.serverVersion).isNull()
            assertThat(journal.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
        }

    @Test
    fun `캐시 없는 기존 기록의 사진 동반 수정은 알려진 서버 횟수에 신규 생성을 더하지 않는다`() =
        runTest {
            local.replaceCountsByRange(date, date, listOf(com.project200.domain.model.ExerciseCount(date, 3)))
            coEvery { remote.patchExerciseRecord(7, any()) } returns response(ExerciseIdDto(7))
            assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record(), listOf("photo")), "7"))
                .isInstanceOf(BaseResult.Success::class.java)
            assertThat(local.getCountsByRange(date, date).single().count).isEqualTo(3)
            assertThat(database.exerciseCountDao().getRange(memberId, date, date).single().count).isEqualTo(3)
        }

    private fun record(day: LocalDate = date) = ExerciseRecord("운동", "본문", "OTHER", day.atTime(9, 0), day.atTime(10, 0), "공원", null)

    private fun itemDto() = GetExerciseRecordListDto(7L, "운동", "OTHER", date.atTime(9, 0), date.atTime(10, 0), null)

    private fun detailDto() = GetExerciseRecordData("운동", "본문", "OTHER", date.atTime(9, 0), date.atTime(10, 0), "공원", null)

    private fun <T> response(data: T): BaseResponse<T> = BaseResponse(succeed = true, code = "200", message = "", data = data)
}
