package com.project200.undabang.e2e

import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.local.PreferenceManager
import com.project200.data.local.UndabangDatabase
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.SessionExitReason
import com.project200.domain.usecase.ClearSessionUseCase
import net.openid.appauth.AuthState
import org.json.JSONObject
import javax.inject.Inject

/** 세션 정책만 제어합니다. 사용자 폐기 승인과 계정 채택은 제품 UI에서만 실행합니다. */
internal class MaestroOfflineSessionCases
    @Inject
    constructor(
        private val preferences: PreferenceManager,
        private val bootstrap: MaestroSessionBootstrap,
        private val clearSession: ClearSessionUseCase,
        private val cleaner: SessionDataCleaner,
        private val imageFiles: ExerciseImageFileStore,
        private val exercises: MaestroOfflineExerciseCases,
        private val api: ApiService,
        private val database: UndabangDatabase,
    ) {
        suspend fun control(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
            action: String,
            authState: AuthState?,
            targetMemberId: String?,
        ): Map<String, String> {
            validate(request, run)
            val session = session(saved)
            requirePendingProof(session)
            if (action !in CONTROL_ACTIONS) fail()
            if (action == "force-exit") {
                if (authState != null || targetMemberId != null || session.optBoolean("approvedErasure")) fail()
                requireActive(request.memberId)
                exercises.assertRetained(request, run, saved)
                clearSession(SessionExitReason.FORCED)
                requireSignedOut(request.memberId)
                exercises.assertRetained(request, run, saved)
                session.put("forcedExit", true)
            } else {
                val state = authState ?: fail()
                val target = targetMemberId?.takeIf { it.isNotBlank() } ?: fail()
                val primary = action == "stage-primary-login"
                if (primary != (target == request.memberId)) fail()
                if (!primary && request.flow != "offline-session-adoption") fail()
                val retained = preferences.getRetainedMemberId()
                if (session.optBoolean("approvedErasure")) {
                    // B가 활성인 동안 A 자격 증명을 설치하면 묵시적 계정 전환이 되므로 금지합니다.
                    if (!primary || !session.optBoolean("logoutApproved") && !session.optBoolean("peerLoggedOut")) fail()
                    requireSignedOut(null)
                    exercises.assertErased(request, run, saved)
                } else {
                    if (retained != request.memberId || preferences.getMemberId()?.let { it != request.memberId } == true) fail()
                    exercises.assertRetained(request, run, saved)
                }
                val observed = bootstrap.stagePendingLogin(state, target, retained)
                if (observed != target || preferences.getMemberId() != null || preferences.getPendingLogin() == null) fail()
                if (!session.optBoolean("approvedErasure")) exercises.assertRetained(request, run, saved)
                if (!primary) {
                    session.put("peerMemberId", observed)
                    session.put("peerConfirmationObserved", false)
                }
                session.put("stagedTarget", if (primary) "primary" else "peer")
            }
            return emptyMap()
        }

        suspend fun observe(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
            stage: String,
        ): Map<String, String> {
            validate(request, run)
            if (stage !in LOCAL_STAGES && stage !in SERVER_STAGES) fail()
            if (stage == "session-baseline") {
                if (saved.has("session")) fail()
                requireActive(request.memberId)
                // 전역 정리가 가능한 UI에 들어가기 전에 모든 사용자 테이블과 소유 파일을 확인합니다.
                imageFiles.withLock {
                    if (cleaner.hasStoredData()) throw MaestroSetupFailure("offline_session_baseline_not_empty")
                    requireActive(request.memberId)
                }
                saved.put("session", JSONObject().put("cleanBaseline", true))
                return emptyMap()
            }
            val session = session(saved)
            if (stage == "session-pending") {
                if (session.optBoolean("pendingCaptured") || session.optBoolean("approvedErasure")) fail()
                requireActive(request.memberId)
                exercises.assertRetained(request, run, saved)
                session.put("pendingCaptured", true)
                return emptyMap()
            }
            requirePendingProof(session)
            when (stage) {
                "session-forced-retained" -> {
                    if (!session.optBoolean("forcedExit")) fail()
                    requireSignedOut(request.memberId)
                    exercises.assertRetained(request, run, saved)
                }
                "session-primary-restored" -> {
                    if (session.optString("stagedTarget") != "primary" || session.optBoolean("approvedErasure")) fail()
                    requireActive(request.memberId)
                    exercises.assertRetained(request, run, saved)
                    session.put("primaryRestored", true)
                }
                "session-logout-cancelled" -> {
                    if (request.flow != "offline-session-logout" || session.optBoolean("approvedErasure")) fail()
                    requireActive(request.memberId)
                    exercises.assertRetained(request, run, saved)
                    session.put("logoutCancelled", true)
                }
                "session-logout-approved" -> {
                    if (request.flow != "offline-session-logout" || !session.optBoolean("logoutCancelled")) fail()
                    requireSignedOut(null)
                    exercises.assertErased(request, run, saved)
                    session.put("approvedErasure", true).put("logoutApproved", true)
                }
                "session-peer-pending" -> {
                    requireAdoption(request, session)
                    val pending = preferences.getPendingLogin() ?: fail()
                    if (
                        preferences.getMemberId() != null || preferences.getRetainedMemberId() != request.memberId ||
                        pending.retainedMemberId != request.memberId || pending.targetMemberId != peer(session) ||
                        pending.subject.isNullOrBlank() || pending.confirmationId.isNullOrBlank()
                    ) {
                        fail()
                    }
                    requireCredentials()
                    exercises.assertRetained(request, run, saved)
                    session.put("peerConfirmationObserved", true)
                }
                "session-peer-cancelled" -> {
                    requireAdoption(request, session)
                    if (!session.optBoolean("peerConfirmationObserved")) fail()
                    requireSignedOut(request.memberId)
                    exercises.assertRetained(request, run, saved)
                    session.put("peerCancelled", true)
                }
                "session-peer-approved" -> {
                    requireAdoption(request, session)
                    if (!session.optBoolean("peerConfirmationObserved") || !session.optBoolean("peerCancelled")) fail()
                    requireActive(peer(session))
                    exercises.assertErased(request, run, saved)
                    session.put("approvedErasure", true).put("peerApproved", true)
                }
                "session-peer-isolated" -> {
                    if (!session.optBoolean("peerApproved") || !session.optBoolean("approvedErasure")) fail()
                    val member = peer(session)
                    requireActive(member)
                    bootstrap.verifyCurrent(member)
                    exercises.assertErased(request, run, saved)
                    val response = api.getExerciseList(run.startedAt.toLocalDate())
                    val records = response.data ?: throw MaestroSetupFailure("offline_session_peer_not_isolated")
                    if (!response.succeed || records.any { it.exerciseTitle in run.exerciseTitles }) {
                        throw MaestroSetupFailure("offline_session_peer_not_isolated")
                    }
                    val dao = database.exerciseRecordDao()
                    val date = run.startedAt.toLocalDate()
                    if (
                        (dao.getListByDate(member, date) + dao.getPendingInRange(member, date, date)).any {
                            it.title in run.exerciseTitles
                        }
                    ) {
                        throw MaestroSetupFailure("offline_session_peer_not_isolated")
                    }
                    requireActive(member)
                    session.put("peerIsolated", true)
                }
                "session-peer-logged-out" -> {
                    if (!session.optBoolean("peerApproved") || !session.optBoolean("peerIsolated")) fail()
                    requireSignedOut(null)
                    exercises.assertErased(request, run, saved)
                    session.put("peerLoggedOut", true)
                }
                "session-primary-restored-clean" -> {
                    if (!session.optBoolean("approvedErasure") || session.optString("stagedTarget") != "primary") fail()
                    if (!session.optBoolean("logoutApproved") && !session.optBoolean("peerLoggedOut")) fail()
                    requireActive(request.memberId)
                    exercises.assertErased(request, run, saved)
                    session.put("primaryRestoredClean", true)
                }
                else -> fail()
            }
            return emptyMap()
        }

        /** 실패나 승인 대기 상태에서는 복구 표식과 미전송 원본을 정리하지 않습니다. */
        suspend fun requireSafeCleanup(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
            saved: JSONObject,
        ) {
            validate(request, run)
            val session = session(saved)
            requirePendingProof(session)
            requireActive(request.memberId)
            if (session.optBoolean("approvedErasure")) {
                val approvedByFlow =
                    when (request.flow) {
                        "offline-session-logout" -> session.optBoolean("logoutCancelled") && session.optBoolean("logoutApproved")
                        "offline-session-adoption" ->
                            session.optBoolean("peerCancelled") && session.optBoolean("peerApproved") &&
                                session.optBoolean("peerIsolated") && session.optBoolean("peerLoggedOut")
                        else -> false
                    }
                if (!approvedByFlow || !session.optBoolean("primaryRestoredClean")) fail()
                exercises.assertErased(request, run, saved)
            } else if (
                request.flow != "offline-session-retain" ||
                !session.optBoolean("forcedExit") || !session.optBoolean("primaryRestored")
            ) {
                throw MaestroSetupFailure("offline_session_cleanup_unsafe")
            }
        }

        private fun validate(
            request: MaestroFeatureRequest,
            run: MaestroOfflineRun,
        ) {
            if (request.feature != "offline" || request.flow !in FLOWS || request.runId != run.id || request.memberId.isBlank()) fail()
        }

        private fun session(saved: JSONObject): JSONObject =
            saved.optJSONObject("session")?.takeIf { it.optBoolean("cleanBaseline") } ?: fail()

        private fun requirePendingProof(session: JSONObject) {
            if (!session.optBoolean("cleanBaseline") || !session.optBoolean("pendingCaptured")) fail()
        }

        private fun requireAdoption(
            request: MaestroFeatureRequest,
            session: JSONObject,
        ) {
            if (
                request.flow != "offline-session-adoption" || session.optString("stagedTarget") != "peer" ||
                session.optBoolean("approvedErasure") || peer(session) == request.memberId
            ) {
                fail()
            }
        }

        private fun peer(session: JSONObject): String = session.optString("peerMemberId").takeIf { it.isNotBlank() } ?: fail()

        private fun requireActive(memberId: String) {
            if (
                preferences.getMemberId() != memberId || preferences.getRetainedMemberId() != memberId ||
                preferences.getPendingLogin() != null
            ) {
                fail()
            }
            requireCredentials()
        }

        private fun requireCredentials() {
            val state = bootstrap.currentState()
            if (!state.isAuthorized || state.refreshToken.isNullOrBlank()) fail()
        }

        private fun requireSignedOut(retainedMemberId: String?) {
            val state = bootstrap.currentState()
            if (
                preferences.getRetainedMemberId() != retainedMemberId || preferences.getMemberId() != retainedMemberId ||
                preferences.getPendingLogin() != null || state.isAuthorized || state.refreshToken != null
            ) {
                fail()
            }
        }

        private fun fail(): Nothing = throw MaestroSetupFailure("offline_session_boundary_invalid")

        companion object {
            val FLOWS = setOf("offline-session-retain", "offline-session-logout", "offline-session-adoption")
            val CONTROL_ACTIONS = setOf("force-exit", "stage-primary-login", "stage-peer-login")
            val LOCAL_STAGES =
                setOf(
                    "session-baseline",
                    "session-pending",
                    "session-forced-retained",
                    "session-primary-restored",
                    "session-logout-cancelled",
                    "session-logout-approved",
                    "session-peer-pending",
                    "session-peer-cancelled",
                    "session-peer-approved",
                    "session-peer-logged-out",
                    "session-primary-restored-clean",
                )
            val SERVER_STAGES = setOf("session-peer-isolated")
        }
    }
