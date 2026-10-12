package com.project200.undabang.e2e

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetExerciseCountByRangeDTO
import com.project200.data.dto.GetExerciseRecordData
import com.project200.data.dto.GetExerciseRecordListDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class MaestroFixtureCleanerTest {
    private val apiService = mockk<ApiService>()
    private val cleaner = MaestroFixtureCleaner(apiService)
    private val today = LocalDate.of(2026, 10, 12)
    private val title = "maestro-e2e-0123456789abcdef0123456789abcdef"

    @Test
    fun `일반 기록은 보존하고 정확한 예약 제목만 삭제한다`() =
        runTest {
            coEvery { apiService.getExerciseCountsByRange(today, today) } returns
                success(listOf(GetExerciseCountByRangeDTO(today, 4)))
            coEvery { apiService.getExerciseList(today) } returns
                success(listOf(record(1, title), record(2, "$title-edited"), record(3, "daily exercise"), record(4, "$title-extra")))
            coEvery { apiService.getExerciseRecordDetail(1) } returns success(detail(title))
            coEvery { apiService.getExerciseRecordDetail(2) } returns success(detail("$title-edited"))
            coEvery { apiService.deleteExerciseRecord(any()) } returns success<Unit?>(null)

            cleaner.clean(today, today)

            coVerify(exactly = 1) { apiService.deleteExerciseRecord(1) }
            coVerify(exactly = 1) { apiService.deleteExerciseRecord(2) }
            coVerify(exactly = 0) { apiService.deleteExerciseRecord(3) }
            coVerify(exactly = 0) { apiService.deleteExerciseRecord(4) }
            coVerify(exactly = 0) { apiService.getExerciseRecordDetail(3) }
        }

    @Test
    fun `삭제 직전에 일반 제목으로 바뀐 기록은 보존한다`() =
        runTest {
            coEvery { apiService.getExerciseCountsByRange(today, today) } returns success(listOf(GetExerciseCountByRangeDTO(today, 1)))
            coEvery { apiService.getExerciseList(today) } returns success(listOf(record(1, title)))
            coEvery { apiService.getExerciseRecordDetail(1) } returns success(detail("my real exercise"))

            cleaner.clean(today, today)

            coVerify(exactly = 0) { apiService.deleteExerciseRecord(any()) }
        }

    @Test
    fun `다음 월 조회가 실패하면 이전 월 후보도 삭제하지 않는다`() =
        runTest {
            val start = LocalDate.of(2026, 9, 30)
            coEvery { apiService.getExerciseCountsByRange(start, start) } returns success(listOf(GetExerciseCountByRangeDTO(start, 1)))
            coEvery { apiService.getExerciseList(start) } returns success(listOf(record(1, title, start)))
            coEvery { apiService.getExerciseCountsByRange(LocalDate.of(2026, 10, 1), today) } returns
                BaseResponse(false, "SERVER_ERROR", "", null)

            expectFailure("fixture_read_failed") { cleaner.clean(start, today) }

            coVerify(exactly = 0) { apiService.deleteExerciseRecord(any()) }
            coVerify(exactly = 0) { apiService.getExerciseRecordDetail(any()) }
        }

    @Test
    fun `서버가 조회 구간 밖 날짜를 반환하면 삭제하지 않는다`() =
        runTest {
            coEvery { apiService.getExerciseCountsByRange(today, today) } returns success(listOf(GetExerciseCountByRangeDTO(today.minusDays(1), 1)))

            expectFailure("fixture_read_failed") { cleaner.clean(today, today) }

            coVerify(exactly = 0) { apiService.getExerciseList(any()) }
            coVerify(exactly = 0) { apiService.deleteExerciseRecord(any()) }
        }

    @Test
    fun `기록이 없는 날짜는 목록을 조회하지 않는다`() =
        runTest {
            coEvery { apiService.getExerciseCountsByRange(today, today) } returns success(listOf(GetExerciseCountByRangeDTO(today, 0)))

            cleaner.clean(today, today)

            coVerify(exactly = 0) { apiService.getExerciseList(any()) }
            coVerify(exactly = 0) { apiService.deleteExerciseRecord(any()) }
        }

    @Test
    fun `삭제 응답이 실패하면 정리 성공을 반환하지 않는다`() =
        runTest {
            coEvery { apiService.getExerciseCountsByRange(today, today) } returns success(listOf(GetExerciseCountByRangeDTO(today, 1)))
            coEvery { apiService.getExerciseList(today) } returns success(listOf(record(1, title)))
            coEvery { apiService.getExerciseRecordDetail(1) } returns success(detail(title))
            coEvery { apiService.deleteExerciseRecord(1) } returns BaseResponse(false, "SERVER_ERROR", "", null)

            expectFailure("fixture_delete_failed") { cleaner.clean(today, today) }
        }

    private fun record(
        id: Long,
        recordTitle: String,
        date: LocalDate = today,
    ) = GetExerciseRecordListDto(id, recordTitle, "OTHER", date.atTime(9, 0), date.atTime(10, 0), null)

    private fun detail(recordTitle: String) =
        GetExerciseRecordData(recordTitle, "", "OTHER", today.atTime(9, 0), today.atTime(10, 0), "", null)

    private fun <T> success(data: T) = BaseResponse(true, "SUCCESS", "", data)

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
