package com.project200.undabang.e2e

import com.project200.data.api.ApiService
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

// 전용 테스트 계정의 기존 심플 타이머 삭제를 승인받은 이번 CI에만 연결합니다.
internal class MaestroTimerFixture
    @Inject
    constructor(
        private val apiService: ApiService,
    ) : MaestroFeatureFixture {
        override suspend fun prepare(request: MaestroFeatureRequest): Map<String, String> {
            validateFeature(request)
            if (request.flow != "timer-simple-preset-crud") return emptyMap()

            // Coordinator의 실제 인증·member 일치 확인 뒤, 성공한 초기 목록의 ID만 삭제합니다.
            // 전체 DTO와 모든 ID를 검증하기 전에는 첫 DELETE도 보내지 않습니다.
            val candidates = readIds("timer_fixture_read_failed")
            for (id in candidates) {
                val response =
                    try {
                        apiService.deleteSimpleTimer(id)
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        throw MaestroSetupFailure("timer_fixture_delete_failed")
                    }
                if (!response.succeed) throw MaestroSetupFailure("timer_fixture_delete_failed")
            }
            // 재조회에 새 ID가 생겨도 삭제 범위를 늘리지 않고 실패합니다.
            if (readIds("timer_fixture_verify_failed").isNotEmpty()) {
                throw MaestroSetupFailure("timer_fixture_reset_incomplete")
            }
            return mapOf("TIMER_SIMPLE_RESET_DELETED_COUNT" to candidates.size.toString())
        }

        override suspend fun cleanup(request: MaestroFeatureRequest) {
            validateFeature(request)
            // 승인된 일회성 삭제는 복원 대상이 아니며 cleanup에서는 API를 호출하지 않습니다.
        }

        private fun validateFeature(request: MaestroFeatureRequest) {
            if (request.feature != "timer") throw MaestroSetupFailure("timer_fixture_identity_invalid")
        }

        private suspend fun readIds(reason: String): Set<Long> {
            val response =
                try {
                    apiService.getSimpleTimers()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    // 서버 응답과 예외 본문은 상태 파일이나 로그에 전달하지 않습니다.
                    throw MaestroSetupFailure(reason)
                }
            if (!response.succeed) throw MaestroSetupFailure(reason)
            val data = response.data ?: throw MaestroSetupFailure(reason)
            if (data.simpleTimerCount < 0 || data.simpleTimerCount != data.simpleTimers.size) {
                throw MaestroSetupFailure(reason)
            }
            val ids = linkedSetOf<Long>()
            for (timer in data.simpleTimers) {
                if (timer.simpleTimerId <= 0 || !ids.add(timer.simpleTimerId)) throw MaestroSetupFailure(reason)
            }
            return ids
        }
    }
