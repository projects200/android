package com.project200.data.impl

import android.content.Context
import android.util.Base64
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.common.utils.ClockProvider
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.PreparedExerciseImage
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.PostSignUpData
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.TimerSnapshotEntity
import com.project200.data.utils.ExerciseSyncAccountGuard
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.model.SessionExitReason
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.undabang.oauth.AuthStateManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryImplTest {
    private val context = RuntimeEnvironment.getApplication()
    private val api = mockk<ApiService>()
    private val auth = mockk<AuthStateManager>()
    private val state = mockk<AuthState>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val root = File(context.filesDir, "adoption-tests")
    private lateinit var preferences: PreferenceManager
    private lateinit var files: ExerciseImageFileStore
    private lateinit var database: UndabangDatabase
    private lateinit var cleaner: RoomSessionDataCleaner
    private lateinit var repository: AuthRepositoryImpl
    private lateinit var local: ExerciseRecordLocalDataSource
    private var token: String? = null
    private val date = LocalDate.of(2025, 1, 1)
    private val birth = LocalDate.of(2000, 1, 1)

    @Before
    fun setUp() {
        context.getSharedPreferences("undabangPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        preferences = PreferenceManager(context)
        preferences.saveMemberId("member-a", "subject-a")
        token = jwt("subject-b")
        every { auth.getCurrent() } returns state
        every { state.idToken } answers { token }
        every { auth.clearAuthState() } answers { token = null }
        root.mkdirs()
        val fileContext = mockk<Context>()
        every { fileContext.filesDir } returns root
        files = ExerciseImageFileStore(fileContext)
        database = Room.inMemoryDatabaseBuilder(context, UndabangDatabase::class.java)
            .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .allowMainThreadQueries()
            .build()
        cleaner = spyk(RoomSessionDataCleaner(database, dispatcher, files))
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
        repository = newRepository()
    }

    private fun newRepository(prefs: PreferenceManager = preferences) =
        AuthRepositoryImpl(api, prefs, auth, cleaner, dispatcher, files)

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    private fun jwt(subject: String) = "e30." + Base64.encodeToString(
        "{\"sub\":\"$subject\"}".toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
    ) + ".signature"

    private fun registered(member: String = "member-b", registered: Boolean = true) {
        coEvery { api.getIsRegistered() } returns response(GetIsRegisteredData(member, registered))
    }

    private fun <T> response(data: T) = BaseResponse(true, "SUCCESS", "", data)

    private fun registrationFailure(code: String? = null): retrofit2.HttpException {
        val body = okhttp3.ResponseBody.create(
            null,
            if (code == null) "" else """{"succeed":false,"code":"$code","message":"registration rejected"}""",
        )
        return retrofit2.HttpException(retrofit2.Response.error<GetIsRegisteredData>(401, body))
    }

    private suspend fun seedPending(): String {
        File(root, "exercise-images").mkdirs()
        val owned = File(root, "exercise-images/owned.png").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val id = local.createRecord(
            ExerciseWriteInput(
                ExerciseRecord("미전송", "본문", "OTHER", date.atTime(9, 0), date.atTime(10, 0), "장소", null),
                addedImageUris = listOf("file://${owned.absolutePath}"),
            ),
            preparedImages = listOf(PreparedExerciseImage("photo-id", "photo-request-key", owned.absolutePath)),
        ).recordId
        val photo = database.exerciseImageDao().getByRecord("member-a", id).single()
        database.exerciseImageDao().save(photo.copy(state = ExerciseImageState.ADD_PENDING))
        val journal = database.exerciseSyncDao().get("member-a", id)!!
        database.exerciseSyncDao().claim(
            "member-a", id, journal.revision!!, journal.operationId!!, journal.createRequestId!!, "immutable-body", null,
        )
        File(root, "original.png").writeBytes(byteArrayOf(4, 5, 6))
        return id
    }

    private suspend fun confirmation(): String =
        (repository.checkIsRegistered() as RegistrationStatus.ConfirmationRequired).confirmationId

    @Test
    fun `다른 회원은 승인 전 DB와 사진을 보존하고 일반 조회와 Worker를 막는다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)
        // subject만 같아도 서버 회원 경계가 다르면 승인 전에는 전송할 수 없습니다.
        preferences.saveMemberId("member-a", "subject-b")
        val guard = ExerciseSyncAccountGuard(preferences, files, auth)
        assertThat(guard.capture("member-a")).isNotNull()
        registered()
        assertThat(repository.checkIsRegistered()).isInstanceOf(RegistrationStatus.ConfirmationRequired::class.java)
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(repository.getMemberId()).isNull()
        assertThat(guard.capture("member-a")).isNull()
        assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    @Test
    fun `승인은 서버를 다시 확인한 뒤 DB와 소유 파일만 지우고 대상 회원을 확정한다`() = runTest {
        seedPending()
        registered()
        val approval = confirmation()
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Registered)
        assertThat(preferences.getMemberId()).isEqualTo("member-b")
        assertThat(preferences.getMemberSubject()).isEqualTo("subject-b")
        assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
        assertThat(File(root, "exercise-images/owned.png").exists()).isFalse()
        assertThat(File(root, "original.png").exists()).isTrue()
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        coVerify(exactly = 2) { api.getIsRegistered() }
    }

    @Test
    fun `취소는 강제 이탈 정책으로 이전 원문과 파일을 보존하고 원래 회원으로 재개한다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)!!.copy(
            recoveryState = ExerciseRecoveryState.RECOVERY_REQUIRED,
            lastError = ExerciseSyncError.CONFLICT,
        )
        database.exerciseSyncDao().saveSyncState(before)
        registered()
        val approval = confirmation()
        ClearSessionUseCase(repository, mockk(relaxed = true), mockk(relaxed = true), cleaner, mockk(relaxed = true))(
            SessionExitReason.FORCED,
        )
        assertThat(token).isNull()
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        token = jwt("subject-a")
        registered("member-a")
        assertThat(repository.checkIsRegistered()).isEqualTo(RegistrationStatus.Registered)
        assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        assertThat(ExerciseSyncAccountGuard(preferences, files, auth).capture("member-a")).isNotNull()
    }

    @Test
    fun `같은 회원 재인증과 캐시 정리는 삭제 의도와 전송 원문 및 사진 요청 키를 보존한다`() = runTest {
        val id = seedPending()
        local.deleteRecord(id)
        val before = database.exerciseSyncDao().get("member-a", id)
        val photos = database.exerciseImageDao().getByRecord("member-a", id)
        token = jwt("subject-a")
        registered("member-a")
        assertThat(repository.checkIsRegistered()).isEqualTo(RegistrationStatus.Registered)
        val clock = mockk<ClockProvider>()
        every { clock.now() } returns date.plusYears(2)
        RoomExerciseCacheCleaner(database, database.exerciseCountDao(), database.exerciseRecordDao(), clock, dispatcher).cleanExpiredCache()
        assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
        assertThat(before?.inFlightBody).isEqualTo("immutable-body")
        assertThat(database.exerciseImageDao().getByRecord("member-a", id)).isEqualTo(photos)
        assertThat(photos.single().requestId).isEqualTo("photo-request-key")
        assertThat(photos.single().state).isEqualTo(ExerciseImageState.DELETE_PENDING)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    @Test
    fun `승인 후 서버 회원이 달라지거나 조회가 실패하면 기존 데이터를 삭제하지 않는다`() = runTest {
        val id = seedPending()
        registered()
        val approval = confirmation()
        registered("member-c")
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        coEvery { api.getIsRegistered() } throws IOException("offline")
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    @Test
    fun `서버 대기 중 로그인 subject 또는 세션이 바뀌면 승인은 삭제할 수 없다`() = runTest {
        val id = seedPending()
        registered()
        val approval = confirmation()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { api.getIsRegistered() } coAnswers {
            entered.complete(Unit)
            release.await()
            response(GetIsRegisteredData("member-b", true))
        }
        val confirming = async { repository.confirmAccountAdoption(approval) }
        entered.await()
        repository.beginLogin()
        token = jwt("subject-c")
        release.complete(Unit)
        assertThat(confirming.await()).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
    }

    @Test
    fun `사진 잠금 대기 중 승인이 오래되면 정리 직전 재검사하여 파일과 DB를 보존한다`() = runTest {
        val id = seedPending()
        registered()
        val approval = confirmation()
        val requested = CompletableDeferred<Unit>()
        val networkRelease = CompletableDeferred<Unit>()
        coEvery { api.getIsRegistered() } coAnswers {
            requested.complete(Unit)
            networkRelease.await()
            response(GetIsRegisteredData("member-b", true))
        }
        val confirming = async { repository.confirmAccountAdoption(approval) }
        requested.await()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holding = async { files.withLock { entered.complete(Unit); release.await() } }
        entered.await()
        networkRelease.complete(Unit)
        testScheduler.runCurrent()
        preferences.invalidateExerciseSession()
        release.complete(Unit)
        holding.await()
        assertThat(confirming.await()).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    @Test
    fun `정리 실패는 대상 회원을 저장하거나 로그인 게이트를 열지 않는다`() = runTest {
        seedPending()
        registered()
        val approval = confirmation()
        coEvery { cleaner.clearForAccountAdoption(any(), any(), any()) } throws IllegalStateException("storage")
        assertThat(runCatching { repository.confirmAccountAdoption(approval) }.exceptionOrNull()).isNotNull()
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(repository.getMemberId()).isNull()
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    @Test
    fun `미가입 계정은 회원가입 전에 승인해야 하며 직접 회원가입으로 우회할 수 없다`() = runTest {
        val id = seedPending()
        registered(registered = false)
        assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
        val approval = confirmation()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        coVerify(exactly = 0) { api.postSignUp(any()) }
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Unregistered)
        assertThat(preferences.getRetainedMemberId()).isNull()
        assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
        stubSignup()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isEqualTo(BaseResult.Success(Unit))
        assertThat(preferences.getMemberId()).isEqualTo("member-new")
        assertThat(preferences.getMemberSubject()).isEqualTo("subject-b")
    }

    @Test
    fun `서버 AUTHENTICATION_FAILED는 미가입 신호지만 보존 계정 삭제와 가입은 승인 후에만 허용한다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)
        coEvery { api.getIsRegistered() } throws registrationFailure("AUTHENTICATION_FAILED")
        val approval = confirmation()
        assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(repository.getMemberId()).isNull()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
        coVerify(exactly = 0) { api.postSignUp(any()) }

        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Unregistered)
        assertThat(preferences.getRetainedMemberId()).isNull()
        assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
        assertThat(File(root, "exercise-images/owned.png").exists()).isFalse()
        assertThat(File(root, "original.png").exists()).isTrue()
        stubSignup()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isEqualTo(BaseResult.Success(Unit))
        assertThat(preferences.getMemberId()).isEqualTo("member-new")
        assertThat(preferences.getMemberSubject()).isEqualTo("subject-b")
    }

    @Test
    fun `보존 회원이 없는 새 사용자는 정확한 서버 미가입 신호로 가입할 수 있다`() = runTest {
        preferences.clearMemberId()
        coEvery { api.getIsRegistered() } throws registrationFailure("AUTHENTICATION_FAILED")
        assertThat(repository.checkIsRegistered()).isEqualTo(RegistrationStatus.Unregistered)
        stubSignup()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isEqualTo(BaseResult.Success(Unit))
        assertThat(preferences.getMemberId()).isEqualTo("member-new")
    }

    @Test
    fun `일반 401과 헤더 오류 및 네트워크 실패는 미가입이나 데이터 삭제로 해석하지 않는다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)
        for (failure in listOf(
            registrationFailure(), registrationFailure("USER_ID_HEADER_MISSING"),
            registrationFailure("INVALID_USER_ID_FORMAT"), IOException("offline"),
        )) {
            coEvery { api.getIsRegistered() } throws failure
            assertThat(repository.checkIsRegistered()).isEqualTo(RegistrationStatus.Indeterminate)
            assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
            assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
            assertThat(repository.getMemberId()).isNull()
            assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
            assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        }
        coVerify(exactly = 0) { api.postSignUp(any()) }
    }

    @Test
    fun `미가입 승인 재확인 중 일반 인증 오류는 원문과 사진을 보존하고 가입을 허용하지 않는다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)
        coEvery { api.getIsRegistered() } throws registrationFailure("AUTHENTICATION_FAILED")
        val approval = confirmation()
        for (failure in listOf(
            registrationFailure(), registrationFailure("USER_ID_HEADER_MISSING"),
            registrationFailure("INVALID_USER_ID_FORMAT"), IOException("offline"),
        )) {
            coEvery { api.getIsRegistered() } throws failure
            assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
            assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
            assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
            assertThat(repository.getMemberId()).isNull()
            assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
            assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        }
        coVerify(exactly = 0) { api.postSignUp(any()) }
    }

    @Test
    fun `회원가입 응답 대기 중 계정이 바뀌면 서버 성공이어도 회원을 덮어쓰지 않는다`() = runTest {
        registered(registered = false)
        repository.confirmAccountAdoption(confirmation())
        coEvery { api.postSignUp(any()) } answers {
            token = jwt("subject-c")
            signupResponse()
        }
        assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
        assertThat(preferences.getRetainedMemberId()).isNull()
        assertThat(preferences.getPendingLogin()).isNotNull()
    }

    @Test
    fun `프로세스 재생성 후에도 게이트는 닫혀 있고 이전 승인은 새 확인에 사용할 수 없다`() = runTest {
        val id = seedPending()
        registered()
        val oldApproval = confirmation()
        val restoredPreferences = PreferenceManager(context)
        repository = newRepository(restoredPreferences)
        assertThat(repository.getMemberId()).isNull()
        val restored = repository.resumePendingLogin() as RegistrationStatus.ConfirmationRequired
        assertThat(restored.confirmationId).isNotEqualTo(oldApproval)
        assertThat(repository.confirmAccountAdoption(oldApproval)).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(restoredPreferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
    }

    @Test
    fun `회원 식별자가 없어도 남은 DB와 사진은 등록 회원 진입 전에 승인이 필요하다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)
        preferences.clearMemberId()
        registered()
        assertThat(repository.checkIsRegistered()).isInstanceOf(RegistrationStatus.ConfirmationRequired::class.java)
        assertThat(preferences.getRetainedMemberId()).isNull()
        assertThat(repository.getMemberId()).isNull()
        assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        assertThat(File(root, "original.png").exists()).isTrue()
    }

    @Test
    fun `회원 식별자가 없는 로그인 취소도 고아 원문과 사진을 보존하고 승인을 무효화한다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)
        preferences.clearMemberId()
        registered()
        val approval = confirmation()
        ClearSessionUseCase(repository, mockk(relaxed = true), mockk(relaxed = true), cleaner, mockk(relaxed = true))(
            SessionExitReason.FORCED,
        )
        assertThat(token).isNull()
        assertThat(preferences.getRetainedMemberId()).isNull()
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        token = jwt("subject-b")
        assertThat(repository.checkIsRegistered()).isInstanceOf(RegistrationStatus.ConfirmationRequired::class.java)
    }

    @Test
    fun `회원 식별자가 없는 고아 데이터도 묶인 승인 뒤에만 삭제하고 대상 회원을 연다`() = runTest {
        seedPending()
        preferences.clearMemberId()
        registered()
        val approval = confirmation()
        assertThat(repository.confirmAccountAdoption("unrelated-approval")).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Registered)
        assertThat(preferences.getMemberId()).isEqualTo("member-b")
        assertThat(preferences.getMemberSubject()).isEqualTo("subject-b")
        assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
        assertThat(File(root, "exercise-images/owned.png").exists()).isFalse()
        assertThat(File(root, "original.png").exists()).isTrue()
    }

    @Test
    fun `회원 식별자가 없어도 고아 데이터가 있으면 미가입 계정의 가입 전에 승인해야 한다`() = runTest {
        val id = seedPending()
        val before = database.exerciseSyncDao().get("member-a", id)
        preferences.clearMemberId()
        registered(registered = false)
        val approval = confirmation()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
        assertThat(database.exerciseSyncDao().get("member-a", id)).isEqualTo(before)
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        coVerify(exactly = 0) { api.postSignUp(any()) }
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Unregistered)
        assertThat(database.exerciseSyncDao().getByMember("member-a")).isEmpty()
        assertThat(File(root, "exercise-images/owned.png").exists()).isFalse()
        assertThat(File(root, "original.png").exists()).isTrue()
        stubSignup()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isEqualTo(BaseResult.Success(Unit))
        assertThat(preferences.getMemberId()).isEqualTo("member-new")
    }

    @Test
    fun `DB가 비어 있어도 앱 소유 사진만 남아 있으면 승인 전에는 지우지 않는다`() = runTest {
        preferences.clearMemberId()
        File(root, "exercise-images").mkdirs()
        val owned = File(root, "exercise-images/orphan.png").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val original = File(root, "original.png").also { it.writeBytes(byteArrayOf(4, 5, 6)) }
        registered()
        val approval = confirmation()
        assertThat(repository.getMemberId()).isNull()
        assertThat(owned.exists()).isTrue()
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Registered)
        assertThat(owned.exists()).isFalse()
        assertThat(original.exists()).isTrue()
    }

    @Test
    fun `타이머 스냅샷만 남아 있어도 회원 식별자가 없다는 이유로 삭제하지 않는다`() = runTest {
        val snapshot = TimerSnapshotEntity("unknown-member", TimerSnapshotEntity.SIMPLE, true, 7L)
        database.timerDao().upsertSnapshot(snapshot)
        preferences.clearMemberId()
        registered()
        val approval = confirmation()
        assertThat(database.timerDao().getSnapshot("unknown-member", TimerSnapshotEntity.SIMPLE)).isEqualTo(snapshot)
        assertThat(repository.getMemberId()).isNull()
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Registered)
        assertThat(database.timerDao().getSnapshot("unknown-member", TimerSnapshotEntity.SIMPLE)).isNull()
    }

    @Test
    fun `실제 사용자 저장소가 빈 최초 로그인은 경고 없이 등록 회원을 열고 외부 원본은 남긴다`() = runTest {
        preferences.clearMemberId()
        File(root, "exercise-images").mkdirs()
        val original = File(root, "original.png").also { it.writeBytes(byteArrayOf(4, 5, 6)) }
        registered()
        assertThat(repository.checkIsRegistered()).isEqualTo(RegistrationStatus.Registered)
        assertThat(preferences.getMemberId()).isEqualTo("member-b")
        assertThat(preferences.getPendingLogin()).isNull()
        assertThat(original.exists()).isTrue()
    }

    @Test
    fun `빈 저장소 확인 뒤 정리 전에 나타난 DB 행은 승인 없이 지우거나 계정을 열지 않는다`() = runTest {
        preferences.clearMemberId()
        registered()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val realCleaner = RoomSessionDataCleaner(database, dispatcher, files)
        coEvery { cleaner.clearForAccountAdoption(any(), any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            realCleaner.clearForAccountAdoption(firstArg(), secondArg(), thirdArg())
        }
        val checking = async { repository.checkIsRegistered() }
        entered.await()
        val snapshot = TimerSnapshotEntity("unknown-member", TimerSnapshotEntity.CUSTOM, true, 3L)
        database.timerDao().upsertSnapshot(snapshot)
        release.complete(Unit)
        assertThat(checking.await()).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(database.timerDao().getSnapshot("unknown-member", TimerSnapshotEntity.CUSTOM)).isEqualTo(snapshot)
        assertThat(repository.getMemberId()).isNull()
        assertThat(preferences.getRetainedMemberId()).isNull()
        assertThat(repository.checkIsRegistered()).isInstanceOf(RegistrationStatus.ConfirmationRequired::class.java)
    }

    @Test
    fun `빈 저장소 확인 뒤 정리 잠금 대기 중 나타난 사진도 승인 없이는 삭제하지 않는다`() = runTest {
        preferences.clearMemberId()
        registered(registered = false)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val realCleaner = RoomSessionDataCleaner(database, dispatcher, files)
        coEvery { cleaner.clearForAccountAdoption(any(), any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            realCleaner.clearForAccountAdoption(firstArg(), secondArg(), thirdArg())
        }
        val checking = async { repository.checkIsRegistered() }
        entered.await()
        val holding = async {
            files.withLock {
                File(root, "exercise-images").mkdirs()
                val owned = File(root, "exercise-images/late.png").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
                release.complete(Unit)
                testScheduler.runCurrent()
                assertThat(checking.isCompleted).isFalse()
                owned
            }
        }
        val owned = holding.await()
        assertThat(checking.await()).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(owned.exists()).isTrue()
        assertThat(repository.getMemberId()).isNull()
        assertThat(preferences.getPendingLogin()?.signupAllowed).isFalse()
        assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
        assertThat(repository.checkIsRegistered()).isInstanceOf(RegistrationStatus.ConfirmationRequired::class.java)
        coVerify(exactly = 0) { api.postSignUp(any()) }
    }

    @Test
    fun `등록 조회가 불확실하거나 요청 중 subject가 바뀌면 회원과 데이터는 그대로 차단한다`() = runTest {
        val id = seedPending()
        coEvery { api.getIsRegistered() } throws IOException("offline")
        assertThat(repository.checkIsRegistered()).isEqualTo(RegistrationStatus.Indeterminate)
        coEvery { api.getIsRegistered() } answers {
            token = jwt("subject-c")
            response(GetIsRegisteredData("member-b", true))
        }
        assertThat(repository.checkIsRegistered()).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(repository.getMemberId()).isNull()
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    @Test
    fun `미가입에서 등록으로 결과가 바뀐 승인은 회원가입 경계를 열지 않는다`() = runTest {
        val id = seedPending()
        registered(registered = false)
        val approval = confirmation()
        registered()
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(repository.signUp("FEMALE", "회원", birth)).isInstanceOf(BaseResult.Error::class.java)
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        coVerify(exactly = 0) { api.postSignUp(any()) }
    }

    @Test
    fun `승인 이후 subject만 바뀌어도 기존 회원의 DB와 사진을 삭제하지 않는다`() = runTest {
        val id = seedPending()
        registered()
        val approval = confirmation()
        token = jwt("subject-c")
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
        coVerify(exactly = 1) { api.getIsRegistered() }
    }

    @Test
    fun `승인 이후 보존 회원이 교체되면 이전 승인으로 다른 회원을 덮어쓰지 않는다`() = runTest {
        val id = seedPending()
        registered()
        val approval = confirmation()
        preferences.saveMemberId("member-c", "subject-c")
        assertThat(repository.confirmAccountAdoption(approval)).isEqualTo(RegistrationStatus.Indeterminate)
        assertThat(preferences.getRetainedMemberId()).isEqualTo("member-c")
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    @Test
    fun `자격 증명 삭제 실패는 영속 차단을 열지 않고 원문과 사진을 보존한다`() = runTest {
        val id = seedPending()
        registered()
        confirmation()
        every { auth.clearAuthState() } throws IllegalStateException("credential-storage")
        assertThat(runCatching { repository.clearTokens() }.exceptionOrNull()).isNotNull()
        val restored = PreferenceManager(context)
        assertThat(restored.getMemberId()).isNull()
        assertThat(restored.getRetainedMemberId()).isEqualTo("member-a")
        assertThat(token).isEqualTo(jwt("subject-b"))
        assertThat(database.exerciseSyncDao().get("member-a", id)).isNotNull()
        assertThat(File(root, "exercise-images/owned.png").exists()).isTrue()
    }

    private fun stubSignup() {
        coEvery { api.postSignUp(any()) } returns signupResponse()
    }

    private fun signupResponse() = response(
        PostSignUpData(
            memberId = "member-new", memberEmail = "member@example.test", memberGender = "FEMALE",
            memberBday = birth, memberNickname = "회원", memberDesc = null, memberScore = null,
            memberCreatedAt = LocalDateTime.of(2026, 10, 30, 0, 0),
        ),
    )
}
