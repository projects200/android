package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class CheckIsRegisteredUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AuthRepository

    private lateinit var useCase: CheckIsRegisteredUseCase

    @Before
    fun setUp() {
        useCase = CheckIsRegisteredUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 등록된 사용자면 Registered 반환`() = runTest {
        // Given
        coEvery { mockRepository.checkIsRegistered() } returns RegistrationStatus.Registered

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.checkIsRegistered() }
        assertThat(result).isEqualTo(RegistrationStatus.Registered)
    }

    @Test
    fun `invoke 호출 시 등록되지 않은 사용자면 Unregistered 반환`() = runTest {
        // Given
        coEvery { mockRepository.checkIsRegistered() } returns RegistrationStatus.Unregistered

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.checkIsRegistered() }
        assertThat(result).isEqualTo(RegistrationStatus.Unregistered)
    }

    @Test
    fun `invoke 호출 시 확인 불가면 Indeterminate 반환`() = runTest {
        // Given
        coEvery { mockRepository.checkIsRegistered() } returns RegistrationStatus.Indeterminate

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.checkIsRegistered() }
        assertThat(result).isEqualTo(RegistrationStatus.Indeterminate)
    }
}
