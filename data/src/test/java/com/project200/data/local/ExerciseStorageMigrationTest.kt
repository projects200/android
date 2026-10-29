package com.project200.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.project200.data.di.DatabaseModule
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ExerciseStorageMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val name = "exercise-storage-migration.db"
    private var database: UndabangDatabase? = null
    private val date = LocalDate.parse("2026-10-10")
    private val pictures = "[ {\"id\":12,\"url\":\"https://example.test/twelve\"}, {\"id\":11,\"url\":\"https://example.test/eleven\"} ]"

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `실제 스키마 4는 모든 운동 원문과 타이머 전송 상태를 보존하고 재개방한다`() =
        runTest { assertMigration(4) }

    @Test
    fun `실제 스키마 5는 모든 운동 원문과 타이머 조회 이력을 보존하고 재개방한다`() =
        runTest { assertMigration(5) }

    private suspend fun assertMigration(version: Int) {
        var before = emptyMap<String, List<List<String?>>>()
        createHistoricalDatabase(context, name, version) { raw ->
            seed(raw, version)
            before = originals(raw, version)
        }
        var db = openDatabase()
        assertThat(db.openHelper.writableDatabase.version).isEqualTo(6)
        assertThat(originals(db.openHelper.writableDatabase, version)).isEqualTo(before)
        assertBackfill(db)
        db.close()
        db = openDatabase()
        assertThat(originals(db.openHelper.writableDatabase, version)).isEqualTo(before)
        assertBackfill(db)
    }

    private suspend fun assertBackfill(db: UndabangDatabase) {
        val records = db.exerciseRecordDao()
        val journal = db.exerciseSyncDao()
        val synced = records.getListItem("member-a", "synced")!!
        // 운동 시작일과 달라도 실제 목록 조회일을 보존합니다
        assertThat(synced.startedAt.toLocalDate()).isEqualTo(date.minusDays(1))
        assertThat(synced.confirmedDate).isEqualTo(date)
        assertThat(synced.serverVersion).isNull()
        assertThat(records.getDetailByLocalId("member-a", "synced")?.confirmedDate).isEqualTo(date)
        assertThat(journal.get("member-a", "synced")).isNull()
        val incomplete = journal.get("member-a", "missing")!!
        assertThat(incomplete.operation).isEqualTo(SyncState.CREATE_PENDING)
        assertThat(incomplete.lastError).isEqualTo(ExerciseSyncError.INCOMPLETE_DETAIL)
        assertThat(records.getListItem("member-a", "missing")?.serverId).isNull()
        val orphan = journal.get("member-a", "orphan")!!
        assertThat(orphan.operation).isEqualTo(SyncState.DELETE_PENDING)
        assertThat(records.getDetailByLocalId("member-a", "orphan")?.syncState).isEqualTo(SyncState.DELETE_PENDING)
        assertThat(records.getDetailByLocalId("member-a", "detail-only")?.confirmedDate).isNull()
        assertThat(journal.get("member-a", "contradiction")?.lastError).isEqualTo(ExerciseSyncError.CONTRADICTORY_STATE)
        assertThat(records.getListItem("member-a", "contradiction")?.syncState).isEqualTo(SyncState.SYNCED)
        assertThat(records.getDetailByLocalId("member-a", "contradiction")?.syncState).isEqualTo(SyncState.UPDATE_PENDING)
        assertThat(journal.get("member-a", "malformed")?.lastError).isEqualTo(ExerciseSyncError.MALFORMED_PICTURES)
        assertThat(db.exerciseImageDao().getByRecord("member-a", "malformed")).isEmpty()
        for (localId in listOf("null-element", "duplicate-picture")) {
            assertThat(journal.get("member-a", localId)?.lastError).isEqualTo(ExerciseSyncError.MALFORMED_PICTURES)
            assertThat(db.exerciseImageDao().getByRecord("member-a", localId)).isEmpty()
        }
        for (member in listOf("member-a", "member-b")) {
            assertThat(journal.getSendable(member)).isEmpty()
            journal.getByMember(member).forEach {
                assertThat(it.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
                assertThat(it.revision).isNull()
                assertThat(it.operationId).isNull()
                assertThat(it.createRequestId).isNull()
                assertThat(it.serverVersion).isNull()
                assertThat(it.confirmedDate).isNull()
                assertThat(it.inFlightOperation).isNull()
                assertThat(it.inFlightBody).isNull()
            }
            val images = db.exerciseImageDao().getByRecord(member, "synced")
            assertThat(images.map { it.serverPictureId }).containsExactly(12L, 11L).inOrder()
            assertThat(images.map { it.localId }).containsExactly("server:12", "server:11").inOrder()
            assertThat(images.map { it.sortOrder }).containsExactly(0, 1).inOrder()
            assertThat(images.map { it.serverUrl })
                .containsExactly("https://example.test/twelve", "https://example.test/eleven").inOrder()
            images.forEach {
                assertThat(it.state).isEqualTo(ExerciseImageState.SYNCED)
                assertThat(it.localFilePath).isNull()
                assertThat(it.requestId).isNull()
            }
        }
        assertThat(records.getDetailByLocalId("member-a", "empty")?.pictures).isEmpty()
        assertThat(records.getDetailByLocalId("member-a", "null")?.pictures).isNull()
        assertThat(records.getListItem("member-a", "pending")?.confirmedDate).isNull()
        assertThat(journal.get("member-a", "pending")?.lastError).isEqualTo(ExerciseSyncError.LEGACY_PENDING)
    }

    private fun seed(
        raw: SupportSQLiteDatabase,
        version: Int,
    ) {
        for (member in listOf("member-a", "member-b")) {
            insertList(raw, member, "synced", 7L, "SYNCED")
            insertDetail(raw, member, "synced", 7L, "SYNCED", pictures)
            insertList(raw, member, "pending", 8L, "UPDATE_PENDING")
            insertDetail(raw, member, "pending", 8L, "UPDATE_PENDING", "[]")
            raw.execSQL("INSERT INTO exercise_count VALUES (?, '2026-10-10', 3)", arrayOf(member))
            raw.execSQL("INSERT INTO exercise_cache_query VALUES (?, 'list', '2026-10-10')", arrayOf(member))
            raw.execSQL("INSERT INTO exercise_cache_query VALUES (?, 'count', '2026-10-10')", arrayOf(member))
        }
        insertList(raw, "member-a", "missing", null, "CREATE_PENDING")
        insertDetail(raw, "member-a", "orphan", 9L, "DELETE_PENDING", null)
        insertDetail(raw, "member-a", "detail-only", 10L, "SYNCED", "[]")
        insertList(raw, "member-a", "contradiction", 13L, "SYNCED")
        insertDetail(raw, "member-a", "contradiction", 13L, "UPDATE_PENDING", "[]")
        insertDetail(raw, "member-a", "malformed", 14L, "SYNCED", "{broken-json")
        insertDetail(raw, "member-a", "null-element", 17L, "SYNCED", "[{\"id\":12,\"url\":\"https://example.test/picture\"},null]")
        insertDetail(raw, "member-a", "duplicate-picture", 18L, "SYNCED", "[{\"id\":12,\"url\":\"a\"},{\"id\":12,\"url\":\"b\"}]")
        insertDetail(raw, "member-a", "empty", 15L, "SYNCED", "[]")
        insertDetail(raw, "member-a", "null", 16L, "SYNCED", null)
        raw.execSQL(
            "INSERT INTO simple_timer VALUES ('member-a', 'simple', 21, 'UPDATE_PENDING', 120, 'simple-create', " +
                "'simple-edit', 200, 'CREATE_PENDING', 'simple-flight', 100, 60, 'NETWORK_ERROR')",
        )
        raw.execSQL(
            "INSERT INTO custom_timer VALUES ('member-b', 'custom', 22, 'DELETE_PENDING', '현재', " +
                "'[{\"order\":0,\"time\":30,\"name\":\"운동\"}]', 'custom-create', 'custom-edit', 400, 'UPDATE_PENDING', " +
                "'custom-flight', 300, '이전', '[]', 'NETWORK_ERROR')",
        )
        if (version == 5) {
            raw.execSQL("INSERT INTO timer_snapshot VALUES ('member-a', 'simple', 1, 9)")
            raw.execSQL("INSERT INTO timer_snapshot VALUES ('member-b', 'custom', 1, 11)")
            raw.execSQL("INSERT INTO timer_snapshot VALUES ('member-a', 'custom', 1, 13)")
        }
    }

    private fun insertList(
        raw: SupportSQLiteDatabase,
        member: String,
        local: String,
        server: Long?,
        state: String,
    ) {
        raw.execSQL(
            "INSERT INTO exercise_list_item VALUES (?, ?, ?, ?, '2026-10-10', 2, '제목', 'WEIGHT', " +
                "'2026-10-09T23:50', '2026-10-10T00:10', '[\"https://example.test/thumb\"]')",
            arrayOf(member, local, server, state),
        )
    }

    private fun insertDetail(
        raw: SupportSQLiteDatabase,
        member: String,
        local: String,
        server: Long?,
        state: String,
        json: String?,
    ) {
        raw.execSQL(
            "INSERT INTO exercise_record_detail VALUES (?, ?, ?, ?, '제목', '상세 원문', 'WEIGHT', " +
                "'2026-10-09T23:50', '2026-10-10T00:10', '장소', ?)",
            arrayOf(member, local, server, state, json),
        )
    }

    private fun originals(
        raw: SupportSQLiteDatabase,
        version: Int,
    ): Map<String, List<List<String?>>> {
        val columns =
            linkedMapOf(
                "exercise_list_item" to "memberId, localId, serverId, syncState, date, sortOrder, title, personalType, startedAt, endedAt, thumbnailUrls",
                "exercise_record_detail" to "memberId, localId, serverId, syncState, title, detail, personalType, startedAt, endedAt, location, pictures",
                "exercise_count" to "*",
                "exercise_cache_query" to "*",
                "simple_timer" to "*",
                "custom_timer" to "*",
            )
        if (version == 5) columns["timer_snapshot"] = "*"
        return columns.mapValues { (table, fields) ->
            raw.query("SELECT $fields FROM $table ORDER BY 1, 2").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
                    }
                }
            }
        }
    }

    private fun openDatabase(): UndabangDatabase =
        Room.databaseBuilder(context, UndabangDatabase::class.java, name)
            .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .addMigrations(DatabaseModule.MIGRATION_4_5, DatabaseModule.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build().also { database = it }
}
