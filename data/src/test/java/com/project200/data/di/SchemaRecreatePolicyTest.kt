package com.project200.data.di

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.createHistoricalDatabase
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseRecoveryState
import com.project200.data.local.entity.SyncState
import com.project200.data.local.entity.TimerSnapshotEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SchemaRecreatePolicyTest {
    private val context = RuntimeEnvironment.getApplication()
    private val converters = UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build())
    private val name = UndabangDatabase.DATABASE_NAME
    private val date = LocalDate.parse("2026-09-10")
    private var database: UndabangDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    private fun openDatabase(): UndabangDatabase = DatabaseModule.provideUndabangDatabase(context, converters).also { database = it }

    @Test
    fun `지원하지 않는 과거 버전은 열기에 실패해도 원본과 파일 버전을 보존한다`() {
        createHistoricalDatabase(context, name, 1) {
            it.execSQL("INSERT INTO exercise_count VALUES ('member-a', '2026-09-10', 7)")
        }
        assertThrows(IllegalStateException::class.java) { openDatabase().openHelper.writableDatabase }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertThat(raw.version).isEqualTo(1)
            raw.rawQuery("SELECT count FROM exercise_count WHERE memberId = 'member-a'", null).use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getInt(0)).isEqualTo(7)
            }
        }
    }

    @Test
    fun `미래 버전도 다운그레이드나 재생성 없이 원본을 보존한다`() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { raw ->
            raw.execSQL("CREATE TABLE original_data (value TEXT NOT NULL)")
            raw.execSQL("INSERT INTO original_data VALUES ('preserved')")
            raw.version = 7
        }
        assertThrows(IllegalStateException::class.java) { openDatabase().openHelper.writableDatabase }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            assertThat(raw.version).isEqualTo(7)
            raw.rawQuery("SELECT value FROM original_data", null).use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("preserved")
            }
        }
    }

    @Test
    fun `같은 버전은 저장한 값을 그대로 다시 연다`() =
        runTest {
            var db = openDatabase()
            db.exerciseCountDao().upsertAll(listOf(ExerciseCountEntity("member-a", date, 3)))
            db.close()
            db = openDatabase()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(6)
            assertThat(db.exerciseCountDao().getRange("member-a", date, date))
                .containsExactly(ExerciseCountEntity("member-a", date, 3))
        }

    @Test
    fun `실제 스키마 2의 지원 경로는 모든 추가 마이그레이션을 거쳐 대기 원본을 보존한다`() =
        runTest {
            createHistoricalDatabase(context, name, 2) { raw ->
                raw.execSQL("INSERT INTO exercise_count VALUES ('member-a', '2026-09-10', 2)")
                raw.execSQL(
                    "INSERT INTO exercise_list_item VALUES " +
                        "('member-a', 'pending', NULL, 'CREATE_PENDING', '2026-09-10', 0, '대기 운동', 'WEIGHT', " +
                        "'2026-09-10T10:00', '2026-09-10T11:00', NULL)",
                )
                raw.execSQL("INSERT INTO simple_timer VALUES ('member-a', 'timer', NULL, 'CREATE_PENDING', 60, 'create', NULL, 1)")
            }
            val db = openDatabase()
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(6)
            assertThat(db.exerciseRecordDao().getListItem("member-a", "pending")?.title).isEqualTo("대기 운동")
            assertThat(db.exerciseSyncDao().get("member-a", "pending")?.recoveryState).isEqualTo(ExerciseRecoveryState.RECOVERY_REQUIRED)
            assertThat(db.exerciseSyncDao().getSendable("member-a")).isEmpty()
            val timer = db.timerDao().getSimpleTimer("member-a", "timer")!!
            assertThat(timer.syncState).isEqualTo(SyncState.CREATE_PENDING)
            assertThat(timer.createRequestId).isEqualTo("create")
            assertThat(timer.inFlightState).isNull()
            assertThat(db.timerDao().getSnapshot("member-a", TimerSnapshotEntity.SIMPLE)?.hasSnapshot).isTrue()
            assertThat(db.exerciseCountDao().getRange("member-a", date, date).single().count).isEqualTo(2)
            db.close()
            assertThat(openDatabase().exerciseRecordDao().getListItem("member-a", "pending")?.serverId).isNull()
        }
}
