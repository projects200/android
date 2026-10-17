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
        private val featureFixtures: Map<String, @JvmSuppressWildcards MaestroFeatureFixture>,
        private val offlineFixtures: MaestroOfflineFixtures,
    ) {
        private val requestMutex = Mutex()

        suspend fun consumeRequest() =
            // Activity 종료로 refresh 콜백과 실패 정리가 교차하지 않도록 작업을 끝까지 직렬화합니다
            withContext(NonCancellable + Dispatchers.IO) {
                requestMutex.withLock {
                    var preserveLocalDataOnFailure = false
                    try {
                        val request = protocol.consumeRequest()
                        preserveLocalDataOnFailure = request.preserveLocalDataOnFailure
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
                                val feature = request.featureRequest
                                if (request.preserveLocalDataOnFailure) {
                                    // 오프라인 복구는 인증을 바꾸기 전에 원 실행의 ownership을 요구합니다.
                                    protocol.validateFeatureRecovery(feature, required = true)
                                    sessionBootstrap.authenticateForOfflineCleanup(request.authState, request.memberId)
                                } else {
                                    sessionBootstrap.authenticate(request.authState, request.memberId)
                                }
                                // 인증과 대상 handler 확인을 모두 마친 뒤에만 서버 정리를 시작합니다.
                                val handler =
                                    feature?.let {
                                        featureFixtures[it.feature] ?: throw MaestroSetupFailure("feature_fixture_missing")
                                    }
                                if (!request.preserveLocalDataOnFailure) protocol.validateFeatureRecovery(feature)
                                val variables =
                                    if (request.operation == "prepare") {
                                        fixtureCleaner.clean(request.fixtureStartDate, today)
                                        if (feature != null && handler != null) {
                                            protocol.markFeatureRecovery(feature)
                                            handler.prepare(feature)
                                        } else {
                                            emptyMap()
                                        }
                                    } else {
                                        if (feature != null && handler != null) handler.cleanup(feature)
                                        fixtureCleaner.clean(request.fixtureStartDate, today)
                                        sessionBootstrap.clearSession()
                                        if (feature != null) protocol.completeFeatureRecovery()
                                        emptyMap()
                                    }
                                protocol.writeStatus(if (request.operation == "prepare") "ready" else "cleaned", variables)
                            }
                            is MaestroSetupRequest.OfflineObservation -> {
                                protocol.validateFeatureRecovery(request.featureRequest, required = true)
                                offlineFixtures.validateDate(request.run)
                                sessionBootstrap.verifyCurrent(request.memberId)
                                offlineFixtures.observe(request.run, request.stage)
                                protocol.writeStatus("observed")
                            }
                        }
                    } catch (failure: MaestroSetupFailure) {
                        failClosed(failure.reason, preserveLocalDataOnFailure)
                    } catch (_: Exception) {
                        // 예외 본문이나 서버 응답을 결과 파일과 로그로 전달하지 않습니다
                        failClosed("setup_failed", preserveLocalDataOnFailure)
                    }
                }
            }

        private suspend fun failClosed(
            reason: String,
            preserveLocalDataOnFailure: Boolean,
        ) {
            var failureReason = reason
            try {
                protocol.deleteRequest()
            } catch (_: Exception) {
                failureReason = "private_file_failed"
            }
            try {
                if (preserveLocalDataOnFailure) {
                    sessionBootstrap.clearTokens()
                } else {
                    sessionBootstrap.clearSession()
                }
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
