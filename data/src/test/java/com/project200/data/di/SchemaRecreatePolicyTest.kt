package com.project200.data.di

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.entity.ExerciseCountEntity
import com.project200.data.local.entity.ExerciseListItemEntity
import com.project200.data.local.entity.SimpleTimerEntity
import com.project200.data.local.entity.SyncState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * 출시 전 스키마 정책을 검증합니다.
 *
 * 2→3은 조회 마커만 추가하고 기존 데이터를 보존합니다.
 * 미지원 파일 버전은 재생성하고, 같은 버전은 그대로 다시 엽니다
 */
@RunWith(RobolectricTestRunner::class)
class SchemaRecreatePolicyTest {
    private val context = RuntimeEnvironment.getApplication()
    private val converters = UndabangTypeConverters(Moshi.Builder().add(KotlinJsonAdapterFactory()).build())

    private fun openDatabase(): UndabangDatabase = DatabaseModule.provideUndabangDatabase(context, converters)

    private val date = LocalDate.parse("2026-09-10")

    @Test
    fun `파일 버전이 코드와 어긋나면 재생성한다`() =
        runTest {
            // Given: 캐시가 쌓인 DB
            var database = openDatabase()
            val codeVersion = database.openHelper.readableDatabase.version
            database.exerciseCountDao().upsertAll(
                listOf(ExerciseCountEntity(memberId = "member-a", date = date, count = 2)),
            )
            assertThat(database.exerciseCountDao().getRange("member-a", date, date)).hasSize(1)
            database.close()

            // When: 파일 버전을 코드보다 앞선 값으로 바꿔 마이그레이션이 없는 상태를 만든다
            val path = context.getDatabasePath(UndabangDatabase.DATABASE_NAME).absolutePath
            val mismatched =
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                    raw.version = codeVersion + 1
                    raw.version
                }
            assertThat(mismatched).isEqualTo(codeVersion + 1)
            database = openDatabase()

            // Then: 예외 없이 열리고 캐시는 비어 있다
            assertThat(database.exerciseCountDao().getRange("member-a", date, date)).isEmpty()
            database.close()
        }

    @Test
    fun `같은 version이면 저장한 값이 유지된다`() =
        runTest {
            // Given
            var database = openDatabase()
            database.exerciseCountDao().upsertAll(
                listOf(ExerciseCountEntity(memberId = "member-a", date = date, count = 3)),
            )
            database.close()

            // When
            database = openDatabase()

            // Then: 재생성 정책이 정상 재열기까지 지우지는 않는다
            val counts = database.exerciseCountDao().getRange("member-a", date, date)
            assertThat(counts.single().count).isEqualTo(3)
            database.close()
        }

    @Test
    fun `3에서 4 마이그레이션은 전송 대기 행과 기존 캐시를 보존한다`() =
        runTest {
            var database = openDatabase()
            val pendingRecord =
                ExerciseListItemEntity(
                    memberId = "member-a",
                    localId = "pending-exercise",
                    serverId = null,
                    syncState = SyncState.CREATE_PENDING,
                    date = date,
                    sortOrder = 0,
                    title = "대기 운동",
                    personalType = "WEIGHT",
                    startedAt = date.atTime(10, 0),
                    endedAt = date.atTime(11, 0),
                    thumbnailUrls = null,
                )
            val pendingTimer =
                SimpleTimerEntity(
                    memberId = "member-a",
                    localId = "pending-timer",
                    serverId = null,
                    syncState = SyncState.CREATE_PENDING,
                    time = 60,
                    createRequestId = "timer-create",
                    pendingEditId = null,
                    editedAt = 1L,
                )
            database.exerciseRecordDao().upsertListItems(listOf(pendingRecord))
            database.timerDao().upsertSimpleTimer(pendingTimer)
            database.exerciseCountDao().upsertAll(listOf(ExerciseCountEntity("member-a", date, 2)))
            database.close()

            val path = context.getDatabasePath(UndabangDatabase.DATABASE_NAME).absolutePath
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                raw.execSQL(
                    "CREATE TABLE simple_timer_v3 (" +
                        "memberId TEXT NOT NULL, localId TEXT NOT NULL, serverId INTEGER, " +
                        "syncState TEXT NOT NULL, time INTEGER NOT NULL, createRequestId TEXT NOT NULL, " +
                        "pendingEditId TEXT, editedAt INTEGER NOT NULL, PRIMARY KEY(memberId, localId))",
                )
                raw.execSQL(
                    "INSERT INTO simple_timer_v3 " +
                        "SELECT memberId, localId, serverId, syncState, time, createRequestId, pendingEditId, editedAt " +
                        "FROM simple_timer",
                )
                raw.execSQL("DROP TABLE simple_timer")
                raw.execSQL("ALTER TABLE simple_timer_v3 RENAME TO simple_timer")
                raw.execSQL("CREATE INDEX index_simple_timer_memberId_serverId ON simple_timer(memberId, serverId)")
                raw.execSQL(
                    "CREATE TABLE custom_timer_v3 (" +
                        "memberId TEXT NOT NULL, localId TEXT NOT NULL, serverId INTEGER, " +
                        "syncState TEXT NOT NULL, name TEXT NOT NULL, steps TEXT NOT NULL, " +
                        "createRequestId TEXT NOT NULL, pendingEditId TEXT, editedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(memberId, localId))",
                )
                raw.execSQL("DROP TABLE custom_timer")
                raw.execSQL("ALTER TABLE custom_timer_v3 RENAME TO custom_timer")
                raw.execSQL("CREATE INDEX index_custom_timer_memberId_serverId ON custom_timer(memberId, serverId)")
                raw.version = 3
            }
            database = openDatabase()

            assertThat(database.openHelper.readableDatabase.version).isEqualTo(4)
            assertThat(database.exerciseRecordDao().getListByDate("member-a", date)).containsExactly(pendingRecord)
            assertThat(database.timerDao().getSimpleTimer("member-a", "pending-timer")).isEqualTo(pendingTimer)
            assertThat(database.exerciseCountDao().getRange("member-a", date, date))
                .containsExactly(ExerciseCountEntity("member-a", date, 2))
            database.close()
        }
}
