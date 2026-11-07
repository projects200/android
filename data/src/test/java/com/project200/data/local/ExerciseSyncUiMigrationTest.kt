package com.project200.data.local

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.project200.data.datasource.ExerciseSyncLocalDataSource
import com.project200.data.di.DatabaseModule
import com.project200.data.local.entity.ExerciseImageState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExerciseSyncUiMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val name = "exercise-sync-ui-migration.db"
    private var database: UndabangDatabase? = null
    private val operation = "00000000-0000-0000-0000-000000000001"
    private val request = "00000000-0000-0000-0000-000000000002"
    private val original =
        """{"memberId":"member","localId":"record","kind":"BODY","operation":"UPDATE_PENDING","revision":1,"operationId":"$operation","requestId":"$request","serverId":7,"baseVersion":4,"submittedDate":"2026-10-20","previousDate":"2026-10-20","body":{"exerciseTitle":"원문","exercisePersonalType":"OTHER","exerciseLocation":"장소","exerciseDetail":"본문","exerciseStartedAt":"2026-10-20T09:00:00","exerciseEndedAt":"2026-10-20T10:00:00"}}"""

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `실제 v6 저널 사진 원문 요청키는 v7과 재개방에서 시각을 발명하지 않고 보존한다`() =
        runTest {
            createHistoricalDatabase(context, name, 6) { raw ->
                raw.execSQL(
                    "INSERT INTO exercise_sync (memberId, localId, serverId, operation, revision, operationId, " +
                        "createRequestId, serverVersion, confirmedDate, currentDate, recoveryState, lastError, " +
                        "inFlightOperation, inFlightRevision, inFlightOperationId, inFlightRequestId, inFlightBody, " +
                        "inFlightBaseVersion) VALUES ('member', 'record', 7, 'UPDATE_PENDING', 2, 'desired-key', " +
                        "'create-key', 4, '2026-10-20', '2026-10-21', 'RETRYABLE', 'NETWORK_ERROR', " +
                        "'UPDATE_PENDING', 1, '$operation', '$request', ?, 4)",
                    arrayOf(original),
                )
                raw.execSQL(
                    "INSERT INTO exercise_image VALUES " +
                        "('member', 'record', 'image', NULL, '/owned/original.jpg', NULL, 'ADD_PENDING', 'photo-key', 0)",
                )
                raw.execSQL(
                    "INSERT INTO exercise_record_detail " +
                        "(memberId, localId, serverId, syncState, title, detail, personalType, startedAt, endedAt, location, pictures) " +
                        "VALUES ('member', 'record', 7, 'UPDATE_PENDING', '원문', '편집 중 본문', 'OTHER', " +
                        "'2026-10-21T09:00', '2026-10-21T10:00', '장소', NULL)",
                )
            }
            repeat(2) {
                val db = open()
                val journal = requireNotNull(db.exerciseSyncDao().get("member", "record"))
                assertThat(journal.desiredEditedAt).isNull()
                assertThat(journal.inFlightEditedAt).isNull()
                assertThat(journal.inFlightBody).isEqualTo(original)
                assertThat(journal.operationId).isEqualTo("desired-key")
                assertThat(journal.inFlightRequestId).isEqualTo(request)
                assertThat(journal.inFlightBaseVersion).isEqualTo(4)
                val detail = requireNotNull(db.exerciseRecordDao().getDetailByLocalId("member", "record"))
                assertThat(detail.detail).isEqualTo("편집 중 본문")
                assertThat(detail.editedAt).isNull()
                val photo = db.exerciseImageDao().getByRecord("member", "record").single()
                assertThat(photo.state).isEqualTo(ExerciseImageState.ADD_PENDING)
                assertThat(photo.localFilePath).isEqualTo("/owned/original.jpg")
                assertThat(photo.requestId).isEqualTo("photo-key")
                assertThat(db.exercisePointReceiptDao().getByMember("member")).isEmpty()
                val sync = ExerciseSyncLocalDataSource(db, Moshi.Builder().add(KotlinJsonAdapterFactory()).build())
                val replay = requireNotNull(sync.claim("member", "record"))
                assertThat(replay.editedAt).isNull()
                assertThat(replay.editRequest().editId).isEqualTo(request)
                assertThat(replay.editRequest().editedAt).isNull()
                assertThat(db.exerciseSyncDao().get("member", "record")!!.inFlightBody).isEqualTo(original)
                db.close()
            }
        }

    private fun open(): UndabangDatabase =
        Room.databaseBuilder(context, UndabangDatabase::class.java, name)
            .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .addMigrations(DatabaseModule.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build().also { database = it }
}
