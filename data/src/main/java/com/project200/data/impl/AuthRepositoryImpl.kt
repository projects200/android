package com.project200.data.impl

import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.dto.GetIsNicknameDuplicated
import com.project200.data.dto.PostSignUpRequest
import com.project200.data.local.PendingLogin
import com.project200.data.local.PreferenceManager
import com.project200.data.utils.apiCallBuilder
import com.project200.data.utils.idTokenSubject
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.BaseResult
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.repository.AuthRepository
import com.project200.undabang.oauth.AuthStateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class AuthRepositoryImpl
    @Inject
    constructor(
        private val apiService: ApiService,
        private val spManager: PreferenceManager,
        private val authStateManager: AuthStateManager,
        private val sessionDataCleaner: SessionDataCleaner,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val imageFileStore: ExerciseImageFileStore,
    ) : AuthRepository {
        override suspend fun beginLogin() = withContext(ioDispatcher) {
            imageFileStore.withLock {
                startPendingLogin()
                Unit
            }
        }

        private fun startPendingLogin(): PendingLogin {
            imageFileStore.invalidateSession()
            spManager.invalidateExerciseSession()
            return PendingLogin(
                attemptId = UUID.randomUUID().toString(),
                retainedMemberId = spManager.getRetainedMemberId(),
                retainedSubject = spManager.getMemberSubject(),
                sessionRevision = spManager.exerciseSessionRevision(),
                fileSessionRevision = imageFileStore.sessionRevision,
            ).also(spManager::savePendingLogin)
        }

        override suspend fun resumePendingLogin(): RegistrationStatus? {
            if (spManager.getPendingLogin() == null) return null
            // 재생성 후에도 자동 승인하지 않습니다. 새 서버 확인과 새 승인 번호를 발급합니다.
            return checkIsRegistered()
        }

        private fun isCurrent(pending: PendingLogin): Boolean =
            spManager.getPendingLogin() == pending &&
                spManager.getRetainedMemberId() == pending.retainedMemberId &&
                spManager.getMemberSubject() == pending.retainedSubject &&
                spManager.exerciseSessionRevision() == pending.sessionRevision &&
                imageFileStore.sessionRevision == pending.fileSessionRevision &&
                pending.subject != null && currentSubject() == pending.subject

        private fun currentSubject(): String? = idTokenSubject(authStateManager.getCurrent().idToken)

        /** 서버 요청은 파일 잠금 밖에서 실행하고 서버가 명시한 등록 여부만 계정 판정에 사용합니다. */
        private suspend fun lookupRegistration(): BaseResult<String?> =
            apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getIsRegistered() },
                mapper = { data ->
                    requireNotNull(data)
                    if (data.isRegistered) requireNotNull(data.memberId).also { require(it.isNotBlank()) } else null
                },
            ).let { result ->
                // XUserIdCheckInterceptor는 검증된 subject의 회원이 없을 때 이 코드만 반환합니다.
                // 일반 401·헤더 오류·네트워크 실패는 미가입으로 추정하지 않습니다.
                if (result is BaseResult.Error && result.errorCode == "AUTHENTICATION_FAILED") {
                    BaseResult.Success(null)
                } else {
                    result
                }
            }

        override suspend fun checkIsRegistered(): RegistrationStatus = withContext(ioDispatcher) check@{
            val pending = imageFileStore.withLock {
                val previous = spManager.getPendingLogin()
                    ?.takeIf {
                        it.sessionRevision == spManager.exerciseSessionRevision() &&
                            it.fileSessionRevision == imageFileStore.sessionRevision
                    }
                    ?: startPendingLogin()
                // 재시도는 이전 다이얼로그의 승인을 무효화합니다.
                previous.copy(subject = currentSubject(), confirmationId = null, signupAllowed = false)
                    .also(spManager::savePendingLogin)
            }
            if (pending.subject == null) return@check RegistrationStatus.Indeterminate
            val result = lookupRegistration()
            if (result !is BaseResult.Success) return@check RegistrationStatus.Indeterminate
            val target = pending.copy(targetMemberId = result.data)
            imageFileStore.withLock {
                if (!isCurrent(pending)) return@withLock RegistrationStatus.Indeterminate
                spManager.savePendingLogin(target)
                when {
                    (target.retainedMemberId != null && target.retainedMemberId != target.targetMemberId) ||
                        (target.retainedMemberId == null && sessionDataCleaner.hasStoredData()) -> {
                        val confirmation = target.copy(confirmationId = UUID.randomUUID().toString())
                        spManager.savePendingLogin(confirmation)
                        RegistrationStatus.ConfirmationRequired(requireNotNull(confirmation.confirmationId))
                    }
                    target.retainedMemberId != null -> {
                        adoptRegistered(target)
                        RegistrationStatus.Registered
                    }
                    else -> null // 빈 최초 로그인도 정리 잠금 안에서 저장소를 다시 확인합니다.
                }
            } ?: discardAndAdopt(target, discardApproved = false)
        }

        override suspend fun confirmAccountAdoption(confirmationId: String): RegistrationStatus {
            val pending = imageFileStore.withLock {
                spManager.getPendingLogin()?.takeIf { it.confirmationId == confirmationId && isCurrent(it) }
            } ?: return RegistrationStatus.Indeterminate
            val result = lookupRegistration()
            if (result !is BaseResult.Success || result.data != pending.targetMemberId) {
                return RegistrationStatus.Indeterminate
            }
            return discardAndAdopt(pending, discardApproved = true)
        }

        private suspend fun discardAndAdopt(
            pending: PendingLogin,
            discardApproved: Boolean,
        ): RegistrationStatus {
            val cleared = sessionDataCleaner.clearForAccountAdoption(
                discardApproved = discardApproved,
                isCurrent = { isCurrent(pending) },
                onCleared = {
                    if (pending.targetMemberId != null) {
                        adoptRegistered(pending)
                    } else {
                        spManager.clearMemberId()
                        // 회원가입도 이 승인된 subject와 빈 계정 경계에만 묶습니다.
                        spManager.savePendingLogin(
                            pending.copy(
                                retainedMemberId = null,
                                retainedSubject = null,
                                sessionRevision = spManager.exerciseSessionRevision(),
                                fileSessionRevision = imageFileStore.sessionRevision,
                                confirmationId = null,
                                signupAllowed = true,
                            ),
                        )
                    }
                },
            )
            return when {
                !cleared -> RegistrationStatus.Indeterminate
                pending.targetMemberId == null -> RegistrationStatus.Unregistered
                else -> RegistrationStatus.Registered
            }
        }

        private fun adoptRegistered(pending: PendingLogin) {
            spManager.saveMemberId(requireNotNull(pending.targetMemberId), pending.subject)
            spManager.completePendingLogin()
        }

        override suspend fun logout(): BaseResult<Unit> =
            apiCallBuilder(ioDispatcher, { apiService.postLogout() }, { Unit })

        override suspend fun signUp(
            gender: String,
            nickname: String,
            birth: LocalDate,
        ): BaseResult<Unit> = withContext(ioDispatcher) signup@{
            val pending = imageFileStore.withLock {
                spManager.getPendingLogin()?.takeIf {
                    it.signupAllowed && it.retainedMemberId == null && isCurrent(it)
                }
            } ?: return@signup sessionChanged()
            val result = apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.postSignUp(PostSignUpRequest(gender, birth, nickname)) },
                mapper = { requireNotNull(it?.memberId).also { id -> require(id.isNotBlank()) } },
            )
            when (result) {
                is BaseResult.Error -> result
                is BaseResult.Success -> imageFileStore.withLock {
                    if (!isCurrent(pending)) return@withLock sessionChanged()
                    spManager.saveMemberId(result.data, pending.subject)
                    spManager.completePendingLogin()
                    BaseResult.Success(Unit)
                }
            }
        }

        private fun sessionChanged() = BaseResult.Error(
            errorCode = "LOGIN_SESSION_CHANGED",
            message = "로그인 상태를 다시 확인해주세요.",
        )

        override suspend fun checkNicknameDuplicated(nickname: String): BaseResult<Boolean> =
            apiCallBuilder(
                ioDispatcher = ioDispatcher,
                apiCall = { apiService.getIsNicknameDuplicated(nickname) },
                mapper = { dto: GetIsNicknameDuplicated? -> dto?.available == true },
            )

        override suspend fun getMemberId(): String? = spManager.getMemberId()

        override suspend fun clearSession() = withContext(ioDispatcher) {
            imageFileStore.withLock {
                invalidateCredentials()
                spManager.clearMemberId()
                spManager.completePendingLogin()
            }
        }

        override suspend fun clearTokens() = withContext(ioDispatcher) {
            imageFileStore.withLock {
                invalidateCredentials()
                spManager.completePendingLogin()
            }
        }

        private fun invalidateCredentials() {
            imageFileStore.invalidateSession()
            spManager.invalidateExerciseSession()
            authStateManager.clearAuthState()
        }
    }
