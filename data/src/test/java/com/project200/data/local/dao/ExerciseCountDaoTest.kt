package com.project200.data.local.dao

import com.google.common.truth.Truth.assertThat
import com.project200.data.local.UndabangDatabase
import com.project200.data.local.createInMemoryDatabase
import com.project200.data.local.entity.ExerciseCountEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ExerciseCountDaoTest {
    private lateinit var database: UndabangDatabase
    private lateinit var dao: ExerciseCountDao

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        dao = database.exerciseCountDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun count(
        memberId: String,
        date: String,
        count: Int = 1,
    ) = ExerciseCountEntity(memberId = memberId, date = LocalDate.parse(date), count = count)

    @Test
    fun `범위 조회 - 시작일과 종료일을 포함한다`() =
        runTest {
            // Given
            dao.upsertAll(
                listOf(
                    count("member-a", "2026-08-31"),
                    count("member-a", "2026-09-01"),
                    count("member-a", "2026-09-15"),
                    count("member-a", "2026-09-30"),
                    count("member-a", "2026-10-01"),
                ),
            )

            // When
            val result = dao.getRange("member-a", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))

            // Then: 경계 날짜는 들어오고 바깥 날짜는 빠진다
            assertThat(result.map { it.date.toString() })
                .containsExactly("2026-09-01", "2026-09-15", "2026-09-30")
                .inOrder()
        }

    @Test
    fun `범위 조회 - 다른 계정의 행은 나오지 않는다`() =
        runTest {
            // Given
            dao.upsertAll(
                listOf(
                    count("member-a", "2026-09-10", count = 2),
                    count("member-b", "2026-09-10", count = 5),
                ),
            )

            // When
            val result = dao.getRange("member-a", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))

            // Then
            assertThat(result).hasSize(1)
            assertThat(result.single().count).isEqualTo(2)
        }

    @Test
    fun `범위 삭제 - 구간 밖과 다른 계정은 남는다`() =
        runTest {
            // Given
            dao.upsertAll(
                listOf(
                    count("member-a", "2026-08-31"),
                    count("member-a", "2026-09-01"),
                    count("member-a", "2026-09-30"),
                    count("member-a", "2026-10-01"),
                    count("member-b", "2026-09-15"),
                ),
            )

            // When
            dao.deleteRange("member-a", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))

            // Then
            val remainingA =
                dao.getRange("member-a", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))
            assertThat(remainingA.map { it.date.toString() }).containsExactly("2026-08-31", "2026-10-01")

            val remainingB =
                dao.getRange("member-b", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))
            assertThat(remainingB).hasSize(1)
        }

    @Test
    fun `저장 - 같은 계정과 날짜는 개수를 덮어쓴다`() =
        runTest {
            // Given
            dao.upsertAll(listOf(count("member-a", "2026-09-10", count = 1)))

            // When
            dao.upsertAll(listOf(count("member-a", "2026-09-10", count = 4)))

            // Then: PK가 (memberId, date)라 행이 늘지 않는다
            val result = dao.getRange("member-a", LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-10"))
            assertThat(result).hasSize(1)
            assertThat(result.single().count).isEqualTo(4)
        }
}
