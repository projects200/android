package com.project200.data.impl

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.NetworkMonitor
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.ExerciseRecordRemoteDataSource
import com.project200.data.datasource.ExerciseSyncLocalDataSource
import com.project200.data.datasource.ExerciseSyncSnapshot
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.ExerciseIdDto
import com.project200.data.dto.ExercisePictureMappingDto
import com.project200.data.dto.ExercisePicturesDto
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.PatchExerciseRequestDto
import com.project200.data.dto.PostExerciseRequestDto
import com.project200.data.dto.PostExerciseResponseDTO
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.data.utils.ExerciseSyncAccountGuard
import com.project200.domain.manager.ExerciseSyncScheduler
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseSyncResult
import com.project200.domain.model.ExerciseWriteInput
import com.project200.undabang.oauth.AuthStateManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
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
import java.time.LocalDateTime
import java.util.UUID

/** 파일 DB와 실제 Repository를 재개방하여 화면 투영·집계·원본의 수명을 검증합니다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseSyncRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val root = File(context.filesDir, "sync-${UUID.randomUUID()}")
    private val fileContext = mockk<Context>()
    private val preferences = PreferenceManager(context)
    private val auth = mockk<AuthStateManager>()
    private val authState = mockk<AuthState>()
    private val apiService = mockk<ApiService>()
    private val remote = mockk<ExerciseRecordRemoteDataSource>()
    private val network = mockk<NetworkMonitor>()
    private val scheduler = mockk<ExerciseSyncScheduler>(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val databaseName = "exercise-sync-${UUID.randomUUID()}.db"
    private val member = "00000000-0000-0000-0000-000000000652"
    private val date = LocalDate.of(2026, 10, 20)
    private lateinit var db: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource
    private lateinit var syncLocal: ExerciseSyncLocalDataSource
    private lateinit var files: ExerciseImageFileStore
    private lateinit var repository: ExerciseRecordRepositoryImpl
    private var token: String? = null
    private var nextId = 100L
    private val serverBodies = mutableMapOf<Long, PostExerciseRequestDto>()
    private val versions = mutableMapOf<Long, Long>()
    private val deleted = mutableSetOf<Long>()
    private val createReceipts = mutableMapOf<String, Pair<PostExerciseRequestDto, PostExerciseResponseDTO>>()
    private val editReceipts = mutableMapOf<String, Pair<PatchExerciseRequestDto, ExerciseIdDto>>()
    private val photoReceipts = mutableMapOf<String, ExercisePicturesDto>()
    private val photoDeletes = mutableSetOf<String>()
    private val createRequests = mutableListOf<PostExerciseRequestDto>()
    private val edits = mutableListOf<PatchExerciseRequestDto>()
    private val uploads = mutableListOf<Upload>()
    private val deletionKeys = mutableListOf<String>()
    private var afterCreate: suspend () -> Unit = {}
    private var afterEdit: suspend () -> Unit = {}
    private var afterUpload: suspend () -> Unit = {}
    private var listVisible = true

    private data class Upload(val key: String, val imageId: String, val filename: String?, val mime: String?, val bytes: List<Byte>)

    @Before
    fun setUp() {
        root.mkdirs()
        every { fileContext.filesDir } returns root
        every { fileContext.contentResolver } returns context.contentResolver
        every { network.isCurrentlyConnected() } returns true
        every { auth.getCurrent() } returns authState
        every { authState.idToken } answers { token }
        preferences.saveMemberId(member, null)
        coEvery { apiService.getIsRegistered() } returns success(GetIsRegisteredData(member, true))
        token = jwt(member)
        open()
        stubServer()
    }

    private fun open() {
        db = Room.databaseBuilder(context, UndabangDatabase::class.java, databaseName)
            .addTypeConverter(UndabangTypeConverters(moshi)).allowMainThreadQueries().build()
        files = ExerciseImageFileStore(fileContext)
        local = ExerciseRecordLocalDataSource(db, db.exerciseCountDao(), db.exerciseRecordDao(), preferences)
        syncLocal = ExerciseSyncLocalDataSource(db, moshi)
        repository = ExerciseRecordRepositoryImpl(
            remote, local, network, dispatcher, files, syncLocal, ExerciseSyncAccountGuard(preferences, files, auth), scheduler,
            apiService, preferences, auth,
        )
    }

    private fun reopen() {
        db.close()
        open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(databaseName)
        root.deleteRecursively()
        preferences.clearMemberId()
    }

    private fun stubServer() {
        coEvery { remote.postExerciseRecord(any(), any()) } coAnswers {
            val request = firstArg<PostExerciseRequestDto>()
            createRequests.add(request)
            val key = requireNotNull(request.createRequestId)
            val receipt = createReceipts[key]
            val response = if (receipt != null) {
                check(receipt.first == request)
                receipt.second
            } else {
                val id = nextId++
                serverBodies[id] = request.copy(createRequestId = null)
                versions[id] = 0
                PostExerciseResponseDTO(id, 3, 0).also { createReceipts[key] = request to it }
            }
            afterCreate()
            success(response)
        }
        coEvery { remote.patchExerciseRecord(any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            val request = secondArg<PatchExerciseRequestDto>()
            edits.add(request)
            val receipt = editReceipts[request.editId]
            when {
                receipt != null -> {
                    check(receipt.first == request)
                    afterEdit()
                    success(receipt.second)
                }
                request.baseVersion != versions[id] -> BaseResponse(false, "EXERCISE_EDIT_CONFLICT", "")
                id in deleted -> BaseResponse(false, "EXERCISE_DELETED", "")
                else -> {
                    serverBodies[id] = PostExerciseRequestDto(
                        request.exerciseTitle, request.exercisePersonalType, request.exerciseLocation, request.exerciseDetail,
                        request.exerciseStartedAt, request.exerciseEndedAt,
                    )
                    versions[id] = versions.getValue(id) + 1
                    val response = ExerciseIdDto(id, versions.getValue(id))
                    editReceipts[requireNotNull(request.editId)] = request to response
                    afterEdit()
                    success(response)
                }
            }
        }
        coEvery { remote.getExerciseList(any(), any()) } coAnswers {
            val query = firstArg<LocalDate>()
            success(serverBodies.filter { (id, body) ->
                listVisible && id !in deleted && LocalDateTime.parse(body.exerciseStartedAt).toLocalDate() == query
            }.map { (id, body) -> listDto(id, body) })
        }
        coEvery { remote.getExerciseRecordDetail(any(), any()) } coAnswers {
            val id = firstArg<Long>()
            if (id in deleted) BaseResponse(false, "EXERCISE_DELETED", "") else {
                val body = serverBodies.getValue(id)
                success(GetExerciseRecordData(
                    requireNotNull(body.exerciseTitle), requireNotNull(body.exerciseDetail), requireNotNull(body.exercisePersonalType),
                    LocalDateTime.parse(body.exerciseStartedAt), LocalDateTime.parse(body.exerciseEndedAt),
                    requireNotNull(body.exerciseLocation), emptyList(), versions.getValue(id),
                ))
            }
        }
        coEvery { remote.deleteExerciseRecord(any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            if (deleted.add(id)) versions[id] = versions.getValue(id) + 1
            success<ExerciseIdDto?>(ExerciseIdDto(id, versions.getValue(id)))
        }
        coEvery { remote.postExerciseImages(any(), any(), any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            val part = secondArg<List<MultipartBody.Part>>().single()
            val key = requireNotNull(arg<String?>(2))
            val imageId = requireNotNull(arg<List<String>?>(3)).single()
            val buffer = Buffer()
            part.body.writeTo(buffer)
            uploads.add(Upload(key, imageId, part.headers?.get("Content-Disposition"), part.body.contentType()?.toString(), buffer.readByteArray().toList()))
            val receipt = photoReceipts[key] ?: run {
                check(id !in deleted)
                versions[id] = versions.getValue(id) + 1
                ExercisePicturesDto(id, versions.getValue(id), listOf(ExercisePictureMappingDto(imageId, 900, "https://example.test/photo.png")))
                    .also { photoReceipts[key] = it }
            }
            afterUpload()
            success(receipt)
        }
        coEvery { remote.deleteExerciseImages(any(), any(), any(), any()) } coAnswers {
            val id = firstArg<Long>()
            val key = requireNotNull(arg<String?>(2))
            deletionKeys.add(key)
            if (photoDeletes.add(key)) versions[id] = versions.getValue(id) + 1
            success<ExerciseIdDto?>(ExerciseIdDto(id, versions.getValue(id)))
        }
    }

    private fun record(title: String = "A", day: LocalDate = date) =
        ExerciseRecord(title, "본문", "OTHER", day.atTime(9, 0), day.atTime(10, 0), "장소", null)

    private suspend fun create(title: String = "A", day: LocalDate = date, image: File? = null): String =
        (repository.createExerciseRecord(ExerciseWriteInput(record(title, day), image?.let { listOf(Uri.fromFile(it).toString()) }.orEmpty()))
            as BaseResult.Success).data.recordId

    private suspend fun edit(id: String, title: String, day: LocalDate = date, removed: Set<String> = emptySet()) {
        assertThat(repository.updateExerciseRecord(ExerciseWriteInput(record(title, day), removedImageLocalIds = removed), id))
            .isInstanceOf(BaseResult.Success::class.java)
    }

    private suspend fun count(day: LocalDate = date): Int = local.getCountsByRange(day, day).singleOrNull()?.count ?: 0
    private suspend fun detail(id: String) = requireNotNull(local.getDetailByLocalId(id))
    private suspend fun journal(id: String) = requireNotNull(db.exerciseSyncDao().get(member, id))
    private suspend fun sync() = repository.syncPendingExercises(member)
    private fun image(): File = File(root, "selected.png").also { file ->
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun <T> success(value: T) = BaseResponse(true, "OK", "", value)
    private fun jwt(subject: String): String = "e30." + Base64.encodeToString("{\"sub\":\"$subject\"}".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) + ".signature"
    private fun listDto(id: Long, body: PostExerciseRequestDto) = GetExerciseRecordListDto(
        id, requireNotNull(body.exerciseTitle), requireNotNull(body.exercisePersonalType),
        LocalDateTime.parse(body.exerciseStartedAt), LocalDateTime.parse(body.exerciseEndedAt), emptyList(),
    )

    @Test
    fun `이관된 기존 로그인 회원은 서버가 확인한 subject를 연결한 뒤 동기화한다`() = runTest {
        val subject = "migrated-subject"
        token = jwt(subject)
        val id = create()

        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(preferences.getMemberSubject()).isEqualTo(subject)
        assertThat(local.resolveServerId(id, member)).isEqualTo(100)
        assertThat(detail(id).syncPending).isFalse()
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        coVerify(exactly = 1) { apiService.getIsRegistered() }
        coVerify(exactly = 1) { remote.postExerciseRecord(any(), match { it?.memberId == member }) }
    }

    @Test
    fun `등록 확인을 기다리는 동안에도 로컬 편집과 파일 잠금을 사용할 수 있다`() = runTest {
        token = jwt("migrated-subject")
        val id = create()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { apiService.getIsRegistered() } coAnswers {
            entered.complete(Unit)
            release.await()
            success(GetIsRegisteredData(member, true))
        }
        val running = async { sync() }
        entered.await()

        edit(id, "B")
        files.withLock { assertThat(preferences.getMemberSubject()).isNull() }
        release.complete(Unit)

        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(createRequests.single().exerciseTitle).isEqualTo("B")
    }

    @Test
    fun `서버 회원ID가 다르면 subject를 연결하지 않고 전송을 건너뛴다`() = runTest {
        token = jwt("migrated-subject")
        val id = create()
        coEvery { apiService.getIsRegistered() } returns success(GetIsRegisteredData("other-member", true))

        assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(preferences.getMemberSubject()).isNull()
        assertThat(journal(id).inFlightBody).isNull()
        coVerify { remote wasNot Called }
    }

    @Test
    fun `미가입 응답이나 실패 응답은 subject를 연결하거나 전송하지 않는다`() = runTest {
        token = jwt("migrated-subject")
        val id = create()
        val original = journal(id)
        val responses = listOf<BaseResponse<GetIsRegisteredData>>(success(GetIsRegisteredData(member, false)), BaseResponse(false, "TEMPORARY_ERROR", ""))
        for (response in responses) {
            coEvery { apiService.getIsRegistered() } returns response
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
            assertThat(preferences.getMemberSubject()).isNull()
            assertThat(journal(id)).isEqualTo(original)
        }
        coVerify { remote wasNot Called }
    }

    @Test
    fun `등록 확인 중 토큰 subject가 바뀌면 연결과 전송을 하지 않는다`() = runTest {
        token = jwt("migrated-subject")
        val id = create()
        coEvery { apiService.getIsRegistered() } coAnswers {
            token = jwt("changed-subject")
            success(GetIsRegisteredData(member, true))
        }

        assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(preferences.getMemberSubject()).isNull()
        assertThat(journal(id).inFlightBody).isNull()
        coVerify { remote wasNot Called }
    }

    @Test
    fun `등록 확인 전후 subject가 없으면 연결과 전송을 하지 않는다`() = runTest {
        val id = create()
        for (before in listOf(null, "migrated-subject")) {
            token = before?.let(::jwt)
            coEvery { apiService.getIsRegistered() } coAnswers {
                token = if (before == null) jwt("migrated-subject") else null
                success(GetIsRegisteredData(member, true))
            }
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
            assertThat(preferences.getMemberSubject()).isNull()
            assertThat(journal(id).inFlightBody).isNull()
        }
        coVerify { remote wasNot Called }
    }

    @Test
    fun `등록 확인 중 회원이나 세션이 바뀌면 이전 subject를 연결하지 않는다`() = runTest {
        token = jwt("migrated-subject")
        val id = create()
        coEvery { apiService.getIsRegistered() } coAnswers {
            preferences.saveMemberId("other-member", null)
            success(GetIsRegisteredData(member, true))
        }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(preferences.getMemberId()).isEqualTo("other-member")
        assertThat(preferences.getMemberSubject()).isNull()

        preferences.saveMemberId(member, null)
        coEvery { apiService.getIsRegistered() } coAnswers {
            preferences.invalidateExerciseSession()
            success(GetIsRegisteredData(member, true))
        }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(preferences.getMemberSubject()).isNull()
        assertThat(journal(id).inFlightBody).isNull()
        coVerify { remote wasNot Called }
    }

    @Test
    fun `등록 확인 네트워크 오류는 원문과 사진을 보존하고 재시도한다`() = runTest {
        token = jwt("migrated-subject")
        val id = create(image = image())
        val snapshot = requireNotNull(syncLocal.claim(member, id))
        val original = journal(id)
        val owned = File(requireNotNull(db.exerciseImageDao().getByRecord(member, id).single().localFilePath))
        coEvery { apiService.getIsRegistered() } throws IOException("등록 확인 응답 유실")

        assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
        assertThat(preferences.getMemberSubject()).isNull()
        assertThat(journal(id)).isEqualTo(original)
        assertThat(owned.exists()).isTrue()
        coVerify { remote wasNot Called }

        coEvery { apiService.getIsRegistered() } returns success(GetIsRegisteredData(member, true))
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(createRequests.single()).isEqualTo(snapshot.createRequest())
    }

    @Test
    fun `등록 확인 HTTP 오류는 인증 오류여도 snapshot을 버리지 않고 재시도한다`() = runTest {
        token = jwt("migrated-subject")
        val id = create()
        syncLocal.claim(member, id)
        val original = journal(id)
        for (status in listOf(401, 503)) {
            val body = okhttp3.ResponseBody.create(null, "")
            coEvery { apiService.getIsRegistered() } throws retrofit2.HttpException(retrofit2.Response.error<GetIsRegisteredData>(status, body))
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
            assertThat(preferences.getMemberSubject()).isNull()
            assertThat(journal(id)).isEqualTo(original)
        }
        coVerify { remote wasNot Called }
    }

    @Test
    fun `등록 확인 취소는 재시도 결과로 숨기지 않고 snapshot을 보존한다`() = runTest {
        token = jwt("migrated-subject")
        val id = create()
        syncLocal.claim(member, id)
        val original = journal(id)
        coEvery { apiService.getIsRegistered() } throws CancellationException("등록 확인 취소")

        try {
            sync()
            throw AssertionError("취소가 전파되지 않았습니다")
        } catch (expected: CancellationException) {
            assertThat(expected.message).isEqualTo("등록 확인 취소")
        }
        assertThat(preferences.getMemberSubject()).isNull()
        assertThat(journal(id)).isEqualTo(original)
        coVerify { remote wasNot Called }
    }

    @Test
    fun `생성 응답 유실 뒤 재개방해도 snapshot A와 같은 키를 재전송하고 최신 B를 후속 수정한다`() = runTest {
        val id = create()
        afterCreate = { throw IOException("응답 유실") }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
        val original = journal(id).inFlightBody
        edit(id, "B", date.plusDays(1))
        reopen()
        assertThat(journal(id).inFlightBody).isEqualTo(original)
        assertThat(detail(id).title).isEqualTo("B")
        afterCreate = {}
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(createRequests).hasSize(2)
        assertThat(createRequests[1]).isEqualTo(createRequests[0])
        assertThat(serverBodies).hasSize(1)
        assertThat(serverBodies.values.single().exerciseTitle).isEqualTo("B")
        assertThat(edits.single().baseVersion).isEqualTo(0)
        assertThat(local.resolveExistingLocalId("100", member)).isEqualTo(id)
        assertThat(detail(id).title).isEqualTo("B")
        assertThat(detail(id).syncPending).isFalse()
        assertThat(count()).isEqualTo(0)
        assertThat(count(date.plusDays(1))).isEqualTo(1)
    }

    @Test
    fun `전송 중 A의 늦은 완료는 B의 본문과 삭제 의도를 덮지 않는다`() = runTest {
        val id = create()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        afterCreate = { entered.complete(Unit); release.await() }
        val running = async { sync() }
        entered.await()
        edit(id, "B")
        repository.deleteExerciseRecord(id)
        assertThat(local.getListByDate(date)).isEmpty()
        release.complete(Unit)
        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(deleted).contains(100L)
        assertThat(db.exerciseSyncDao().get(member, id)).isNull()
        assertThat(db.exerciseRecordDao().getDetailByLocalId(member, id)).isNull()
        assertThat(count()).isEqualTo(0)
        assertThat(edits).isEmpty()
    }

    @Test
    fun `PATCH 응답 유실과 재편집은 재개방 뒤 원래 editId 본문 기준 version을 먼저 재생한다`() = runTest {
        val id = create()
        sync()
        edit(id, "B")
        afterEdit = { throw IOException("유실") }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
        val original = journal(id).inFlightBody
        edit(id, "C")
        reopen()
        afterEdit = {}
        assertThat(journal(id).inFlightBody).isEqualTo(original)
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(edits).hasSize(3)
        assertThat(edits[1]).isEqualTo(edits[0])
        assertThat(edits[2].editId).isNotEqualTo(edits[0].editId)
        assertThat(edits[2].baseVersion).isEqualTo(1)
        assertThat(detail(id).title).isEqualTo("C")
        assertThat(serverBodies.getValue(100).exerciseTitle).isEqualTo("C")
        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `취소된 생성의 snapshot은 DB 재개방 뒤에도 삭제 전에 같은 생성 영수증을 확인한다`() = runTest {
        val id = create()
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        afterCreate = { entered.complete(Unit); never.await() }
        val running = async { sync() }
        entered.await()
        running.cancelAndJoin()
        val original = journal(id).inFlightBody
        reopen()
        repository.deleteExerciseRecord(id)
        afterCreate = {}
        assertThat(journal(id).inFlightBody).isEqualTo(original)
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(createRequests[1]).isEqualTo(createRequests[0])
        assertThat(deleted).contains(100L)
        assertThat(local.getListByDate(date)).isEmpty()
    }

    @Test
    fun `서버 목록 membership 없는 성공 응답은 시작 날짜나 query 표식을 확정 날짜로 쓰지 않는다`() = runTest {
        val id = create()
        listVisible = false
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
        assertThat(journal(id).confirmedDate).isNull()
        assertThat(journal(id).inFlightBody).isNotNull()
        assertThat(detail(id).syncPending).isTrue()
        listVisible = true
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(db.exerciseRecordDao().getDetailByLocalId(member, id)?.confirmedDate).isEqualTo(date)
        assertThat(createRequests[1]).isEqualTo(createRequests[0])
    }

    @Test
    fun `상세의 시작 날짜는 두 번째 목록 조회 힌트일 뿐 실제 membership으로만 날짜를 확정한다`() = runTest {
        val id = create()
        afterCreate = {
            serverBodies[100] = serverBodies.getValue(100).copy(
                exerciseStartedAt = date.plusDays(1).atTime(9, 0).toString(), exerciseEndedAt = date.plusDays(1).atTime(10, 0).toString(),
            )
        }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(db.exerciseRecordDao().getDetailByLocalId(member, id)?.confirmedDate).isEqualTo(date.plusDays(1))
        coVerify { remote.getExerciseList(date.plusDays(1), any()) }
    }

    @Test
    fun `생성 반영을 포함한 서버 집계가 전송 중 도착해도 delta를 두 번 세지 않는다`() = runTest {
        local.replaceCountsByRange(date, date, listOf(ExerciseCount(date, 2)))
        val id = create()
        assertThat(count()).isEqualTo(3)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        afterCreate = { entered.complete(Unit); release.await() }
        val running = async { sync() }
        entered.await()
        local.replaceCountsByRange(date, date, listOf(ExerciseCount(date, 3)))
        assertThat(count()).isEqualTo(3)
        edit(id, "B", date.plusDays(1))
        local.replaceCountsByRange(date, date.plusDays(1), listOf(ExerciseCount(date, 3)))
        assertThat(count()).isEqualTo(2)
        assertThat(count(date.plusDays(1))).isEqualTo(1)
        release.complete(Unit)
        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(count()).isEqualTo(2)
        assertThat(count(date.plusDays(1))).isEqualTo(1)
        local.replaceCountsByRange(date, date.plusDays(1), listOf(ExerciseCount(date, 2), ExerciseCount(date.plusDays(1), 1)))
        assertThat(count()).isEqualTo(2)
        assertThat(count(date.plusDays(1))).isEqualTo(1)
    }

    @Test
    fun `날짜 이동과 삭제는 서버 기준과 남은 delta를 같은 트랜잭션에서 옮긴다`() = runTest {
        val id = create()
        sync()
        edit(id, "B", date.plusDays(1))
        afterEdit = { throw IOException("유실") }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
        local.replaceCountsByRange(date, date.plusDays(1), listOf(ExerciseCount(date.plusDays(1), 1)))
        assertThat(count()).isEqualTo(0)
        assertThat(count(date.plusDays(1))).isEqualTo(1)
        reopen()
        afterEdit = {}
        sync()
        repository.deleteExerciseRecord(id)
        assertThat(count(date.plusDays(1))).isEqualTo(0)
        local.replaceCountsByRange(date, date.plusDays(1), emptyList())
        assertThat(count(date.plusDays(1))).isEqualTo(0)
        sync()
        assertThat(count(date.plusDays(1))).isEqualTo(0)
    }

    @Test
    fun `ack 이전에 시작한 집계 응답은 mutation generation이 바뀌면 기준을 되돌리지 않는다`() = runTest {
        create()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { remote.getExerciseCountsByRange(date, date) } coAnswers {
            entered.complete(Unit)
            release.await()
            success(emptyList())
        }
        val reading = async { repository.getExerciseCountByRange(date, date, refresh = true) }
        entered.await()
        sync()
        release.complete(Unit)
        reading.await()
        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `사진 업로드 응답 유실 뒤 재개방하면 같은 이름 MIME 바이트 요청키와 localId를 재전송한다`() = runTest {
        val original = image()
        val id = create(image = original)
        afterUpload = { throw IOException("사진 응답 유실") }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
        val first = db.exerciseImageDao().getByRecord(member, id).single()
        val snapshot = journal(id).inFlightBody
        assertThat(first.state).isEqualTo(ExerciseImageState.ADD_PENDING)
        original.delete()
        reopen()
        afterUpload = {}
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        val mapped = db.exerciseImageDao().getByRecord(member, id).single()
        assertThat(uploads).hasSize(2)
        assertThat(uploads[1]).isEqualTo(uploads[0])
        assertThat(mapped.localId).isEqualTo(first.localId)
        assertThat(mapped.requestId).isEqualTo(first.requestId)
        assertThat(mapped.serverPictureId).isEqualTo(900)
        assertThat(mapped.localFilePath).isEqualTo(first.localFilePath)
        assertThat(File(requireNotNull(mapped.localFilePath)).readBytes().toList()).isEqualTo(uploads[0].bytes)
        assertThat(snapshot).isNotNull()
        assertThat(detail(id).syncPending).isFalse()
        assertThat(detail(id).pictures!!.single().localId).isEqualTo(first.localId)
    }

    @Test
    fun `업로드 중 사진 제거는 숨김을 유지하고 업로드와 별개인 고정 삭제키로 이어진다`() = runTest {
        val id = create(image = image())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        afterUpload = { entered.complete(Unit); release.await() }
        val running = async { sync() }
        entered.await()
        val image = db.exerciseImageDao().getByRecord(member, id).single()
        edit(id, "B", removed = setOf(image.localId))
        assertThat(detail(id).pictures).isEmpty()
        release.complete(Unit)
        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(deletionKeys.single()).isNotEqualTo(image.requestId)
        assertThat(db.exerciseImageDao().getByRecord(member, id)).isEmpty()
        assertThat(File(requireNotNull(image.localFilePath)).exists()).isFalse()
        assertThat(edits.single().baseVersion).isEqualTo(2)
        assertThat(detail(id).pictures).isEmpty()
        assertThat(detail(id).title).isEqualTo("B")
    }

    @Test
    fun `응답 불명 사진을 먼저 재생해야 최신 본문 PATCH가 자신의 사진 version과 충돌하지 않는다`() = runTest {
        val id = create(image = image())
        afterUpload = { throw IOException("유실") }
        sync()
        edit(id, "B")
        reopen()
        afterUpload = {}
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(edits.single().baseVersion).isEqualTo(1)
        assertThat(uploads[0]).isEqualTo(uploads[1])
        assertThat(detail(id).title).isEqualTo("B")
    }

    @Test
    fun `사진 응답 version이 자신의 한 번 증가보다 크면 매핑만 보존하고 본문 덮어쓰기를 중단한다`() = runTest {
        val id = create(image = image())
        coEvery { remote.postExerciseImages(any(), any(), any(), any(), any()) } coAnswers {
            val imageId = requireNotNull(arg<List<String>?>(3)).single()
            success(ExercisePicturesDto(100, 4, listOf(ExercisePictureMappingDto(imageId, 900, "https://example.test/photo.png"))))
        }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(journal(id).serverVersion).isEqualTo(0)
        assertThat(journal(id).lastError).isEqualTo(ExerciseSyncError.CONFLICT)
        assertThat(db.exerciseImageDao().getByRecord(member, id).single().serverPictureId).isEqualTo(900)
        edit(id, "B")
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(edits).isEmpty()
        assertThat(detail(id).title).isEqualTo("B")
    }

    @Test
    fun `전체 삭제는 업로드 응답이 늦어도 사진과 본문을 부활시키지 않는다`() = runTest {
        val id = create(image = image())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        afterUpload = { entered.complete(Unit); release.await() }
        val running = async { sync() }
        entered.await()
        val owned = File(requireNotNull(db.exerciseImageDao().getByRecord(member, id).single().localFilePath))
        repository.deleteExerciseRecord(id)
        release.complete(Unit)
        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(deleted).contains(100L)
        assertThat(local.getDetailByLocalId(id)).isNull()
        assertThat(db.exerciseImageDao().getByRecord(member, id)).isEmpty()
        assertThat(owned.exists()).isFalse()
        assertThat(uploads).hasSize(1)
    }

    @Test
    fun `한 번도 claim하지 않은 사진과 본문 삭제는 서버 업로드를 만들지 않는다`() = runTest {
        val id = create(image = image())
        repository.deleteExerciseRecord(id)
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(uploads).isEmpty()
        assertThat(createRequests).isEmpty()
        assertThat(File(root, "exercise-images").listFiles()).isEmpty()
    }

    @Test
    fun `응답 후 계정이나 세션이 바뀌면 같은 회원ID여도 이전 작업을 apply하지 않는다`() = runTest {
        val id = create()
        afterCreate = { preferences.invalidateExerciseSession() }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(journal(id).inFlightBody).isNotNull()
        assertThat(local.resolveServerId(id, member)).isNull()
        afterCreate = {}
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(local.resolveServerId(id, member)).isEqualTo(100)
        val second = create("second")
        afterCreate = { token = jwt("other-account") }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(journal(second).serverId).isNull()
    }

    @Test
    fun `다른 작업ID나 revision 완료는 기존 snapshot과 투영에 영향을 주지 않는다`() = runTest {
        val id = create()
        val snapshot = requireNotNull(syncLocal.claim(member, id))
        assertThat(syncLocal.applyBody(snapshot.copy(operationId = UUID.randomUUID().toString()), 100, 0, date)).isFalse()
        assertThat(syncLocal.applyBody(snapshot.copy(revision = 99), 100, 0, date)).isFalse()
        assertThat(syncLocal.applyBody(snapshot.copy(memberId = "other"), 100, 0, date)).isFalse()
        assertThat(journal(id).inFlightRequestId).isEqualTo(snapshot.requestId)
        assertThat(local.resolveServerId(id, member)).isNull()
        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `요청키 재사용 충돌 삭제된 원격과 계약 오류는 원문 사진파일을 복구 상태로 남긴다`() = runTest {
        for (code in listOf("EXERCISE_REQUEST_ID_REUSED", "EXERCISE_EDIT_CONFLICT", "EXERCISE_DELETED", "INVALID_INPUT_VALUE")) {
            val id = create(code, image = image())
            coEvery { remote.postExerciseRecord(match { it.exerciseTitle == code }, any()) } returns BaseResponse(false, code, "")
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
            val pending = journal(id)
            assertThat(pending.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(pending.inFlightBody).isNotNull()
            assertThat(detail(id).title).isEqualTo(code)
            assertThat(File(requireNotNull(db.exerciseImageDao().getByRecord(member, id).single().localFilePath)).exists()).isTrue()
        }
        val invalid = create("invalid")
        coEvery { remote.postExerciseRecord(match { it.exerciseTitle == "invalid" }, any()) } returns success(PostExerciseResponseDTO(0, 3, null))
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(journal(invalid).lastError).isEqualTo(ExerciseSyncError.CONTRACT_ERROR)
        assertThat(journal(invalid).serverId).isNull()
    }

    @Test
    fun `401과 403은 대기 원문을 보존하고 같은 계정 로그인 후 재예약으로 이어진다`() = runTest {
        val id = create()
        for (code in listOf("401", "403")) {
            coEvery { remote.postExerciseRecord(any(), any()) } returns BaseResponse(false, code, "")
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
            assertThat(journal(id).recoveryState).isEqualTo(ExerciseRecoveryState.RETRYABLE)
            assertThat(journal(id).lastError).isEqualTo(ExerciseSyncError.AUTHENTICATION_REQUIRED)
        }
        val snapshot = journal(id).inFlightBody
        reopen()
        stubServer()
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(snapshot).isNotNull()
        assertThat(detail(id).syncPending).isFalse()
    }

    @Test
    fun `429와 503 네트워크 오류 및 반복 상한 이후 재진입에도 요청 원문과 파일이 남는다`() = runTest {
        val id = create(image = image())
        var snapshot: String? = null
        for (code in listOf("429", "503", "NETWORK_ERROR", "503", "503", "503")) {
            coEvery { remote.postExerciseRecord(any(), any()) } returns BaseResponse(false, code, "")
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
            val current = journal(id)
            if (snapshot == null) snapshot = current.inFlightBody else assertThat(current.inFlightBody).isEqualTo(snapshot)
            assertThat(File(requireNotNull(db.exerciseImageDao().getByRecord(member, id).single().localFilePath)).exists()).isTrue()
        }
        reopen()
        stubServer()
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(detail(id).syncPending).isFalse()
    }

    @Test
    fun `영구 오류인 한 행 때문에 다른 전송 가능한 기록까지 완료나 실패로 버리지 않는다`() = runTest {
        val bad = create("bad")
        val good = create("good")
        coEvery { remote.postExerciseRecord(match { it.exerciseTitle == "bad" }, any()) } returns BaseResponse(false, "EXERCISE_REQUEST_ID_REUSED", "")
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(journal(bad).inFlightBody).isNotNull()
        assertThat(db.exerciseSyncDao().get(member, good)).isNull()
        assertThat(detail(good).syncPending).isFalse()
    }

    @Test
    fun `일반 경로 404는 삭제 성공이 아니고 이름 붙은 운동 부재만 삭제를 확정한다`() = runTest {
        val id = create()
        sync()
        repository.deleteExerciseRecord(id)
        coEvery { remote.deleteExerciseRecord(any(), any(), any()) } returns BaseResponse(false, "404", "")
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(journal(id).operation).isEqualTo(SyncState.DELETE_PENDING)
        val retry = journal(id).copy(recoveryState = ExerciseRecoveryState.RETRYABLE)
        db.exerciseSyncDao().saveSyncState(retry)
        coEvery { remote.deleteExerciseRecord(any(), any(), any()) } returns BaseResponse(false, "EXERCISE_NOT_FOUND", "")
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(db.exerciseSyncDao().get(member, id)).isNull()
        assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `예약 실패는 이미 commit한 사용자 저장을 실패로 바꾸지 않는다`() = runTest {
        every { scheduler.schedule() } throws IllegalStateException("WorkManager")
        val id = create()
        assertThat(detail(id).title).isEqualTo("A")
        assertThat(journal(id).operation).isEqualTo(SyncState.CREATE_PENDING)
        verify { scheduler.schedule() }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
    }

    @Test
    fun `사진 삭제 응답 유실은 재개방 뒤에도 업로드키와 다른 동일 삭제키를 재생한다`() = runTest {
        val id = create(image = image())
        sync()
        val image = db.exerciseImageDao().getByRecord(member, id).single()
        edit(id, "B", removed = setOf(image.localId))
        val keys = mutableListOf<String>()
        var loseResponse = true
        coEvery { remote.deleteExerciseImages(any(), any(), any(), any()) } coAnswers {
            keys.add(requireNotNull(arg<String?>(2)))
            if (loseResponse) throw IOException("삭제 응답 유실")
            success<ExerciseIdDto?>(ExerciseIdDto(100, 2))
        }
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
        val original = journal(id).inFlightBody
        assertThat(detail(id).pictures).isEmpty()
        assertThat(File(requireNotNull(image.localFilePath)).exists()).isTrue()
        reopen()
        loseResponse = false
        versions[100] = 2
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(keys).hasSize(2)
        assertThat(keys[0]).isEqualTo(keys[1])
        assertThat(keys[0]).isNotEqualTo(image.requestId)
        assertThat(original).isNotNull()
        assertThat(File(requireNotNull(image.localFilePath)).exists()).isFalse()
        assertThat(detail(id).pictures).isEmpty()
    }

    @Test
    fun `잘못된 사진 매핑과 영구 사진 오류는 pending과 원본 요청 바이트를 보존한다`() = runTest {
        for (malformed in listOf(false, true)) {
            val id = create(if (malformed) "mapping" else "count", image = image())
            coEvery { remote.postExerciseImages(any(), any(), any(), any(), any()) } coAnswers {
                if (malformed) {
                    success(ExercisePicturesDto(firstArg(), 1, listOf(ExercisePictureMappingDto(UUID.randomUUID().toString(), 900, "https://example.test/x.png"))))
                } else {
                    BaseResponse(false, "EXERCISE_PICTURE_COUNT_EXCEEDED", "")
                }
            }
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
            val image = db.exerciseImageDao().getByRecord(member, id).single()
            val snapshot = journal(id).inFlightBody
            assertThat(image.state).isEqualTo(ExerciseImageState.ADD_PENDING)
            assertThat(image.serverPictureId).isNull()
            assertThat(File(requireNotNull(image.localFilePath)).exists()).isTrue()
            assertThat(detail(id).syncPending).isTrue()
            assertThat(local.getListByDate(date).find { it.recordId == id }?.syncPending).isTrue()
            assertThat(detail(id).pictures!!.single().syncStatus).isEqualTo(com.project200.domain.model.ExerciseImageSyncStatus.FAILED)
            reopen()
            assertThat(journal(id).inFlightBody).isEqualTo(snapshot)
        }
    }

    @Test
    fun `불확실한 사진 실패보다 나중에 요청한 전체 삭제는 그 사진을 올리려고 재시도하지 않는다`() = runTest {
        val id = create(image = image())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { remote.postExerciseImages(any(), any(), any(), any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            BaseResponse(false, "EXERCISE_PICTURE_COUNT_EXCEEDED", "")
        }
        val running = async { sync() }
        entered.await()
        repository.deleteExerciseRecord(id)
        release.complete(Unit)
        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(deleted).contains(100L)
        assertThat(db.exerciseImageDao().getByRecord(member, id)).isEmpty()
        assertThat(local.getDetailByLocalId(id)).isNull()
        coVerify(exactly = 1) { remote.postExerciseImages(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `미확정 본문 수정 중 삭제하면 늦은 수정 응답 후에도 삭제가 최종 상태다`() = runTest {
        val id = create()
        sync()
        edit(id, "B")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        afterEdit = { entered.complete(Unit); release.await() }
        val running = async { sync() }
        entered.await()
        repository.deleteExerciseRecord(id)
        release.complete(Unit)
        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(deleted).contains(100L)
        assertThat(local.getDetailByLocalId(id)).isNull()
        assertThat(count()).isEqualTo(0)
    }

    @Test
    fun `구 캐시의 알 수 없는 편집 version은 새 GET version으로 채우지 않는다`() = runTest {
        val id = create()
        sync()
        val detail = requireNotNull(db.exerciseRecordDao().getDetailByLocalId(member, id))
        val item = requireNotNull(db.exerciseRecordDao().getListItem(member, id))
        db.exerciseRecordDao().upsertDetail(detail.copy(serverVersion = null))
        db.exerciseRecordDao().upsertListItems(listOf(item.copy(serverVersion = null)))
        edit(id, "local")
        local.saveSyncedDetail(100, record("remote").copy(serverVersion = 9), member, id)
        assertThat(journal(id).serverVersion).isNull()
        assertThat(journal(id).recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(edits).isEmpty()
        assertThat(this@ExerciseSyncRepositoryTest.detail(id).title).isEqualTo("local")
    }

    @Test
    fun `불완전한 snapshot은 전송하거나 덮어쓰지 않고 원문 그대로 복구 상태로 둔다`() = runTest {
        val id = create()
        val snapshot = requireNotNull(syncLocal.claim(member, id))
        val stored = journal(id)
        db.exerciseSyncDao().saveSyncState(stored.copy(inFlightBody = "{broken"))
        reopen()
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(journal(id).inFlightBody).isEqualTo("{broken")
        assertThat(journal(id).inFlightRequestId).isEqualTo(snapshot.requestId)
        assertThat(createRequests).isEmpty()
        assertThat(detail(id).title).isEqualTo("A")
    }

    @Test
    fun `실제 HttpException 503과 429의 원인은 분류에 남고 요청은 재시도 가능하다`() = runTest {
        val id = create()
        for (status in listOf(429, 503)) {
            val body = okhttp3.ResponseBody.create(null, "{\"succeed\":false,\"code\":\"TEMPORARY_ERROR\"}")
            coEvery { remote.postExerciseRecord(any(), any()) } throws retrofit2.HttpException(retrofit2.Response.error<Any>(status, body))
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Retry)
            assertThat(journal(id).recoveryState).isEqualTo(ExerciseRecoveryState.RETRYABLE)
            assertThat(journal(id).inFlightBody).isNotNull()
        }
    }

    @Test
    fun `명시적 세션 삭제 뒤 늦은 사진 응답은 새 DB와 파일을 되살리지 않는다`() = runTest {
        val id = create(image = image())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        afterUpload = { entered.complete(Unit); release.await() }
        val running = async { sync() }
        entered.await()
        val snapshot = journal(id).inFlightBody
        RoomSessionDataCleaner(db, dispatcher, files).clearAll()
        release.complete(Unit)
        assertThat(running.await()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(snapshot).isNotNull()
        assertThat(db.exerciseSyncDao().getByMember(member)).isEmpty()
        assertThat(db.exerciseImageDao().getByRecord(member, id)).isEmpty()
        assertThat(File(root, "exercise-images").listFiles()).isEmpty()
    }

    @Test
    fun `사진 취소와 강제 이탈은 원본 snapshot과 파일을 보존하고 같은 계정에서 재개한다`() = runTest {
        val id = create(image = image())
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        afterUpload = { entered.complete(Unit); never.await() }
        val running = async { sync() }
        entered.await()
        running.cancelAndJoin()
        val snapshot = journal(id).inFlightBody
        val image = db.exerciseImageDao().getByRecord(member, id).single()
        every { auth.clearAuthState() } answers { token = null }
        val authRepository = AuthRepositoryImpl(mockk(), preferences, auth, mockk(), dispatcher, files)
        authRepository.clearTokens()
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Skipped)
        assertThat(journal(id).inFlightBody).isEqualTo(snapshot)
        assertThat(File(requireNotNull(image.localFilePath)).exists()).isTrue()
        token = jwt(member)
        reopen()
        afterUpload = {}
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(uploads[0]).isEqualTo(uploads[1])
        assertThat(detail(id).pictures!!.single().localId).isEqualTo(image.localId)
    }

    @Test
    fun `snapshot의 불변 본문과 식별자는 JSON 재개방 뒤에도 동일하다`() = runTest {
        val id = create()
        val claimed = requireNotNull(syncLocal.claim(member, id))
        val json = requireNotNull(journal(id).inFlightBody)
        reopen()
        val restored = requireNotNull(moshi.adapter(ExerciseSyncSnapshot::class.java).fromJson(requireNotNull(journal(id).inFlightBody)))
        assertThat(restored).isEqualTo(claimed)
        assertThat(journal(id).inFlightBody).isEqualTo(json)
        assertThat(restored.createRequest().createRequestId).isEqualTo(journal(id).createRequestId)
        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `본문 ack보다 늦게 도착한 옛 목록과 상세 응답은 최신 로컬 필드를 되돌리지 않는다`() = runTest {
        val id = create()
        sync()
        for (list in listOf(true, false)) {
            val before = serverBodies.getValue(100)
            edit(id, if (list) "B" else "C")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            if (list) {
                coEvery { remote.getExerciseList(date) } coAnswers {
                    entered.complete(Unit)
                    release.await()
                    success(listOf(listDto(100, before)))
                }
            } else {
                coEvery { remote.getExerciseRecordDetail(100) } coAnswers {
                    entered.complete(Unit)
                    release.await()
                    success(GetExerciseRecordData(
                        requireNotNull(before.exerciseTitle), requireNotNull(before.exerciseDetail), requireNotNull(before.exercisePersonalType),
                        LocalDateTime.parse(before.exerciseStartedAt), LocalDateTime.parse(before.exerciseEndedAt),
                        requireNotNull(before.exerciseLocation), emptyList(), 0,
                    ))
                }
            }
            val reading = async {
                if (list) repository.getExerciseRecordList(date, refresh = true) else repository.getExerciseDetail(id, refresh = true)
            }
            entered.await()
            assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
            release.complete(Unit)
            reading.await()
            assertThat(detail(id).title).isEqualTo(if (list) "B" else "C")
            assertThat(local.getListByDate(date).single().title).isEqualTo(if (list) "B" else "C")
        }
    }

    @Test
    fun `서버 ID가 붙은 ADD_PENDING 사진은 삭제 의도로 추정하지 않는다`() = runTest {
        val id = create(image = image())
        sync()
        val image = db.exerciseImageDao().getByRecord(member, id).single()
        db.exerciseImageDao().save(image.copy(state = ExerciseImageState.ADD_PENDING))
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Failure)
        assertThat(journal(id).recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
        assertThat(db.exerciseImageDao().getByRecord(member, id).single().serverPictureId).isEqualTo(image.serverPictureId)
        coVerify(exactly = 0) { remote.deleteExerciseImages(any(), any(), any(), any()) }
        assertThat(File(requireNotNull(image.localFilePath)).exists()).isTrue()
    }

    @Test
    fun `마지막 사진 삭제 완료 뒤 구 목록 썸네일로 사진을 다시 표시하지 않는다`() = runTest {
        val id = create(image = image())
        sync()
        val item = requireNotNull(db.exerciseRecordDao().getListItem(member, id))
        db.exerciseRecordDao().upsertListItems(listOf(item.copy(thumbnailUrls = listOf("https://example.test/old.png"))))
        val image = db.exerciseImageDao().getByRecord(member, id).single()
        edit(id, "B", removed = setOf(image.localId))
        assertThat(sync()).isEqualTo(ExerciseSyncResult.Success)
        assertThat(db.exerciseImageDao().getByRecord(member, id)).isEmpty()
        assertThat(local.getListByDate(date).single().imageUrl).isEmpty()
        assertThat(detail(id).pictures).isEmpty()
    }
}
