package com.project200.undabang.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.undabang.auth.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class LoginViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var checkIsRegisteredUseCase: CheckIsRegisteredUseCase

    private lateinit var viewModel: LoginViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(checkIsRegisteredUseCase)
    }

    @Test
    fun `checkIsRegistered - 가입된 사용자면 Registered 결과를 반환한다`() =
        runTest {
            // Given
            coEvery { checkIsRegisteredUseCase() } returns RegistrationStatus.Registered
            viewModel = createViewModel()

            // When
            viewModel.checkIsRegistered()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Registered)
            coVerify(exactly = 1) { checkIsRegisteredUseCase() }
        }

    @Test
    fun `checkIsRegistered - 미가입 사용자면 Unregistered 결과를 반환한다`() =
        runTest {
            // Given
            coEvery { checkIsRegisteredUseCase() } returns RegistrationStatus.Unregistered
            viewModel = createViewModel()

            // When
            viewModel.checkIsRegistered()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Unregistered)
            coVerify(exactly = 1) { checkIsRegisteredUseCase() }
        }

    @Test
    fun `checkIsRegistered - 확인 불가면 Indeterminate 결과를 반환한다`() =
        runTest {
            // Given
            coEvery { checkIsRegisteredUseCase() } returns RegistrationStatus.Indeterminate
            viewModel = createViewModel()

            // When
            viewModel.checkIsRegistered()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Indeterminate)
            coVerify(exactly = 1) { checkIsRegisteredUseCase() }
        }
}
