package com.project200.undabang.e2e

import com.project200.domain.model.BaseResult
import com.project200.domain.repository.ExerciseRecordRepository
import kotlinx.coroutines.delay
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
            val candidates = linkedSetOf<String>()
            for (range in MaestroFixturePolicy.monthlyRanges(fixtureStartDate, today)) {
                val counts =
                    readFresh {
                        exerciseRecordRepository.getExerciseCountByRange(
                            range.start,
                            range.endInclusive,
                            refresh = true,
                        )
                    }
                val seenDates = mutableSetOf<LocalDate>()
                for (count in counts) {
                    if (count.date !in range || count.count < 0 || !seenDates.add(count.date)) {
                        throw MaestroSetupFailure("fixture_read_failed")
                    }
                    if (count.count == 0) continue
                    val records =
                        readFresh {
                            exerciseRecordRepository.getExerciseRecordList(count.date, refresh = true)
                        }
                    for (record in records) {
                        if (record.recordId.isBlank() || record.syncPending || record.startTime.toLocalDate() != count.date) {
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
                val record = readFresh { exerciseRecordRepository.getExerciseDetail(recordId, refresh = true) }
                if (record.syncPending || !record.canModify || record.startedAt.toLocalDate() !in fixtureStartDate..today) {
                    throw MaestroSetupFailure("fixture_read_failed")
                }
                if (!MaestroFixturePolicy.ownsTitle(record.title)) continue
                if (!deleteWithRetry(recordId)) {
                    throw MaestroSetupFailure("fixture_delete_failed")
                }
            }
        }

        private suspend fun <T> readFresh(read: suspend () -> BaseResult<T>): T {
            repeat(MAX_TRANSIENT_ATTEMPTS) { attempt ->
                when (val result = read()) {
                    is BaseResult.Success -> {
                        val refreshError = result.refreshError
                        if (refreshError != null) {
                            if (!isTransient(refreshError) || attempt == MAX_TRANSIENT_ATTEMPTS - 1) {
                                throw MaestroSetupFailure("fixture_read_failed")
                            }
                        } else if (!result.isCachedSnapshot) {
                            return result.data
                        } else if (attempt == MAX_TRANSIENT_ATTEMPTS - 1) {
                            throw MaestroSetupFailure("fixture_read_failed")
                        }
                    }
                    is BaseResult.Error -> {
                        if (!isTransient(result) || attempt == MAX_TRANSIENT_ATTEMPTS - 1) {
                            throw MaestroSetupFailure("fixture_read_failed")
                        }
                    }
                }
                delay(RETRY_DELAY_MILLIS shl attempt)
            }
            throw MaestroSetupFailure("fixture_read_failed")
        }

        private suspend fun deleteWithRetry(recordId: String): Boolean {
            repeat(MAX_TRANSIENT_ATTEMPTS) { attempt ->
                when (val result = exerciseRecordRepository.deleteExerciseRecord(recordId)) {
                    is BaseResult.Success -> return true
                    is BaseResult.Error -> {
                        if (!isTransient(result) || attempt == MAX_TRANSIENT_ATTEMPTS - 1) return false
                    }
                }
                delay(RETRY_DELAY_MILLIS shl attempt)
            }
            return false
        }

        private fun isTransient(error: BaseResult.Error): Boolean {
            val code = error.errorCode ?: return false
            return code == "NETWORK_ERROR" ||
                code == "UNKNOWN_ERROR" ||
                code.toIntOrNull()?.let { it >= 500 } == true
        }

        private companion object {
            const val MAX_TRANSIENT_ATTEMPTS = 3
            const val RETRY_DELAY_MILLIS = 1_000L
        }
    }
