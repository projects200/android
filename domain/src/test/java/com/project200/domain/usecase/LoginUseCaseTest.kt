package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
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
class LoginUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: AuthRepository

    private lateinit var useCase: LoginUseCase

    @Before
    fun setUp() {
        useCase = LoginUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 repository login 호출 및 성공 결과 반환`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.login() } returns successResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.login() }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `invoke 호출 시 repository login 호출 및 실패 결과 반환`() = runTest {
        // Given
        val errorResult = BaseResult.Error("ERR", "Login failed")
        coEvery { mockRepository.login() } returns errorResult

        // When
        val result = useCase()

        // Then
        coVerify(exactly = 1) { mockRepository.login() }
        assertThat(result).isEqualTo(errorResult)
    }
}
