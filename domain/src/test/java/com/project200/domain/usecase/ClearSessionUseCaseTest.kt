package com.project200.domain.usecase

import com.project200.domain.manager.FcmTokenSyncScheduler
import com.project200.domain.repository.AuthRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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

    private lateinit var useCase: ClearSessionUseCase

    @Before
    fun setUp() {
        useCase = ClearSessionUseCase(mockRepository, mockFcmTokenSyncScheduler)
    }

    @Test
    fun `invoke 호출 시 repository clearSession 호출`() = runTest {
        // Given
        coEvery { mockRepository.clearSession() } just Runs
        every { mockFcmTokenSyncScheduler.cancel() } just Runs

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

        // When
        useCase()

        // Then
        verify(exactly = 1) { mockFcmTokenSyncScheduler.cancel() }
    }
}
