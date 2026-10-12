package com.project200.undabang.e2e

import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import java.time.LocalDate
import javax.inject.Inject

internal class MaestroFixtureCleaner
    @Inject
    constructor(
        private val apiService: ApiService,
    ) {
        suspend fun clean(
            fixtureStartDate: LocalDate,
            today: LocalDate,
        ) {
            // 계정 확인은 호출자가 실제 registration-status 응답으로 완료한 뒤에만 들어옵니다
            val candidates = linkedSetOf<Long>()
            for (range in MaestroFixturePolicy.monthlyRanges(fixtureStartDate, today)) {
                val counts =
                    requireData(apiService.getExerciseCountsByRange(range.start, range.endInclusive))
                val seenDates = mutableSetOf<LocalDate>()
                for (count in counts) {
                    if (count.date !in range || count.exerciseCount < 0 || !seenDates.add(count.date)) {
                        throw MaestroSetupFailure("fixture_read_failed")
                    }
                    if (count.exerciseCount == 0) continue
                    val records = requireData(apiService.getExerciseList(count.date))
                    for (record in records) {
                        if (record.exerciseId <= 0 || record.exerciseStartedAt.toLocalDate() != count.date) {
                            throw MaestroSetupFailure("fixture_read_failed")
                        }
                        if (MaestroFixturePolicy.ownsTitle(record.exerciseTitle)) {
                            candidates.add(record.exerciseId)
                        }
                    }
                }
            }

            // 모든 월의 조회가 성공한 뒤 삭제하며, 조회 후 일반 제목으로 바뀐 기록은 보존합니다
            for (exerciseId in candidates) {
                val record = requireData(apiService.getExerciseRecordDetail(exerciseId))
                if (record.exerciseStartedAt.toLocalDate() !in fixtureStartDate..today) {
                    throw MaestroSetupFailure("fixture_read_failed")
                }
                if (!MaestroFixturePolicy.ownsTitle(record.exerciseTitle)) continue
                val deleted = apiService.deleteExerciseRecord(exerciseId)
                if (!deleted.succeed) throw MaestroSetupFailure("fixture_delete_failed")
            }
        }

        private fun <T : Any> requireData(response: BaseResponse<T>): T {
            if (!response.succeed) throw MaestroSetupFailure("fixture_read_failed")
            return response.data ?: throw MaestroSetupFailure("fixture_read_failed")
        }
    }
