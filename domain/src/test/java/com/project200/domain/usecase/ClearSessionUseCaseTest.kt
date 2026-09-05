package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.manager.SessionDataCleaner
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
    }

    @Test
    fun `invoke 호출 시 repository clearSession 호출`() = runTest {
        // Given
        coEvery { mockRepository.clearSession() } just Runs
        every { mockFcmTokenSyncScheduler.cancel() } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } just Runs

        // When
        useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.clearSession() }
    }

    @Test
    fun `invoke 호출 시 예약된 FCM 토큰 등록을 취소한다`() = runTest {
        // Given
        coEvery { mockRepository.clearSession() } just Runs
        every { mockFcmTokenSyncScheduler.cancel() } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } just Runs

        // When
        useCase()

        // Then
        verify(exactly = 1) { mockFcmTokenSyncScheduler.cancel() }
    }

    @Test
    fun `invoke 호출 시 로컬 캐시를 지운다`() = runTest {
        // Given
        coEvery { mockRepository.clearSession() } just Runs
        every { mockFcmTokenSyncScheduler.cancel() } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } just Runs

        // When
        useCase()

        // Then
        coVerify(exactly = 1) { mockSessionDataCleaner.clearAll() }
    }

    @Test
    fun `invoke 호출 시 캐시를 지운 뒤 세션을 정리한다`() = runTest {
        // Given
        coEvery { mockRepository.clearSession() } just Runs
        every { mockFcmTokenSyncScheduler.cancel() } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } just Runs

        // When
        useCase()

        // Then: 회원ID가 남아 있는 동안 지워야 계정 스코프 삭제로 좁힐 수 있다
        coVerifyOrder {
            mockSessionDataCleaner.clearAll()
            mockRepository.clearSession()
        }
    }

    @Test
    fun `invoke 호출 시 캐시 삭제가 실패해도 예약과 세션을 정리한다`() = runTest {
        // Given
        coEvery { mockRepository.clearSession() } just Runs
        every { mockFcmTokenSyncScheduler.cancel() } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } throws IllegalStateException("db")

        // When
        val thrown = runCatching { useCase() }.exceptionOrNull()

        // Then
        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        verify(exactly = 1) { mockFcmTokenSyncScheduler.cancel() }
        coVerify(exactly = 1) { mockRepository.clearSession() }
    }
}
