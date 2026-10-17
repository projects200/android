package com.project200.undabang.e2e

import com.project200.data.api.ApiService
import com.project200.data.datasource.FeatureFlagDataSource
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetCustomTimerDetailDTO
import com.project200.data.dto.GetExerciseRecordListDto
import com.project200.domain.repository.AppUpdateRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

internal data class MaestroOfflineRun(
    val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
) {
    init {
        if (!ID_PATTERN.matches(id) || endedAt != startedAt.plusHours(1) ||
            startedAt.minute != 0 || startedAt.second != 0 || startedAt.nano != 0
        ) {
            throw MaestroSetupFailure("invalid_request")
        }
    }

    val exerciseTitle: String = "maestro-e2e-$id"
    val createdTimer: String = "maestro-offline-$id-create"
    val originalTimer: String = "maestro-offline-$id-update"
    val updatedTimer: String = "maestro-offline-$id-updated"
    val deletedTimer: String = "maestro-offline-$id-delete"
    val draftTimer: String = "maestro-offline-$id-draft"
    val timerNames: Set<String> = setOf(createdTimer, originalTimer, updatedTimer, deletedTimer, draftTimer)

    companion object {
        private val ID_PATTERN = Regex("[a-f0-9]{32}")
    }
}

/** 생성·수정은 UI와 실제 동기화만 수행합니다. 이 클래스는 직접 GET과 실행별 정리만 수행합니다 */
internal class MaestroOfflineFixtures
    @Inject
    constructor(
        private val api: ApiService,
        private val featureFlags: FeatureFlagDataSource,
        private val appUpdateRepository: AppUpdateRepository,
    ) {
        suspend fun requireCacheEnabled() {
            // 기본값 false인 플래그를 가정하지 않고 실제 fetch/activate 경로에서 확인합니다.
            appUpdateRepository.getUpdateInfo().getOrElse { throw MaestroSetupFailure("offline_flag_fetch_failed") }
            if (!featureFlags.isOfflineCacheEnabled()) throw MaestroSetupFailure("offline_cache_flag_disabled")
        }

        fun validateDate(run: MaestroOfflineRun) {
            if (run.endedAt.toLocalDate() > LocalDate.now()) throw MaestroSetupFailure("fixture_range_invalid")
        }

        suspend fun observe(
            run: MaestroOfflineRun,
            stage: String,
        ) {
            if (stage == "exercise-online") {
                val records = exerciseRecords(run)
                if (records.size != 1) throw MaestroSetupFailure("offline_exercise_server_mismatch")
                val detail = read(api.getExerciseRecordDetail(records.single().exerciseId))
                if (detail.exerciseTitle != run.exerciseTitle || detail.exerciseStartedAt != run.startedAt ||
                    detail.exerciseEndedAt != run.endedAt
                ) {
                    throw MaestroSetupFailure("offline_exercise_server_mismatch")
                }
                return
            }
            val expected =
                when (stage) {
                    "timers-online" -> mapOf(run.originalTimer to 45, run.deletedTimer to 45)
                    "timers-synced" -> mapOf(run.createdTimer to 45, run.updatedTimer to 75)
                    else -> throw MaestroSetupFailure("invalid_request")
                }
            val observed =
                withTimeoutOrNull(UPLOAD_TIMEOUT_MS) {
                    var matches = timersMatch(run, expected)
                    while (!matches) {
                        delay(2000)
                        matches = timersMatch(run, expected)
                    }
                    true
                }
            if (observed != true) throw MaestroSetupFailure("offline_upload_not_observed")
        }

        private suspend fun timersMatch(
            run: MaestroOfflineRun,
            expected: Map<String, Int>,
        ): Boolean {
            val summaries = timerSummaries()
            val owned = summaries.filter { it.customTimerName in run.timerNames }
            if (owned.size != expected.size || owned.map { it.customTimerName }.toSet() != expected.keys) return false
            for (summary in owned) {
                val detail = read(api.getCustomTimer(summary.customTimerId))
                if (!matchesTimer(detail, summary.customTimerId, summary.customTimerName, expected.getValue(summary.customTimerName))) {
                    return false
                }
            }
            return true
        }

        suspend fun clean(run: MaestroOfflineRun) {
            // 호출자가 DEV 세션과 전용 member ID를 검증한 뒤에만 실행합니다.
            val exercises = exerciseRecords(run)
            val timers = timerSummaries().filter { it.customTimerName in run.timerNames }
            // 양쪽 목록 GET이 성공한 뒤 UUID와 상세를 재확인한 행만 삭제합니다.
            for (record in exercises) {
                val detail = read(api.getExerciseRecordDetail(record.exerciseId))
                if (detail.exerciseTitle != run.exerciseTitle) continue
                if (detail.exerciseStartedAt.toLocalDate() != run.startedAt.toLocalDate()) {
                    throw MaestroSetupFailure("fixture_read_failed")
                }
                if (!api.deleteExerciseRecord(record.exerciseId).succeed) throw MaestroSetupFailure("fixture_delete_failed")
            }
            for (timer in timers) {
                val detail = read(api.getCustomTimer(timer.customTimerId))
                if (detail.customTimerId != timer.customTimerId) throw MaestroSetupFailure("fixture_read_failed")
                if (detail.customTimerName !in run.timerNames) continue
                if (!api.deleteCustomTimer(timer.customTimerId).succeed) throw MaestroSetupFailure("fixture_delete_failed")
            }
        }

        private suspend fun exerciseRecords(run: MaestroOfflineRun): List<GetExerciseRecordListDto> {
            val records = read(api.getExerciseList(run.startedAt.toLocalDate()))
            if (records.any { it.exerciseId <= 0 || it.exerciseStartedAt.toLocalDate() != run.startedAt.toLocalDate() } ||
                records.map { it.exerciseId }.toSet().size != records.size
            ) {
                throw MaestroSetupFailure("fixture_read_failed")
            }
            return records.filter { it.exerciseTitle == run.exerciseTitle }
        }

        private suspend fun timerSummaries() =
            read(api.getCustomTimerList()).also { list ->
                if (list.customTimerCount != list.customTimers.size || list.customTimers.any { it.customTimerId <= 0 } ||
                    list.customTimers.map { it.customTimerId }.toSet().size != list.customTimers.size
                ) {
                    throw MaestroSetupFailure("fixture_read_failed")
                }
            }.customTimers

        private fun <T : Any> read(response: BaseResponse<T>): T {
            if (!response.succeed) throw MaestroSetupFailure("fixture_read_failed")
            return response.data ?: throw MaestroSetupFailure("fixture_read_failed")
        }

        companion object {
            val STAGES = setOf("exercise-online", "timers-online", "timers-synced")
            private const val UPLOAD_TIMEOUT_MS = 120_000L

            internal fun matchesTimer(
                detail: GetCustomTimerDetailDTO,
                id: Long,
                name: String,
                seconds: Int,
            ): Boolean =
                detail.customTimerId == id && detail.customTimerName == name && detail.customTimerStepCount == 1 &&
                    detail.customTimerSteps.singleOrNull()?.let {
                        it.customTimerStepName == "offline-step" && it.customTimerStepOrder == 0 && it.customTimerStepTime == seconds
                    } == true
        }
    }
