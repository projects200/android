package com.project200.data.impl

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.common.utils.NetworkMonitor
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.entity.CachedPicture
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseImageSyncStatus
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
import com.project200.domain.model.SessionExitReason
import com.project200.domain.usecase.ClearSessionUseCase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.time.LocalDate

/** 실제 개인 파일과 재개방 가능한 Room으로 사용자에게 보이는 사진의 수명을 검증합니다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseOwnedPhotosTest {
    private val context = RuntimeEnvironment.getApplication()
    private val root = File(context.filesDir, "owned-photo-tests")
    private val resolver = mockk<ContentResolver>()
    private val fileContext = mockk<Context>()
    private val remote = mockk<ExerciseRecordRemoteDataSource>()
    private val network = mockk<NetworkMonitor>()
    private val preferences = mockk<PreferenceManager>(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()
    private val date = LocalDate.of(2025, 1, 10)
    private val databaseName = "owned-photos.db"
    private var memberId = "member-a"
    private lateinit var database: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource
    private lateinit var store: ExerciseImageFileStore
    private lateinit var repository: ExerciseRecordRepositoryImpl
    private var openSource: (Uri) -> InputStream? = { FileInputStream(File(requireNotNull(it.path))) }

    @Before
    fun setUp() {
        root.mkdirs()
        every { fileContext.filesDir } returns root
        every { fileContext.contentResolver } returns resolver
        every { resolver.getType(any()) } returns "image/png"
        every { resolver.openInputStream(any()) } answers { openSource(firstArg()) }
        every { preferences.getMemberId() } answers { memberId }
        every { network.isCurrentlyConnected() } returns false
        store = ExerciseImageFileStore(fileContext)
        openDatabase()
    }

    private fun openDatabase() {
        database =
            Room.databaseBuilder(context, UndabangDatabase::class.java, databaseName)
                .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .allowMainThreadQueries()
                .build()
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
        repository = writer(local)
    }

    private fun writer(source: ExerciseRecordLocalDataSource) =
        ExerciseRecordRepositoryImpl(
            remote, source, network, dispatcher, store, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), preferences, mockk(relaxed = true),
        )

    private fun reopen() {
        database.close()
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        root.deleteRecursively()
    }

    private fun source(name: String = "selected.png"): File =
        File(root, name).also { file ->
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }

    private fun uri(file: File): String = Uri.fromFile(file).toString()

    private fun record(title: String = "운동") =
        ExerciseRecord(title, "본문", "OTHER", date.atTime(9, 0), date.atTime(10, 0), "장소", null)

    private suspend fun create(file: File): String =
        (repository.createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(file)))) as BaseResult.Success).data.recordId

    private suspend fun picture(id: String): ExerciseRecordPicture =
        (repository.getExerciseDetail(id, localOnly = true) as BaseResult.Success).data.pictures!!.single()

    private fun ownedFiles(): List<File> = File(root, "exercise-images").listFiles()?.toList().orEmpty()

    private fun assertNoRemoteWrites() {
        coVerify(exactly = 0) { remote.postExerciseRecord(any()) }
        coVerify(exactly = 0) { remote.patchExerciseRecord(any(), any()) }
        coVerify(exactly = 0) { remote.postExerciseImages(any(), any()) }
        coVerify(exactly = 0) { remote.deleteExerciseImages(any(), any()) }
        coVerify(exactly = 0) { remote.deleteExerciseRecord(any()) }
    }

    @Test
    fun `선택 원본을 잃어도 재개방한 상세와 썸네일은 같은 ID와 실제 디코딩 가능한 바이트를 표시한다`() =
        runTest {
            val selected = source()
            val bytes = selected.readBytes()
            val id = create(selected)
            val original = picture(id)
            val metadata = database.exerciseImageDao().getByRecord(memberId, id).single()
            assertThat(original.serverId).isNull()
            assertThat(original.syncStatus).isEqualTo(ExerciseImageSyncStatus.PENDING)
            assertThat(original.localId).isEqualTo(metadata.localId)
            assertThat(metadata.requestId).isNotEmpty()
            assertThat(selected.exists()).isTrue()
            selected.delete()
            reopen()
            assertThat(picture(id)).isEqualTo(original)
            val owned = File(requireNotNull(Uri.parse(original.source).path))
            assertThat(owned.readBytes()).isEqualTo(bytes)
            assertThat(BitmapFactory.decodeFile(owned.path)).isNotNull()
            val items = (repository.getExerciseRecordList(date, localOnly = true) as BaseResult.Success).data
            assertThat(items.single().imageUrl).containsExactly(original.source)
            assertThat(database.exerciseImageDao().getByRecord(memberId, id).single()).isEqualTo(metadata)
            assertThat(database.exerciseRecordDao().getDetailByLocalId(memberId, id)?.pictures).isNull()
            assertNoRemoteWrites()
        }

    @Test
    fun `여러 사진 중 하나의 준비 실패는 새 파일만 정리하고 원본과 기존 기록을 보존한다`() =
        runTest {
            val selected = source()
            val id = create(selected)
            val before = picture(id)
            val files = ownedFiles()
            val result = repository.updateExerciseRecord(
                ExerciseWriteInput(record("저장 안 됨"), listOf(uri(selected), uri(File(root, "missing.png")))), id,
            )
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            assertThat(ownedFiles()).containsExactlyElementsIn(files)
            assertThat(picture(id)).isEqualTo(before)
            assertThat(local.getDetailByLocalId(id)?.title).isEqualTo("운동")
            assertThat(selected.exists()).isTrue()
            assertNoRemoteWrites()
        }

    @Test
    fun `사진 메타데이터 DB 실패는 본문 저널과 사진을 함께 롤백하고 준비 파일만 제거한다`() =
        runTest {
            val selected = source()
            val id = create(selected)
            val original = database.exerciseSyncDao().get(memberId, id)
            val before = picture(id)
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_owned_image BEFORE INSERT ON exercise_image " +
                    "BEGIN SELECT RAISE(ABORT, 'PHOTO_STORAGE_FAILURE'); END",
            )
            val result = repository.updateExerciseRecord(ExerciseWriteInput(record("실패"), listOf(uri(selected))), id)
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            reopen()
            assertThat(database.exerciseSyncDao().get(memberId, id)).isEqualTo(original)
            assertThat(local.getDetailByLocalId(id)?.title).isEqualTo("운동")
            assertThat(picture(id)).isEqualTo(before)
            assertThat(ownedFiles()).hasSize(1)
            assertThat(selected.exists()).isTrue()
        }

    @Test
    fun `준비 후 저장 전 취소는 완료 없이 새 파일을 정리한다`() =
        runTest {
            val selected = source()
            val entered = CompletableDeferred<Unit>()
            val blocked = spyk(local)
            coEvery { blocked.createRecord(any(), any(), any()) } coAnswers {
                entered.complete(Unit)
                awaitCancellation()
            }
            val pending = async { writer(blocked).createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(selected)))) }
            entered.await()
            assertThat(ownedFiles()).hasSize(1)
            pending.cancelAndJoin()
            assertThat(ownedFiles()).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            assertThat(selected.exists()).isTrue()
        }

    @Test
    fun `DB 확정 직후 취소되어 결과를 못 받아도 참조 중인 파일과 ID는 보존한다`() =
        runTest {
            val selected = source()
            val committed = CompletableDeferred<LocalExerciseSaveResult>()
            val delayed = spyk(local)
            coEvery { delayed.createRecord(any(), any(), any()) } coAnswers {
                val saved = local.createRecord(firstArg(), secondArg(), thirdArg())
                committed.complete(saved)
                awaitCancellation()
            }
            val pending = async { writer(delayed).createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(selected)))) }
            val saved = committed.await()
            val before = picture(saved.recordId)
            pending.cancelAndJoin()
            reopen()
            assertThat(picture(saved.recordId)).isEqualTo(before)
            assertThat(ownedFiles()).hasSize(1)
            assertThat(File(requireNotNull(Uri.parse(before.source).path)).exists()).isTrue()
        }

    @Test
    fun `복사 중 계정 변경은 어느 계정에도 본문이나 사진을 확정하지 않는다`() =
        runTest {
            val selected = source()
            openSource = { uri ->
                object : FileInputStream(File(requireNotNull(uri.path))) {
                    override fun read(buffer: ByteArray): Int {
                        val count = super.read(buffer)
                        memberId = "member-b"
                        return count
                    }
                }
            }
            assertThat(repository.createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(selected)))))
                .isInstanceOf(BaseResult.Error::class.java)
            reopen()
            assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember("member-b")).isEmpty()
            assertThat(ownedFiles()).isEmpty()
            assertThat(selected.exists()).isTrue()
            assertNoRemoteWrites()
        }

    @Test
    fun `미전송 사진 제거와 미전송 기록 삭제는 파일을 취소하되 다른 기록과 계정 파일은 건드리지 않는다`() =
        runTest {
            val selected = source()
            val first = create(selected)
            val firstPhoto = picture(first)
            val second = create(selected)
            val secondPhoto = picture(second)
            memberId = "member-b"
            val other = create(selected)
            val otherPhoto = picture(other)
            memberId = "member-a"
            val invalid = repository.updateExerciseRecord(
                ExerciseWriteInput(record("잘못된 변경"), removedImageLocalIds = setOf(otherPhoto.localId)), first,
            )
            assertThat(invalid).isInstanceOf(BaseResult.Error::class.java)
            assertThat(local.getDetailByLocalId(first)?.title).isEqualTo("운동")
            repository.updateExerciseRecord(ExerciseWriteInput(record(), removedImageLocalIds = setOf(firstPhoto.localId)), first)
            assertThat(local.getDetailByLocalId(first)?.pictures).isEmpty()
            assertThat(database.exerciseImageDao().getByRecord(memberId, first)).isEmpty()
            assertThat(File(requireNotNull(Uri.parse(firstPhoto.source).path)).exists()).isFalse()
            repository.deleteExerciseRecord(second)
            assertThat(database.exerciseSyncDao().get(memberId, second)).isNull()
            assertThat(File(requireNotNull(Uri.parse(secondPhoto.source).path)).exists()).isFalse()
            assertThat(File(requireNotNull(Uri.parse(otherPhoto.source).path)).exists()).isTrue()
            assertThat(selected.exists()).isTrue()
            assertNoRemoteWrites()
        }

    @Test
    fun `서버 사진 삭제와 로컬 추가는 한 번에 보이며 오래된 상세와 목록으로 부활하지 않는다`() =
        runTest {
            val serverPicture = ExerciseRecordPicture("server:9", 9L, "https://example.test/9.png", ExerciseImageSyncStatus.SYNCED)
            val snapshot = record().copy(pictures = listOf(serverPicture))
            val id = local.saveSyncedDetail(7, snapshot)!!
            local.replaceSyncedListByDate(date, listOf(serverItem()))
            val selected = source()
            val input = ExerciseWriteInput(record("수정"), listOf(uri(selected)), setOf(serverPicture.localId))
            assertThat(repository.updateExerciseRecord(input, id)).isInstanceOf(BaseResult.Success::class.java)
            val visible = picture(id)
            val deletion = database.exerciseImageDao().getByRecord(memberId, id).single { it.localId == serverPicture.localId }
            assertThat(deletion.state).isEqualTo(ExerciseImageState.DELETE_PENDING)
            assertThat(deletion.serverPictureId).isEqualTo(9L)
            assertThat(deletion.requestId).isNotEmpty()
            local.saveSyncedDetail(7, snapshot)
            local.replaceSyncedListByDate(date, listOf(serverItem()))
            database.exerciseImageDao().refreshSynced(memberId, id, listOf(CachedPicture(9, serverPicture.source)))
            reopen()
            assertThat(picture(id)).isEqualTo(visible)
            assertThat(local.getListByDate(date).single().imageUrl).containsExactly(visible.source)
            assertThat(database.exerciseImageDao().getByRecord(memberId, id)).contains(deletion)
            assertNoRemoteWrites()
        }

    @Test
    fun `전송 여부 불명 사진은 서버 ID가 없어도 삭제 시 바이트와 요청 키를 유지한다`() =
        runTest {
            val id = create(source())
            val photo = database.exerciseImageDao().getByRecord(memberId, id).single()
            database.exerciseImageDao().save(photo.copy(state = ExerciseImageState.ADD_PENDING))
            repository.updateExerciseRecord(ExerciseWriteInput(record(), removedImageLocalIds = setOf(photo.localId)), id)
            val hidden = database.exerciseImageDao().getByRecord(memberId, id).single()
            assertThat(hidden).isEqualTo(photo.copy(state = ExerciseImageState.DELETE_PENDING))
            assertThat(local.getDetailByLocalId(id)?.pictures).isEmpty()
            repository.deleteExerciseRecord(id)
            reopen()
            val retained = database.exerciseImageDao().getByRecord(memberId, id).single()
            assertThat(retained).isEqualTo(photo.copy(state = ExerciseImageState.DELETE_PENDING))
            assertThat(File(retained.localFilePath!!).exists()).isTrue()
            assertThat(database.exerciseSyncDao().get(memberId, id)?.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(local.getDetailByLocalId(id)).isNull()
            assertNoRemoteWrites()
        }

    @Test
    fun `본문 전송 중 사진 재편집은 생성 키와 불변 본문 원문 및 모르는 확정 날짜를 유지한다`() =
        runTest {
            val selected = source()
            val id = create(selected)
            val original = database.exerciseSyncDao().get(memberId, id)!!
            database.exerciseSyncDao().claim(
                memberId, id, original.revision!!, original.operationId!!, original.createRequestId!!, "immutable-body-A", null,
            )
            val oldPicture = picture(id)
            repository.updateExerciseRecord(
                ExerciseWriteInput(record("새 본문 B"), listOf(uri(selected)), setOf(oldPicture.localId)), id,
            )
            reopen()
            val latest = database.exerciseSyncDao().get(memberId, id)!!
            assertThat(latest.inFlightBody).isEqualTo("immutable-body-A")
            assertThat(latest.inFlightRequestId).isEqualTo(original.createRequestId)
            assertThat(latest.createRequestId).isEqualTo(original.createRequestId)
            assertThat(latest.confirmedDate).isNull()
            assertThat(local.getDetailByLocalId(id)?.title).isEqualTo("새 본문 B")
            assertThat(picture(id).localId).isNotEqualTo(oldPicture.localId)
            repository.deleteExerciseRecord(id)
            assertThat(database.exerciseSyncDao().get(memberId, id)?.inFlightBody).isEqualTo("immutable-body-A")
        }

    @Test
    fun `강제 이탈과 오래된 캐시 정리는 소유 파일을 보존하고 명시적 이탈은 원본 외 파일과 DB를 지운다`() =
        runTest {
            val selected = source()
            val id = create(selected)
            val photo = picture(id)
            val cleaner = RoomSessionDataCleaner(database, dispatcher, store)
            val clearSession = ClearSessionUseCase(
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), cleaner, mockk(relaxed = true),
            )
            clearSession(SessionExitReason.FORCED)
            val clock = mockk<ClockProvider>()
            every { clock.now() } returns date.plusYears(1)
            RoomExerciseCacheCleaner(database, database.exerciseCountDao(), database.exerciseRecordDao(), clock, dispatcher).cleanExpiredCache()
            reopen()
            assertThat(picture(id)).isEqualTo(photo)
            assertThat(File(requireNotNull(Uri.parse(photo.source).path)).exists()).isTrue()
            val explicitExit =
                ClearSessionUseCase(
                    mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                    RoomSessionDataCleaner(database, dispatcher, store),
                    mockk(relaxed = true),
                )
            explicitExit(SessionExitReason.USER_INITIATED)
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            assertThat(ownedFiles()).isEmpty()
            assertThat(selected.exists()).isTrue()
        }

    @Test
    fun `명시적 삭제는 저장의 commit이 끝나기 전에 준비 파일을 지우지 않는다`() =
        runTest {
            val selected = source()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val delayed = spyk(local)
            coEvery { delayed.createRecord(any(), any(), any()) } coAnswers {
                entered.complete(Unit)
                release.await()
                local.createRecord(firstArg(), secondArg(), thirdArg())
            }
            val saving = async { writer(delayed).createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(selected)))) }
            entered.await()
            val clearing = async { RoomSessionDataCleaner(database, dispatcher, store).clearAll() }
            val queued = async { repository.createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(selected)))) }
            testScheduler.runCurrent()
            assertThat(clearing.isCompleted).isFalse()
            assertThat(ownedFiles()).hasSize(1)
            release.complete(Unit)
            saving.await()
            clearing.await()
            assertThat(queued.await()).isInstanceOf(BaseResult.Error::class.java)
            assertThat(ownedFiles()).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            assertThat(selected.exists()).isTrue()
        }

    @Test
    fun `외부 경로나 다른 원본 경로는 소유 파일 삭제로 제거하지 않는다`() {
        val selected = source()
        store.deleteOwned(selected.absolutePath)
        store.deleteOwned(File(root, "exercise-images/../selected.png").absolutePath)
        assertThat(selected.exists()).isTrue()
    }

    @Test
    fun `새 기록의 DB 실패는 완성된 사진 파일까지 정리하고 어떤 투영도 남기지 않는다`() =
        runTest {
            val selected = source()
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_new_photo BEFORE INSERT ON exercise_image " +
                    "BEGIN SELECT RAISE(ABORT, 'NEW_PHOTO_FAILURE'); END",
            )
            val result = repository.createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(selected))))
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            assertThat(local.getListByDate(date)).isEmpty()
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            assertThat(ownedFiles()).isEmpty()
            assertThat(selected.exists()).isTrue()
        }

    @Test
    fun `복사 도중 취소는 이미 준비한 다른 사진도 정리하고 원래 저장된 사진은 유지한다`() =
        runTest {
            val selected = source()
            val id = create(selected)
            val original = picture(id)
            var opened = 0
            openSource = { sourceUri ->
                opened++
                if (opened == 2) throw CancellationException("사진 복사 취소")
                FileInputStream(File(requireNotNull(sourceUri.path)))
            }
            val failure = runCatching {
                repository.updateExerciseRecord(
                    ExerciseWriteInput(record("미확정"), listOf(uri(selected), uri(selected))), id,
                )
            }.exceptionOrNull()
            assertThat(failure).isInstanceOf(CancellationException::class.java)
            assertThat(picture(id)).isEqualTo(original)
            assertThat(local.getDetailByLocalId(id)?.title).isEqualTo("운동")
            assertThat(ownedFiles()).hasSize(1)
            assertThat(selected.exists()).isTrue()
        }

    @Test
    fun `영속 디렉터리를 만들 수 없으면 원본과 입력 외에 저장 성공이나 DB 행을 만들지 않는다`() =
        runTest {
            val selected = source()
            File(root, "exercise-images").writeText("디렉터리가 아닌 파일")
            val result = repository.createExerciseRecord(ExerciseWriteInput(record(), listOf(uri(selected))))
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            assertThat(database.exerciseSyncDao().getByMember(memberId)).isEmpty()
            assertThat(selected.exists()).isTrue()
        }

    @Test
    fun `서버 ID 연결 뒤에도 이미지 로컬 ID와 파일을 유지하고 재조회는 중복 사진을 만들지 않는다`() =
        runTest {
            val selected = source()
            val id = create(selected)
            val original = picture(id)
            val dao = database.exerciseImageDao()
            val metadata = dao.getByRecord(memberId, id).single()
            dao.save(metadata.copy(serverPictureId = 91L, serverUrl = "https://example.test/synced.png", state = ExerciseImageState.SYNCED))
            dao.refreshSynced(memberId, id, listOf(CachedPicture(91L, "https://example.test/new.png")))
            val records = database.exerciseRecordDao()
            records.upsertDetail(records.getDetailByLocalId(memberId, id)!!.copy(serverId = 7, syncState = SyncState.SYNCED))
            records.upsertListItems(listOf(records.getListItem(memberId, id)!!.copy(serverId = 7, syncState = SyncState.SYNCED)))
            val journal = database.exerciseSyncDao().get(memberId, id)!!
            database.exerciseSyncDao().deleteMatching(memberId, id, journal.operationId, journal.revision)
            val clock = mockk<ClockProvider>()
            every { clock.now() } returns date.plusYears(1)
            RoomExerciseCacheCleaner(database, database.exerciseCountDao(), records, clock, dispatcher).cleanExpiredCache()
            reopen()
            val linked = picture(id)
            assertThat(linked.localId).isEqualTo(original.localId)
            assertThat(linked.serverId).isEqualTo(91L)
            assertThat(linked.source).isEqualTo(original.source)
            assertThat(linked.syncStatus).isEqualTo(ExerciseImageSyncStatus.SYNCED)
            assertThat(database.exerciseImageDao().getByRecord(memberId, id).single().requestId).isEqualTo(metadata.requestId)
            assertThat(ownedFiles()).hasSize(1)
        }

    @Test
    fun `MIME이 없는 사진 선택 URI도 검증된 표시 이름의 확장자를 사용해 복사한다`() =
        runTest {
            val selected = source()
            val selectedUri = Uri.parse("content://photos/opaque-id")
            every { resolver.getType(selectedUri) } returns null
            every { resolver.query(selectedUri, any(), null, null, null) } returns
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                    addRow(arrayOf("selected.PNG"))
                }
            openSource = { FileInputStream(selected) }
            val result = repository.createExerciseRecord(ExerciseWriteInput(record(), listOf(selectedUri.toString()))) as BaseResult.Success
            val saved = picture(result.data.recordId)
            assertThat(File(requireNotNull(Uri.parse(saved.source).path)).extension).isEqualTo("png")
            assertThat(ownedFiles().single().readBytes()).isEqualTo(selected.readBytes())
        }

    private fun serverItem() =
        ExerciseListItem("7", "서버", "OTHER", date.atTime(9, 0), date.atTime(10, 0), listOf("https://example.test/9.png"))
}
