package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseCount
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.repository.ExerciseRecordRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class MaestroFixtureCleanerTest {
    private val exerciseRecordRepository = mockk<ExerciseRecordRepository>()
    private val cleaner = MaestroFixtureCleaner(exerciseRecordRepository)
    private val today = LocalDate.of(2026, 10, 12)
    private val title = "maestro-e2e-0123456789abcdef0123456789abcdef"

    @Test
    fun `일반 기록은 보존하고 정확한 예약 제목만 삭제한다`() =
        runTest {
            // Given
            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns
                success(listOf(ExerciseCount(today, 4)))
            coEvery { exerciseRecordRepository.getExerciseRecordList(today) } returns
                success(
                    listOf(
                        item(1, title),
                        item(2, "$title-edited"),
                        item(3, "daily exercise"),
                        item(4, "$title-extra"),
                    ),
                )
            coEvery { exerciseRecordRepository.getExerciseDetail("1") } returns success(detail(title))
            coEvery { exerciseRecordRepository.getExerciseDetail("2") } returns success(detail("$title-edited"))
            coEvery { exerciseRecordRepository.deleteExerciseRecord(any()) } returns success(Unit)

            // When
            cleaner.clean(today, today)

            // Then
            coVerify(exactly = 1) { exerciseRecordRepository.deleteExerciseRecord("1") }
            coVerify(exactly = 1) { exerciseRecordRepository.deleteExerciseRecord("2") }
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord("3") }
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord("4") }
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseDetail("3") }
        }

    @Test
    fun `삭제 직전에 일반 제목으로 바뀐 기록은 보존한다`() =
        runTest {
            // Given
            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns
                success(listOf(ExerciseCount(today, 1)))
            coEvery { exerciseRecordRepository.getExerciseRecordList(today) } returns success(listOf(item(1, title)))
            coEvery { exerciseRecordRepository.getExerciseDetail("1") } returns success(detail("my real exercise"))

            // When
            cleaner.clean(today, today)

            // Then
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }
        }

    @Test
    fun `다음 월 조회가 실패하면 이전 월 후보도 삭제하지 않는다`() =
        runTest {
            // Given
            val start = LocalDate.of(2026, 9, 30)
            coEvery { exerciseRecordRepository.getExerciseCountByRange(start, start) } returns
                success(listOf(ExerciseCount(start, 1)))
            coEvery { exerciseRecordRepository.getExerciseRecordList(start) } returns
                success(listOf(item(1, title, start)))
            coEvery {
                exerciseRecordRepository.getExerciseCountByRange(LocalDate.of(2026, 10, 1), today)
            } returns serverError()

            // When
            expectFailure("fixture_read_failed") { cleaner.clean(start, today) }

            // Then
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseDetail(any()) }
        }

    @Test
    fun `서버가 조회 구간 밖 날짜를 반환하면 삭제하지 않는다`() =
        runTest {
            // Given
            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns
                success(listOf(ExerciseCount(today.minusDays(1), 1)))

            // When
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }

            // Then
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseRecordList(any()) }
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }
        }

    @Test
    fun `기록이 없는 날짜는 목록을 조회하지 않는다`() =
        runTest {
            // Given
            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns
                success(listOf(ExerciseCount(today, 0)))

            // When
            cleaner.clean(today, today)

            // Then
            coVerify(exactly = 0) { exerciseRecordRepository.getExerciseRecordList(any()) }
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }
        }

    @Test
    fun `삭제 응답이 실패하면 정리 성공을 반환하지 않는다`() =
        runTest {
            // Given
            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns
                success(listOf(ExerciseCount(today, 1)))
            coEvery { exerciseRecordRepository.getExerciseRecordList(today) } returns success(listOf(item(1, title)))
            coEvery { exerciseRecordRepository.getExerciseDetail("1") } returns success(detail(title))
            coEvery { exerciseRecordRepository.deleteExerciseRecord("1") } returns serverError()

            // When, Then
            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }
        }

    @Test
    fun `오프라인 스냅샷이나 갱신 실패를 근거로 기록을 지우지 않는다`() =
        runTest {
            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns
                BaseResult.Success(listOf(ExerciseCount(today, 1)), isCachedSnapshot = true)
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }

            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns
                BaseResult.Success(listOf(ExerciseCount(today, 1)), refreshError = BaseResult.Error("401", "인증 실패"))
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }
        }

    @Test
    fun `상세에 대기 변경이 남아 있으면 서버 삭제를 실행하지 않는다`() =
        runTest {
            coEvery { exerciseRecordRepository.getExerciseCountByRange(today, today) } returns success(listOf(ExerciseCount(today, 1)))
            coEvery { exerciseRecordRepository.getExerciseRecordList(today) } returns success(listOf(item(1, title)))
            coEvery { exerciseRecordRepository.getExerciseDetail("1") } returns success(detail(title).copy(syncPending = true, canModify = false))
            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }
            coVerify(exactly = 0) { exerciseRecordRepository.deleteExerciseRecord(any()) }
        }

    private fun item(
        id: Long,
        itemTitle: String,
        date: LocalDate = today,
    ) = ExerciseListItem(id.toString(), itemTitle, "OTHER", date.atTime(9, 0), date.atTime(10, 0), null)

    private fun detail(recordTitle: String) = ExerciseRecord(recordTitle, "", "OTHER", today.atTime(9, 0), today.atTime(10, 0), "", null)

    private fun <T> success(data: T): BaseResult<T> = BaseResult.Success(data)

    private fun serverError(): BaseResult<Nothing> = BaseResult.Error(errorCode = "SERVER_ERROR", message = "")

    private suspend fun expectFailure(
        reason: String,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
            throw AssertionError("실패 응답이 허용되었습니다")
        } catch (failure: MaestroSetupFailure) {
            assertThat(failure.reason).isEqualTo(reason)
        }
    }
}
