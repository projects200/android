package com.project200.undabang.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.LoginUseCase
import com.project200.domain.usecase.SendFcmTokenUseCase
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
    private lateinit var loginUseCase: LoginUseCase

    @MockK
    private lateinit var sendFcmTokenUseCase: SendFcmTokenUseCase

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
        return LoginViewModel(loginUseCase, sendFcmTokenUseCase)
    }

    @Test
    fun `checkIsRegistered - 로그인 성공 시 Success 결과를 반환한다`() = runTest {
        // Given
        coEvery { loginUseCase() } returns BaseResult.Success(Unit)
        viewModel = createViewModel()

        // When
        viewModel.checkIsRegistered()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.loginResult.value).isInstanceOf(BaseResult.Success::class.java)
        coVerify(exactly = 1) { loginUseCase() }
    }

    @Test
    fun `checkIsRegistered - 로그인 실패 시 Error 결과를 반환한다`() = runTest {
        // Given
        coEvery { loginUseCase() } returns BaseResult.Error("ERROR", "Login failed")
        viewModel = createViewModel()

        // When
        viewModel.checkIsRegistered()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val result = viewModel.loginResult.value
        assertThat(result).isInstanceOf(BaseResult.Error::class.java)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("ERROR")
    }

    @Test
    fun `sendFcmToken - FCM 토큰 전송 성공 시 Success 이벤트를 발생시킨다`() = runTest {
        // Given
        coEvery { loginUseCase() } returns BaseResult.Success(Unit)
        coEvery { sendFcmTokenUseCase() } returns BaseResult.Success(Unit)
        viewModel = createViewModel()

        // When
        viewModel.sendFcmToken()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.fcmTokenEvent.value).isInstanceOf(BaseResult.Success::class.java)
        coVerify(exactly = 1) { sendFcmTokenUseCase() }
    }

    @Test
    fun `sendFcmToken - FCM 토큰 전송 실패 시 Error 이벤트를 발생시킨다`() = runTest {
        // Given
        coEvery { loginUseCase() } returns BaseResult.Success(Unit)
        coEvery { sendFcmTokenUseCase() } returns BaseResult.Error("FCM_ERROR", "FCM token send failed")
        viewModel = createViewModel()

        // When
        viewModel.sendFcmToken()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val result = viewModel.fcmTokenEvent.value
        assertThat(result).isInstanceOf(BaseResult.Error::class.java)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("FCM_ERROR")
    }
}
