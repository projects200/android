package com.project200.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.project200.data.datasource.ExerciseRecordLocalDataSource
import com.project200.data.di.DatabaseModule
import com.project200.data.local.entity.ExerciseImageState
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.ExerciseSyncError
import com.project200.data.local.entity.SyncState
import com.project200.domain.model.ExerciseListItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
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
    fun `실제 스키마 4는 모든 운동 원문과 타이머 전송 상태를 보존하고 재개방한다`() = runTest { assertMigration(4) }

    @Test
    fun `실제 스키마 5는 모든 운동 원문과 타이머 조회 이력을 보존하고 재개방한다`() = runTest { assertMigration(5) }

    @Test
    fun `스키마 5 생성 성공 직후 목록 조회가 없으면 확정 날짜 없이 원문을 보존하고 재개방한다`() =
        runTest {
            var before = emptyMap<String, List<List<String?>>>()
            createHistoricalDatabase(context, name, 5) { raw ->
                seedCreatedRecord(raw)
                before = originals(raw, 5)
            }
            repeat(2) {
                val db = openDatabase()
                val records = db.exerciseRecordDao()
                val item = records.getListItem("member-a", "created")!!
                val detail = records.getDetailByLocalId("member-a", "created")!!
                assertThat(originals(db.openHelper.writableDatabase, 5)).isEqualTo(before)
                assertThat(item.date).isEqualTo(date)
                assertThat(item.startedAt).isEqualTo(date.atTime(10, 0))
                assertThat(detail.startedAt).isEqualTo(item.startedAt)
                assertThat(item.syncState).isEqualTo(SyncState.SYNCED)
                assertThat(detail.syncState).isEqualTo(SyncState.SYNCED)
                assertThat(item.confirmedDate).isNull()
                assertThat(detail.confirmedDate).isNull()
                assertThat(detail.detail).isEqualTo("상세 원문")
                assertThat(detail.pictures?.map { it.id }).containsExactly(12L, 11L).inOrder()
                assertThat(records.hasQuery("member-a", "list", date)).isFalse()
                assertThat(db.exerciseSyncDao().getByMember("member-a")).isEmpty()
                db.close()
            }
        }

    @Test
    fun `스키마 5 수정 성공으로 이전에 조회한 날짜로 옮겨도 확정 날짜를 추정하지 않는다`() =
        runTest {
            val updatedDate = date.plusDays(1)
            val startedAt = updatedDate.atTime(10, 0)
            val endedAt = updatedDate.atTime(11, 0)
            var before = emptyMap<String, List<List<String?>>>()
            createHistoricalDatabase(context, name, 5) { raw ->
                seedCreatedRecord(raw)
                raw.execSQL(
                    "INSERT INTO exercise_cache_query VALUES (?, ?, ?)",
                    arrayOf("member-a", "list", updatedDate.toString()),
                )
                raw.execSQL(
                    "UPDATE exercise_list_item SET date = ?, title = ?, startedAt = ?, endedAt = ? " +
                        "WHERE memberId = ? AND localId = ?",
                    arrayOf(updatedDate.toString(), "수정 제목", startedAt.toString(), endedAt.toString(), "member-a", "created"),
                )
                raw.execSQL(
                    "UPDATE exercise_record_detail SET title = ?, detail = ?, startedAt = ?, endedAt = ? " +
                        "WHERE memberId = ? AND localId = ?",
                    arrayOf("수정 제목", "수정 원문", startedAt.toString(), endedAt.toString(), "member-a", "created"),
                )
                raw.execSQL(
                    "UPDATE exercise_count SET count = ? WHERE memberId = ? AND date = ?",
                    arrayOf(0, "member-a", date.toString()),
                )
                raw.execSQL(
                    "INSERT INTO exercise_count VALUES (?, ?, ?)",
                    arrayOf("member-a", updatedDate.toString(), 1),
                )
                before = originals(raw, 5)
            }
            repeat(2) {
                val db = openDatabase()
                val records = db.exerciseRecordDao()
                val item = records.getListItem("member-a", "created")!!
                val detail = records.getDetailByLocalId("member-a", "created")!!
                assertThat(originals(db.openHelper.writableDatabase, 5)).isEqualTo(before)
                assertThat(item.date).isEqualTo(updatedDate)
                assertThat(item.startedAt).isEqualTo(startedAt)
                assertThat(item.endedAt).isEqualTo(endedAt)
                assertThat(detail.startedAt).isEqualTo(startedAt)
                assertThat(detail.endedAt).isEqualTo(endedAt)
                assertThat(item.title).isEqualTo("수정 제목")
                assertThat(detail.title).isEqualTo("수정 제목")
                assertThat(detail.detail).isEqualTo("수정 원문")
                assertThat(item.confirmedDate).isNull()
                assertThat(detail.confirmedDate).isNull()
                assertThat(records.hasQuery("member-a", "list", updatedDate)).isTrue()
                assertThat(db.exerciseSyncDao().getByMember("member-a")).isEmpty()
                db.close()
            }
        }

    @Test
    fun `업그레이드 뒤 실제 서버 목록 수신만 두 투영의 날짜를 확정하고 재개방해도 보존한다`() =
        runTest {
            createHistoricalDatabase(context, name, 5) { raw -> seedCreatedRecord(raw) }
            var db = openDatabase()
            val originalItem = db.exerciseRecordDao().getListItem("member-a", "created")!!
            val originalDetail = db.exerciseRecordDao().getDetailByLocalId("member-a", "created")!!
            val originalImages = db.exerciseImageDao().getByRecord("member-a", "created")
            assertThat(originalItem.confirmedDate).isNull()
            assertThat(originalDetail.confirmedDate).isNull()
            val queryDate = date.plusDays(1)
            assertThat(queryDate).isNotEqualTo(originalItem.startedAt.toLocalDate())
            val preferences = mockk<PreferenceManager>()
            every { preferences.getMemberId() } returns "member-a"
            val local = ExerciseRecordLocalDataSource(db, db.exerciseCountDao(), db.exerciseRecordDao(), preferences)
            local.replaceSyncedListByDate(
                queryDate,
                listOf(
                    ExerciseListItem(
                        recordId = "7",
                        title = originalItem.title,
                        type = originalItem.personalType,
                        startTime = originalItem.startedAt,
                        endTime = originalItem.endedAt,
                        imageUrl = originalItem.thumbnailUrls,
                    ),
                ),
            )
            val expectedItem = originalItem.copy(date = queryDate, confirmedDate = queryDate)
            val expectedDetail = originalDetail.copy(confirmedDate = queryDate)
            assertThat(db.exerciseRecordDao().getListItem("member-a", "created")).isEqualTo(expectedItem)
            assertThat(db.exerciseRecordDao().getDetailByLocalId("member-a", "created")).isEqualTo(expectedDetail)
            assertThat(db.exerciseRecordDao().getListByDate("member-a", date)).isEmpty()
            assertThat(db.exerciseRecordDao().getListByDate("member-a", queryDate)).containsExactly(expectedItem)
            assertThat(db.exerciseImageDao().getByRecord("member-a", "created")).isEqualTo(originalImages)
            assertThat(db.exerciseRecordDao().hasQuery("member-a", "list", queryDate)).isTrue()
            assertThat(db.exerciseSyncDao().getByMember("member-a")).isEmpty()
            db.close()
            db = openDatabase()
            assertThat(db.exerciseRecordDao().getListItem("member-a", "created")).isEqualTo(expectedItem)
            assertThat(db.exerciseRecordDao().getDetailByLocalId("member-a", "created")).isEqualTo(expectedDetail)
            assertThat(db.exerciseRecordDao().getListByDate("member-a", date)).isEmpty()
            assertThat(db.exerciseRecordDao().getListByDate("member-a", queryDate)).containsExactly(expectedItem)
            assertThat(db.exerciseImageDao().getByRecord("member-a", "created")).isEqualTo(originalImages)
            assertThat(db.exerciseRecordDao().hasQuery("member-a", "list", queryDate)).isTrue()
            assertThat(db.exerciseSyncDao().getByMember("member-a")).isEmpty()
        }

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
        // 기존 목록 날짜와 시작일은 보존하되 개별 행의 조회 출처는 추정하지 않습니다
        assertThat(synced.startedAt.toLocalDate()).isEqualTo(date.minusDays(1))
        assertThat(synced.date).isEqualTo(date)
        assertThat(synced.confirmedDate).isNull()
        assertThat(synced.serverVersion).isNull()
        assertThat(records.getDetailByLocalId("member-a", "synced")?.confirmedDate).isNull()
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

    private fun seedCreatedRecord(raw: SupportSQLiteDatabase) {
        val startedAt = date.atTime(10, 0).toString()
        val endedAt = date.atTime(11, 0).toString()
        raw.execSQL(
            "INSERT INTO exercise_list_item VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                "member-a",
                "created",
                7L,
                "SYNCED",
                date.toString(),
                0,
                "제목",
                "WEIGHT",
                startedAt,
                endedAt,
                "[\"https://example.test/twelve\",\"https://example.test/eleven\"]",
            ),
        )
        raw.execSQL(
            "INSERT INTO exercise_record_detail VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("member-a", "created", 7L, "SYNCED", "제목", "상세 원문", "WEIGHT", startedAt, endedAt, "장소", pictures),
        )
        raw.execSQL("INSERT INTO exercise_count VALUES (?, ?, ?)", arrayOf("member-a", date.toString(), 1))
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
                "exercise_list_item" to "memberId, localId, serverId, syncState, date, sortOrder, title, " +
                    "personalType, startedAt, endedAt, thumbnailUrls",
                "exercise_record_detail" to "memberId, localId, serverId, syncState, title, detail, personalType, " +
                    "startedAt, endedAt, location, pictures",
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
