package com.project200.data.datasource

import com.google.common.truth.Truth.assertThat
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.PictureData
import com.project200.data.impl.RoomSessionDataCleaner
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.ExerciseImageEntity
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExercisePointReceiptEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ExerciseSyncUiStorageTest {
    private lateinit var db: UndabangDatabase
    private lateinit var local: ExerciseRecordLocalDataSource
    private lateinit var sync: ExerciseSyncLocalDataSource
    private val member = "member"
    private val day = LocalDate.of(2026, 10, 20)
    private val preferences = mockk<PreferenceManager>()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private var nextServerId = 7L
    private val record = ExerciseRecord("원문", "본문", "OTHER", day.atTime(9, 0), day.atTime(10, 0), "장소", null)

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        every { preferences.getMemberId() } returns member
        local = ExerciseRecordLocalDataSource(db, db.exerciseCountDao(), db.exerciseRecordDao(), preferences)
        sync = ExerciseSyncLocalDataSource(db, moshi)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun created(
        points: Int? = 3,
        version: Long = 0,
    ): String {
        val id = local.createRecord(ExerciseWriteInput(record), editedAt = 100).recordId
        val request = requireNotNull(sync.claim(member, id))
        assertThat(sync.applyBody(request, nextServerId++, version, day, points)).isTrue()
        return id
    }

    private suspend fun edit(
        id: String,
        clock: Long = 200,
        title: String = "로컬",
    ) {
        local.updateRecord(id, ExerciseWriteInput(record.copy(title = title)), editedAt = clock)
    }

    private fun remote(
        clock: Long?,
        version: Long = 4,
    ) = GetExerciseRecordData(
        "서버",
        "서버 본문",
        "OTHER",
        day.atTime(9, 0),
        day.atTime(10, 0),
        "서버 장소",
        listOf(PictureData(91, "https://example.test/91", "91", "jpg")),
        version,
        clock,
    )

    @Test
    fun `사용자 저장 시각과 in flight 원문은 재시도와 재편집 뒤에도 고정된다`() =
        runTest {
            val id = created()
            edit(id)
            val first = requireNotNull(sync.claim(member, id))
            val bytes = db.exerciseSyncDao().get(member, id)!!.inFlightBody
            edit(id, 300, "새 편집")
            assertThat(sync.claim(member, id)).isEqualTo(first)
            assertThat(db.exerciseSyncDao().get(member, id)!!.inFlightBody).isEqualTo(bytes)
            assertThat(first.editRequest().editedAt).isEqualTo(200)
            sync.applyBody(first, 7, 8, day)
            val next = requireNotNull(sync.claim(member, id))
            assertThat(next.editedAt).isEqualTo(300)
            assertThat(next.body!!.exerciseTitle).isEqualTo("새 편집")
            assertThat(next.requestId).isNotEqualTo(first.requestId)
        }

    @Test
    fun `같은 시각과 더 최신 서버 본문은 자동으로 남고 대기 사진과 원격 매핑은 보존된다`() =
        runTest {
            for (clock in listOf(200L, 300L)) {
                val id = created()
                edit(id)
                db.exerciseImageDao().save(
                    ExerciseImageEntity(
                        member,
                        id,
                        "local-photo",
                        null,
                        "/owned/photo.jpg",
                        null,
                        ExerciseImageState.ADD_LOCAL,
                        "photo-key",
                        0,
                    ),
                )
                val snapshot = requireNotNull(sync.claim(member, id))
                assertThat(sync.resolveBodyConflict(snapshot, remote(clock))).isFalse()
                assertThat(db.exerciseRecordDao().getDetailByLocalId(member, id)!!.title).isEqualTo("서버")
                assertThat(db.exerciseImageDao().getByRecord(member, id).map { it.localId }).contains("local-photo")
                assertThat(db.exerciseImageDao().getByRecord(member, id).mapNotNull { it.serverPictureId }).contains(91L)
                assertThat(sync.consumePointNotice(member, setOf(id))).isNull()
                assertThat(sync.observeStatus(member, true, true, false).first().single { it.recordId == id }.complete).isFalse()
            }
        }

    @Test
    fun `더 최신 로컬은 확정 거절 뒤에만 새 요청키로 rebase하고 저장 시각 본문은 바꾸지 않는다`() =
        runTest {
            val id = created()
            edit(id, 400)
            val first = requireNotNull(sync.claim(member, id))
            assertThat(sync.resolveBodyConflict(first, remote(300))).isTrue()
            val next = requireNotNull(sync.claim(member, id))
            assertThat(next.requestId).isNotEqualTo(first.requestId)
            assertThat(next.body).isEqualTo(first.body)
            assertThat(next.editedAt).isEqualTo(first.editedAt)
            assertThat(next.baseVersion).isEqualTo(4)
        }

    @Test
    fun `늦은 자동 서버 승자는 더 최신 로컬 편집과 전체 삭제를 덮어쓰지 않는다`() =
        runTest {
            for (delete in listOf(false, true)) {
                val id = created()
                edit(id)
                val first = requireNotNull(sync.claim(member, id))
                if (delete) local.deleteRecord(id) else edit(id, 500, "후속 편집")
                assertThat(sync.resolveBodyConflict(first, remote(300))).isTrue()
                val journal = db.exerciseSyncDao().get(member, id)!!
                assertThat(journal.operation).isEqualTo(if (delete) SyncState.DELETE_PENDING else SyncState.UPDATE_PENDING)
                if (!delete) {
                    assertThat(journal.desiredEditedAt).isEqualTo(500)
                    assertThat(local.getDetailByLocalId(id)!!.title).isEqualTo("후속 편집")
                } else {
                    assertThat(local.getDetailByLocalId(id)).isNull()
                }
            }
        }

    @Test
    fun `알 수 없는 과거 시각은 충돌 원문을 보존하고 새 사용자 저장만 실제 시각을 만든다`() =
        runTest {
            val id = created()
            edit(id)
            db.exerciseSyncDao().saveSyncState(db.exerciseSyncDao().get(member, id)!!.copy(desiredEditedAt = null))
            val legacy = requireNotNull(sync.claim(member, id))
            val bytes = db.exerciseSyncDao().get(member, id)!!.inFlightBody
            assertThat(legacy.editedAt).isNull()
            assertThat(legacy.editRequest().editedAt).isNull()
            assertThat(sync.resolveBodyConflict(legacy, remote(300))).isNull()
            sync.fail(legacy, false, ExerciseSyncError.CONFLICT)
            assertThat(sync.claim(member, id)).isNull()
            assertThat(db.exerciseSyncDao().get(member, id)!!.inFlightBody).isEqualTo(bytes)
            edit(id, 600, "의도적 새 저장")
            assertThat(sync.claim(member, id)!!.editedAt).isEqualTo(600)
        }

    @Test
    fun `미확인 서버 시각과 계약 역행 version은 본문을 자동 교체하지 않는다`() =
        runTest {
            val id = created(version = 8)
            edit(id)
            val first = requireNotNull(sync.claim(member, id))
            assertThat(sync.resolveBodyConflict(first, remote(null))).isNull()
            assertThat(sync.resolveBodyConflict(first, remote(0))).isNull()
            assertThat(sync.resolveBodyConflict(first, remote(300, -1))).isNull()
            assertThat(sync.resolveBodyConflict(first, remote(300, 4))).isNull()
            assertThat(local.getDetailByLocalId(id)!!.title).isEqualTo("로컬")
        }

    @Test
    fun `생성 영수증은 저널과 분리하고 0점과 unknown 및 중복 소비를 구별한다`() =
        runTest {
            val unknown = created(null)
            val zero = created(0)
            val positive = created(3)
            assertThat(db.exerciseSyncDao().getByMember(member)).isEmpty()
            assertThat(db.exercisePointReceiptDao().getByMember(member)).hasSize(2)
            assertThat(sync.consumePointNotice(member, setOf(unknown))).isNull()
            assertThat(sync.consumePointNotice(member, setOf(zero))!!.earnedPoints).isEqualTo(0)
            val receipt = db.exercisePointReceiptDao().getByMember(member).single { it.recordLocalId == zero }
            db.exercisePointReceiptDao().acknowledge(receipt.copy(noticeConsumed = false))
            assertThat(sync.consumePointNotice(member, setOf(zero))).isNull()
            assertThat(sync.consumePointNotice(member, setOf(positive))!!.earnedPoints).isEqualTo(3)
            assertThat(sync.consumePointNotice(member, setOf(positive))).isNull()
            assertThat(db.exercisePointReceiptDao().getByMember(member).all { it.noticeConsumed }).isTrue()
        }

    @Test
    fun `영수증 삽입 실패는 ACK 투영과 저널 제거를 모두 롤백하고 다른 점수 재생을 거부한다`() =
        runTest {
            val id = local.createRecord(ExerciseWriteInput(record), editedAt = 100).recordId
            val first = requireNotNull(sync.claim(member, id))
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_receipt BEFORE INSERT ON exercise_point_receipt BEGIN SELECT RAISE(ABORT, 'receipt'); END",
            )
            assertThat(runCatching { sync.applyBody(first, 7, 0, day, 3) }.isFailure).isTrue()
            assertThat(db.exerciseSyncDao().get(member, id)!!.inFlightRequestId).isEqualTo(first.requestId)
            assertThat(db.exerciseRecordDao().getDetailByLocalId(member, id)!!.serverId).isNull()
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_receipt")
            assertThat(sync.applyBody(first, 7, 0, day, 3)).isTrue()
            val receipt = db.exercisePointReceiptDao().getByMember(member).single()
            assertThat(runCatching { db.exercisePointReceiptDao().acknowledge(receipt.copy(earnedPoints = 4)) }.isFailure).isTrue()
            assertThat(db.exercisePointReceiptDao().getByMember(member).single()).isEqualTo(receipt)
        }

    @Test
    fun `영수증만 남아도 다른 계정 채택은 승인해야 하며 강제 이탈 없이 보존한다`() =
        runTest {
            db.exercisePointReceiptDao().acknowledge(ExercisePointReceiptEntity(member, "key", "record", 7, 3, false))
            val context = RuntimeEnvironment.getApplication()
            val files = ExerciseImageFileStore(context)
            val cleaner = RoomSessionDataCleaner(db, StandardTestDispatcher(testScheduler), files)
            assertThat(cleaner.hasStoredData()).isTrue()
            var adopted = false
            assertThat(cleaner.clearForAccountAdoption(false, { true }, { adopted = true })).isFalse()
            assertThat(adopted).isFalse()
            assertThat(db.exercisePointReceiptDao().getByMember(member)).hasSize(1)
            assertThat(cleaner.clearForAccountAdoption(true, { true }, { adopted = true })).isTrue()
            assertThat(adopted).isTrue()
            assertThat(db.exercisePointReceiptDao().getByMember(member)).isEmpty()
        }

    @Test
    fun `원격 삭제는 명명된 오류 뒤 승인 폐기만 원문과 미소비 점수를 지운다`() =
        runTest {
            val id = created()
            edit(id)
            val snapshot = requireNotNull(sync.claim(member, id))
            sync.fail(snapshot, false, ExerciseSyncError.REMOTE_DELETED)
            assertThat(db.exerciseSyncDao().get(member, id)!!.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(sync.claim(member, id)).isNull()
            assertThat(local.getDetailByLocalId(id)!!.title).isEqualTo("로컬")
            assertThat(sync.consumePointNotice(member, setOf(id))).isNull()
            assertThat(sync.discardRemoteDeleted("other", id)).isFalse()
            assertThat(sync.discardRemoteDeleted(member, id)).isTrue()
            assertThat(db.exerciseRecordDao().getDetailByLocalId(member, id)).isNull()
            assertThat(db.exercisePointReceiptDao().getByMember(member)).isEmpty()
        }

    @Test
    fun `사진만 저장하는 오래된 폼은 자동 승자 본문과 시각을 다시 쓰지 않는다`() =
        runTest {
            val id = created()
            edit(id)
            val first = requireNotNull(sync.claim(member, id))
            assertThat(sync.resolveBodyConflict(first, remote(300))).isFalse()
            val picture = db.exerciseImageDao().getByRecord(member, id).single()
            local.updateRecord(
                id,
                ExerciseWriteInput(record, removedImageLocalIds = setOf(picture.localId), bodyEdited = false),
                editedAt = 900,
            )
            val stored = requireNotNull(db.exerciseRecordDao().getDetailByLocalId(member, id))
            assertThat(stored.title).isEqualTo("서버")
            assertThat(stored.editedAt).isEqualTo(300)
            assertThat(db.exerciseSyncDao().get(member, id)).isNull()
            assertThat(db.exerciseImageDao().getByRecord(member, id).single().state).isEqualTo(ExerciseImageState.DELETE_PENDING)
            assertThat(sync.consumePointNotice(member, setOf(id))).isNull()
        }

    @Test
    fun `시각만 남은 불완전한 in flight는 확실한 미전송으로 간주해 삭제하지 않는다`() =
        runTest {
            val id = local.createRecord(ExerciseWriteInput(record), editedAt = 100).recordId
            val original = requireNotNull(db.exerciseSyncDao().get(member, id))
            db.exerciseSyncDao().saveSyncState(original.copy(inFlightEditedAt = 100))
            local.deleteRecord(id)
            val retained = requireNotNull(db.exerciseSyncDao().get(member, id))
            assertThat(retained.operation).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(retained.inFlightEditedAt).isEqualTo(100)
            assertThat(retained.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(db.exerciseRecordDao().getDetailByLocalId(member, id)!!.title).isEqualTo(record.title)
            assertThat(sync.claim(member, id)).isNull()
        }
}
