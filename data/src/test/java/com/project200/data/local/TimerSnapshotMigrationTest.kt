package com.project200.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.datasource.TimerLocalDataSource
import com.project200.data.di.DatabaseModule
import com.project200.data.impl.RoomSessionDataCleaner
import com.project200.data.local.entity.SyncState
import com.project200.data.local.entity.TimerSnapshotEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TimerSnapshotMigrationTest {
    private val context = RuntimeEnvironment.getApplication()
    private val name = "timer-snapshot-migration.db"
    private var database: UndabangDatabase? = null
    private val preferences = mockk<PreferenceManager>()

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `실제 스키마 4에서 현재 버전으로 이동해 ID 대기 전송 영수증과 기존 조회 이력을 보존한다`() =
        runTest {
            createVersionFour()
            val migrated = openDatabase()
            val simple = migrated.timerDao().getSimpleTimer("member-a", "simple-local")!!
            assertThat(simple.serverId).isEqualTo(7L)
            assertThat(simple.syncState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(simple.time).isEqualTo(120)
            assertThat(simple.createRequestId).isEqualTo("simple-create")
            assertThat(simple.pendingEditId).isEqualTo("simple-edit")
            assertThat(simple.editedAt).isEqualTo(200L)
            assertThat(simple.inFlightState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(simple.inFlightEditId).isEqualTo("simple-flight")
            assertThat(simple.inFlightEditedAt).isEqualTo(100L)
            assertThat(simple.inFlightTime).isEqualTo(60)
            assertThat(simple.lastSyncError).isEqualTo("NETWORK_ERROR")
            val custom = migrated.timerDao().getCustomTimer("member-b", "custom-local")!!
            assertThat(custom.serverId).isEqualTo(8L)
            assertThat(custom.syncState).isEqualTo(SyncState.DELETE_PENDING)
            assertThat(custom.name).isEqualTo("삭제 대기")
            assertThat(custom.steps).isEmpty()
            assertThat(custom.createRequestId).isEqualTo("custom-create")
            assertThat(custom.pendingEditId).isEqualTo("custom-edit")
            assertThat(custom.editedAt).isEqualTo(400L)
            assertThat(custom.inFlightState).isEqualTo(SyncState.UPDATE_PENDING)
            assertThat(custom.inFlightEditId).isEqualTo("custom-flight")
            assertThat(custom.inFlightEditedAt).isEqualTo(300L)
            assertThat(custom.inFlightName).isEqualTo("이전 이름")
            assertThat(custom.inFlightSteps).isEmpty()
            assertThat(custom.lastSyncError).isEqualTo("NETWORK_ERROR")
            assertThat(migrated.timerDao().getSnapshot("member-a", TimerSnapshotEntity.SIMPLE))
                .isEqualTo(TimerSnapshotEntity("member-a", TimerSnapshotEntity.SIMPLE, true, 0))
            assertThat(migrated.timerDao().getSnapshot("member-b", TimerSnapshotEntity.CUSTOM)?.hasSnapshot).isTrue()
            assertThat(migrated.timerDao().getSnapshot("member-a", TimerSnapshotEntity.CUSTOM)).isNull()
            assertThat(migrated.exerciseRecordDao().hasQuery("member-a", "list", java.time.LocalDate.of(2026, 10, 10))).isTrue()
        }

    @Test
    fun `빈 스냅샷은 재개방 후에도 남고 세션 정리는 메타데이터까지 지운다`() =
        runTest {
            createVersionFour(withTimers = false)
            every { preferences.getMemberId() } returns "member-a"
            var db = openDatabase()
            var local = TimerLocalDataSource(db, db.timerDao(), preferences)
            assertThat(local.getSimpleSnapshot("member-a")).isNull()
            assertThat(local.getCustomSnapshot("member-a")).isNull()
            local.replaceSyncedSimpleTimers(emptyList(), "member-a", 0L)
            val last = local.createCustomTimer("미전송", emptyList())!!
            local.deleteCustomTimer(last)
            db.close()
            db = openDatabase()
            local = TimerLocalDataSource(db, db.timerDao(), preferences)
            assertThat(local.getSimpleSnapshot("member-a")).isEmpty()
            assertThat(local.getCustomSnapshot("member-a")).isEmpty()
            assertThat(local.snapshotRevision("member-a", TimerSnapshotEntity.CUSTOM)).isEqualTo(2L)
            RoomSessionDataCleaner(db, UnconfinedTestDispatcher(), ExerciseImageFileStore(context)).clearAll()
            assertThat(local.getSimpleSnapshot("member-a")).isNull()
            assertThat(local.getCustomSnapshot("member-a")).isNull()
            db.close()
            db = openDatabase()
            assertThat(db.timerDao().getSnapshot("member-a", TimerSnapshotEntity.SIMPLE)).isNull()
        }

    private fun openDatabase(): UndabangDatabase =
        Room.databaseBuilder(context, UndabangDatabase::class.java, name)
            .addTypeConverter(UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .addMigrations(DatabaseModule.MIGRATION_4_5, DatabaseModule.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun createVersionFour(withTimers: Boolean = true) {
        context.deleteDatabase(name)
        val schema =
            javaClass.classLoader!!.getResourceAsStream("com.project200.data.local.UndabangDatabase/4.json")!!
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val configuration =
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(4) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val entities = schema.getJSONArray("entities")
                            for (index in 0 until entities.length()) {
                                val entity = entities.getJSONObject(index)
                                val tableName = entity.getString("tableName")
                                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                                val indices = entity.getJSONArray("indices")
                                for (position in 0 until indices.length()) {
                                    db.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", tableName))
                                }
                            }
                            val setupQueries = schema.getJSONArray("setupQueries")
                            for (index in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(index))
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = error("예상하지 않은 테스트 DB 업그레이드")
                    },
                )
                .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            if (withTimers) {
                db.execSQL(
                    "INSERT INTO simple_timer VALUES " +
                        "('member-a', 'simple-local', 7, 'UPDATE_PENDING', 120, 'simple-create', 'simple-edit', 200, " +
                        "'UPDATE_PENDING', 'simple-flight', 100, 60, 'NETWORK_ERROR')",
                )
                db.execSQL(
                    "INSERT INTO custom_timer VALUES " +
                        "('member-b', 'custom-local', 8, 'DELETE_PENDING', '삭제 대기', '[]', 'custom-create', 'custom-edit', 400, " +
                        "'UPDATE_PENDING', 'custom-flight', 300, '이전 이름', '[]', 'NETWORK_ERROR')",
                )
            }
            db.execSQL("INSERT INTO exercise_cache_query VALUES ('member-a', 'list', '2026-10-10')")
        }
    }
}
