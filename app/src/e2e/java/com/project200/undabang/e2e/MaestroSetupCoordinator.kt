package com.project200.undabang.e2e

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MaestroSetupCoordinator
    @Inject
    constructor(
        private val protocol: MaestroSetupProtocol,
        private val sessionBootstrap: MaestroSessionBootstrap,
        private val fixtureCleaner: MaestroFixtureCleaner,
    ) {
        private val requestMutex = Mutex()

        suspend fun consumeRequest() =
            // Activity 종료로 refresh 콜백과 실패 정리가 교차하지 않도록 작업을 끝까지 직렬화합니다
            withContext(NonCancellable + Dispatchers.IO) {
                requestMutex.withLock {
                    try {
                        val request = protocol.consumeRequest()
                        protocol.clearOutputs()
                        when (request) {
                            MaestroSetupRequest.Export -> {
                                val memberId = sessionBootstrap.authenticate(sessionBootstrap.currentState())
                                protocol.writeSession(sessionBootstrap.currentState(), memberId)
                                protocol.writeStatus("exported")
                            }
                            is MaestroSetupRequest.FixtureOperation -> {
                                val today = LocalDate.now()
                                if (request.fixtureStartDate > today) throw MaestroSetupFailure("fixture_range_invalid")
                                sessionBootstrap.authenticate(request.authState, request.memberId)
                                fixtureCleaner.clean(request.fixtureStartDate, today)
                                if (request.operation == "cleanup") sessionBootstrap.clearSession()
                                protocol.writeStatus(if (request.operation == "prepare") "ready" else "cleaned")
                            }
                        }
                    } catch (failure: MaestroSetupFailure) {
                        failClosed(failure.reason)
                    } catch (_: Exception) {
                        // 예외 본문이나 서버 응답을 결과 파일과 로그로 전달하지 않습니다
                        failClosed("setup_failed")
                    }
                }
            }

        private suspend fun failClosed(reason: String) {
            var failureReason = reason
            try {
                protocol.deleteRequest()
            } catch (_: Exception) {
                failureReason = "private_file_failed"
            }
            try {
                sessionBootstrap.clearSession()
            } catch (_: Exception) {
                failureReason = "local_session_clear_failed"
            }
            try {
                protocol.deleteSession()
                protocol.writeFailure(failureReason)
            } catch (_: Exception) {
                // 결과를 쓸 수 없으면 이전 성공 파일도 지워 호스트가 성공으로 해석하지 못하게 합니다
                try {
                    protocol.clearOutputs()
                } catch (_: Exception) {
                    // 호스트는 결과 파일 부재 또는 private 파일 I/O 실패를 테스트 실패로 처리합니다
                }
            }
        }
    }
