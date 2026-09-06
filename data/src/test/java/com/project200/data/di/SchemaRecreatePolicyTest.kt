package com.project200.data.di

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.UndabangTypeConverters
import com.project200.data.local.entity.ExerciseCountEntity
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
 * 파일의 version을 낮춰 구버전 DB를 만든 뒤 다시 열어, 마이그레이션 없이도
 * 크래시하지 않고 재생성되는지 봅니다
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
}
