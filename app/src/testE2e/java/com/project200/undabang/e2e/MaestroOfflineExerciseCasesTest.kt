package com.project200.undabang.e2e

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.datasource.PreparedExerciseImage
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.data.dto.PictureData
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MaestroOfflineExerciseCasesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val api = mockk<ApiService>()
    private val preferences = mockk<PreferenceManager>()
    private val transport = mockk<MaestroOfflineTransport>()
    private val memberId = "member"
    private var activeMember: String? = memberId
    private val name = "maestro-offline-cases.db"
    private val run = MaestroOfflineRun(
        "0123456789abcdef0123456789abcdef", LocalDateTime.of(2026, 1, 1, 9, 0),
        LocalDateTime.of(2026, 1, 1, 10, 0), MaestroOfflineTimerInput(45, 75, "offline-step"),
    )
    private val request = MaestroFeatureRequest("offline", "offline-session-retain", run.id, memberId, JSONObject())
    private lateinit var database: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource
    private lateinit var fixture: MaestroOfflineExerciseCases
    private lateinit var saved: JSONObject
    private val owned = mutableListOf<PreparedExerciseImage>()
    private val source get() = File(context.cacheDir, "maestro-retention-unit-source.png")

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        every { preferences.getMemberId() } answers { activeMember }
        openDatabase()
        saved = JSONObject().put("flow", request.flow).put("baselineExerciseLocalIds", JSONArray())
            .put("extendedExercise", JSONObject()).put("media", JSONObject().put("copies", JSONArray()))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
        owned.forEach { File(it.path).delete() }
        source.delete()
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, UndabangDatabase::class.java, name)
            .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        local = ExerciseRecordLocalDataSource(database, database.exerciseCountDao(), database.exerciseRecordDao(), preferences)
        fixture = MaestroOfflineExerciseCases(api, database, context, transport, preferences)
    }

    private suspend fun createFromProduct(): String {
        val bitmap = MaestroOfflinePhotoMedia.bitmap("A")
        try { source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        saved.getJSONObject("media").put("A", JSONObject().put("sha256", MaestroOfflinePhotoMedia.sha256(source.readBytes())))
        val uri = source.toURI().toString()
        val prepared = ExerciseImageFileStore(context).prepare(listOf(uri))
        owned.addAll(prepared)
        val input = ExerciseWriteInput(
            ExerciseRecord(run.exerciseTitle, "e2e-body-${run.id}", "", run.startedAt, run.endedAt, "", null), listOf(uri),
        )
        val id = local.createRecord(input, preparedImages = prepared).recordId
        fixture.observe(request, run, saved, "exercise-case-local-created")
        return id
    }

    @Test
    fun `real product pending body photo and request proof survives database reopen and signed out observation`() = runTest {
        val id = createFromProduct()
        val journal = database.exerciseSyncDao().get(memberId, id)
        val image = database.exerciseImageDao().getByRecord(memberId, id).single()
        val bytes = File(image.localFilePath!!).readBytes()
        val proof = saved.getJSONObject("extendedExercise").getJSONObject("firstPending").toString()
        database.close()
        activeMember = null
        openDatabase()
        fixture.assertRetained(request, run, saved)
        assertThat(database.exerciseSyncDao().get(memberId, id)).isEqualTo(journal)
        assertThat(database.exerciseImageDao().getByRecord(memberId, id).single()).isEqualTo(image)
        assertThat(File(image.localFilePath!!).readBytes()).isEqualTo(bytes)
        assertThat(saved.getJSONObject("extendedExercise").getJSONObject("firstPending").toString()).isEqualTo(proof)
        coVerify(exactly = 0) { api.postExerciseRecord(any()) }
        coVerify(exactly = 0) { api.patchExerciseRecord(any(), any()) }
        coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
    }

    @Test
    fun `unsafe cleanup never removes pending originals or replaces first snapshot`() = runTest {
        val id = createFromProduct()
        val file = File(database.exerciseImageDao().getByRecord(memberId, id).single().localFilePath!!)
        val before = file.readBytes()
        val snapshot = saved.toString()
        failure("offline_exercise_cleanup_unsafe") { fixture.cleanup(request, run, saved) }
        assertThat(file.readBytes()).isEqualTo(before)
        assertThat(saved.toString()).isEqualTo(snapshot)
        assertThat(database.exerciseSyncDao().get(memberId, id)).isNotNull()
        coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
        verify(exactly = 0) { transport.finishTrace(any(), any(), any()) }
    }

    @Test
    fun `changed owned bytes fail retention without deleting row or damaged original`() = runTest {
        val id = createFromProduct()
        val file = File(database.exerciseImageDao().getByRecord(memberId, id).single().localFilePath!!)
        file.appendBytes(byteArrayOf(42))
        val damaged = file.readBytes()
        failure("offline_photo_retention_mismatch") { fixture.assertRetained(request, run, saved) }
        assertThat(file.readBytes()).isEqualTo(damaged)
        assertThat(database.exerciseSyncDao().get(memberId, id)).isNotNull()
    }

    @Test
    fun `approval flag cannot substitute for actual product erasure or clean session baseline`() = runTest {
        val id = createFromProduct()
        saved.put("session", JSONObject().put("approvedErasure", true).put("cleanBaseline", true))
        failure("offline_exercise_erasure_not_observed") { fixture.requireSafeCleanup(request, run, saved) }
        saved.getJSONObject("session").put("cleanBaseline", false)
        failure("offline_exercise_cleanup_unsafe") { fixture.requireSafeCleanup(request, run, saved) }
        assertThat(database.exerciseSyncDao().get(memberId, id)).isNotNull()
        assertThat(File(owned.single().path).exists()).isTrue()
    }

    @Test
    fun `baseline IDs and unowned remote actions cannot adopt pending records`() = runTest {
        val id = createFromProduct()
        saved.getJSONArray("baselineExerciseLocalIds").put(id)
        failure("offline_exercise_identity_changed") { fixture.observe(request, run, saved, "exercise-case-local-created") }
        failure("invalid_request") { fixture.control(request, run, saved, "remote-update") }
        coVerify(exactly = 0) { api.patchExerciseRecord(any(), any()) }
        coVerify(exactly = 0) { api.deleteExerciseRecord(any()) }
    }

    @Test
    fun `matching independent server body and photo count cannot pass while real Room still has pending originals`() = runTest {
        val id = createFromProduct()
        coEvery { api.getExerciseList(run.startedAt.toLocalDate()) } returns BaseResponse(
            true, "OK", "", listOf(GetExerciseRecordListDto(101, run.exerciseTitle, "", run.startedAt, run.endedAt, emptyList(), 1)),
        )
        coEvery { api.getExerciseRecordDetail(101) } returns BaseResponse(
            true, "OK", "", GetExerciseRecordData(run.exerciseTitle, "e2e-body-${run.id}", "", run.startedAt, run.endedAt,
                "", listOf(PictureData(901, "", "synthetic", "png")), 1, System.currentTimeMillis()),
        )
        val before = File(owned.single().path).readBytes()
        failure("offline_upload_not_observed") { fixture.observe(request, run, saved, "exercise-case-complete") }
        assertThat(database.exerciseSyncDao().get(memberId, id)).isNotNull()
        assertThat(File(owned.single().path).readBytes()).isEqualTo(before)
        assertThat(saved.getJSONObject("extendedExercise").optBoolean("completed")).isFalse()
        coVerify(exactly = 0) { api.postExerciseRecord(any()) }
    }

    @Test
    fun `prepared namespace alone never proves that the user created an exercise`() = runTest {
        failure("offline_exercise_local_mismatch") { fixture.observe(request, run, saved, "exercise-case-local-created") }
        assertThat(saved.getJSONObject("extendedExercise").has("retained")).isFalse()
        coVerify(exactly = 0) { api.postExerciseRecord(any()) }
    }

    private suspend fun failure(code: String, block: suspend () -> Unit) {
        try { block(); throw AssertionError("Expected private fixture failure") } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(code)
        }
    }
}
