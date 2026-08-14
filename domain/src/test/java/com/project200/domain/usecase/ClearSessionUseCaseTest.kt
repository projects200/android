package com.project200.domain.usecase

import com.project200.domain.repository.AuthRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
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

    private lateinit var useCase: ClearSessionUseCase

    @Before
    fun setUp() {
        useCase = ClearSessionUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository clearSession 호출`() = runTest {
        // Given
        coEvery { mockRepository.clearSession() } just Runs

        // When
        useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.clearSession() }
    }
}
