package com.project200.undabang.e2e

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.ExerciseRecordRepository
import java.time.LocalDate
import javax.inject.Inject

internal class MaestroFixtureCleaner
    @Inject
    constructor(
        private val exerciseRecordRepository: ExerciseRecordRepository,
    ) {
        suspend fun clean(
            fixtureStartDate: LocalDate,
            today: LocalDate,
        ) {
            // 계정 확인은 호출자가 실제 registration-status 응답으로 완료한 뒤에만 들어옵니다
            val candidates = linkedSetOf<Long>()
            for (range in MaestroFixturePolicy.monthlyRanges(fixtureStartDate, today)) {
                val counts =
                    readOrFail(exerciseRecordRepository.getExerciseCountByRange(range.start, range.endInclusive))
                val seenDates = mutableSetOf<LocalDate>()
                for (count in counts) {
                    if (count.date !in range || count.count < 0 || !seenDates.add(count.date)) {
                        throw MaestroSetupFailure("fixture_read_failed")
                    }
                    if (count.count == 0) continue
                    val records = readOrFail(exerciseRecordRepository.getExerciseRecordList(count.date))
                    for (record in records) {
                        if (record.recordId <= 0 || record.startTime.toLocalDate() != count.date) {
                            throw MaestroSetupFailure("fixture_read_failed")
                        }
                        if (MaestroFixturePolicy.ownsTitle(record.title)) {
                            candidates.add(record.recordId)
                        }
                    }
                }
            }

            // 모든 월의 조회가 성공한 뒤 삭제하며, 조회 후 일반 제목으로 바뀐 기록은 보존합니다
            for (recordId in candidates) {
                val record = readOrFail(exerciseRecordRepository.getExerciseDetail(recordId))
                if (record.startedAt.toLocalDate() !in fixtureStartDate..today) {
                    throw MaestroSetupFailure("fixture_read_failed")
                }
                if (!MaestroFixturePolicy.ownsTitle(record.title)) continue
                if (exerciseRecordRepository.deleteExerciseRecord(recordId) is BaseResult.Error) {
                    throw MaestroSetupFailure("fixture_delete_failed")
                }
            }
        }

        private fun <T> readOrFail(result: BaseResult<T>): T =
            when (result) {
                is BaseResult.Success -> result.data
                is BaseResult.Error -> throw MaestroSetupFailure("fixture_read_failed")
            }
    }
