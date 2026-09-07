package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.SessionExitReason
import com.project200.domain.repository.AuthRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ClearSessionUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AuthRepository

    @MockK
    private lateinit var mockFcmTokenSyncScheduler: FcmTokenSyncScheduler

    @MockK
    private lateinit var mockSessionDataCleaner: SessionDataCleaner

    private lateinit var useCase: ClearSessionUseCase

    @Before
    fun setUp() {
        useCase = ClearSessionUseCase(mockRepository, mockFcmTokenSyncScheduler, mockSessionDataCleaner)
        coEvery { mockRepository.clearSession() } just Runs
        coEvery { mockRepository.clearTokens() } just Runs
        every { mockFcmTokenSyncScheduler.cancel() } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } just Runs
    }

    @Test
    fun `사용자 이탈 - 토큰과 회원ID를 모두 지운다`() = runTest {
        // When
        useCase(SessionExitReason.USER_INITIATED)

        // Then
        coVerify(exactly = 1) { mockRepository.clearSession() }
        coVerify(exactly = 0) { mockRepository.clearTokens() }
    }

    @Test
    fun `사용자 이탈 - 로컬 캐시를 지운다`() = runTest {
        // When
        useCase(SessionExitReason.USER_INITIATED)

        // Then
        coVerify(exactly = 1) { mockSessionDataCleaner.clearAll() }
    }

    @Test
    fun `사용자 이탈 - 캐시를 지운 뒤 세션을 정리한다`() = runTest {
        // When
        useCase(SessionExitReason.USER_INITIATED)

        // Then: 계정 스코프 삭제로 좁히면 회원ID가 먼저 사라지면 지울 대상을 특정할 수 없다
        coVerifyOrder {
            mockSessionDataCleaner.clearAll()
            mockRepository.clearSession()
        }
    }

    @Test
    fun `사용자 이탈 - 캐시 삭제가 실패해도 예약과 세션을 정리한다`() = runTest {
        // Given
        coEvery { mockSessionDataCleaner.clearAll() } throws IllegalStateException("db")

        // When
        val thrown = runCatching { useCase(SessionExitReason.USER_INITIATED) }.exceptionOrNull()

        // Then
        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        verify(exactly = 1) { mockFcmTokenSyncScheduler.cancel() }
        coVerify(exactly = 1) { mockRepository.clearSession() }
    }

    @Test
    fun `강제 이탈 - 로컬 캐시를 지우지 않는다`() = runTest {
        // When
        useCase(SessionExitReason.FORCED)

        // Then: 아직 서버에 올리지 못한 전송 대기 행을 지킨다
        coVerify(exactly = 0) { mockSessionDataCleaner.clearAll() }
    }

    @Test
    fun `강제 이탈 - 토큰만 지우고 회원ID는 남긴다`() = runTest {
        // When
        useCase(SessionExitReason.FORCED)

        // Then: 회원ID가 남아야 재로그인 때 같은 계정으로 판정되어 캐시가 살아남는다
        coVerify(exactly = 1) { mockRepository.clearTokens() }
        coVerify(exactly = 0) { mockRepository.clearSession() }
    }

    @Test
    fun `이탈 이유와 무관하게 예약된 FCM 토큰 등록을 취소한다`() = runTest {
        // When
        useCase(SessionExitReason.USER_INITIATED)
        useCase(SessionExitReason.FORCED)

        // Then: 세션이 없으면 전송할 수 없다
        verify(exactly = 2) { mockFcmTokenSyncScheduler.cancel() }
    }
}
