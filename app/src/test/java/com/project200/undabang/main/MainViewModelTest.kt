package com.project200.undabang.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.usecase.CheckForUpdateUseCase
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.domain.usecase.LoginUseCase
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
class MainViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockCheckForUpdateUseCase: CheckForUpdateUseCase

    @MockK
    private lateinit var mockCheckIsRegisteredUseCase: CheckIsRegisteredUseCase

    @MockK
    private lateinit var mockLoginUseCase: LoginUseCase

    private lateinit var viewModel: MainViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        return MainViewModel(
            checkForUpdateUseCase = mockCheckForUpdateUseCase,
            checkIsRegisteredUseCase = mockCheckIsRegisteredUseCase,
            loginUseCase = mockLoginUseCase
        )
    }

    @Test
    fun `checkForUpdate - 업데이트가 필요하면 UpdateAvailable 결과를 반환한다`() = runTest {
        // Given
        val updateResult = UpdateCheckResult.UpdateAvailable(isForceUpdate = false)
        coEvery { mockCheckForUpdateUseCase() } returns Result.success(updateResult)

        viewModel = createViewModel()

        // When
        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.updateCheckResult.value).isEqualTo(updateResult)
        coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
    }

    @Test
    fun `checkForUpdate - 강제 업데이트가 필요하면 isForceUpdate가 true인 결과를 반환한다`() = runTest {
        // Given
        val updateResult = UpdateCheckResult.UpdateAvailable(isForceUpdate = true)
        coEvery { mockCheckForUpdateUseCase() } returns Result.success(updateResult)

        viewModel = createViewModel()

        // When
        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val result = viewModel.updateCheckResult.value
        assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        assertThat((result as UpdateCheckResult.UpdateAvailable).isForceUpdate).isTrue()
    }

    @Test
    fun `checkForUpdate - 업데이트가 불필요하면 NoUpdateNeeded 결과를 반환한다`() = runTest {
        // Given
        coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)

        viewModel = createViewModel()

        // When
        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.updateCheckResult.value).isEqualTo(UpdateCheckResult.NoUpdateNeeded)
    }

    @Test
    fun `checkForUpdate - 이미 체크했으면 다시 호출하지 않는다`() = runTest {
        // Given
        coEvery { mockCheckForUpdateUseCase() } returns Result.success(UpdateCheckResult.NoUpdateNeeded)

        viewModel = createViewModel()
        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockCheckForUpdateUseCase() }
    }

    @Test
    fun `checkForUpdate - 실패해도 크래시하지 않는다`() = runTest {
        // Given
        coEvery { mockCheckForUpdateUseCase() } returns Result.failure(Exception("Network error"))

        viewModel = createViewModel()

        // When
        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.updateCheckResult.value).isNull()
    }

    @Test
    fun `login - 성공하면 Success 결과를 반환한다`() = runTest {
        // Given
        coEvery { mockLoginUseCase() } returns BaseResult.Success(Unit)
        coEvery { mockCheckIsRegisteredUseCase() } returns true

        viewModel = createViewModel()

        // When
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.loginResult.value).isInstanceOf(BaseResult.Success::class.java)
        coVerify { mockLoginUseCase() }
        coVerify { mockCheckIsRegisteredUseCase() }
    }

    @Test
    fun `login - 실패하면 Error 결과를 반환한다`() = runTest {
        // Given
        coEvery { mockLoginUseCase() } returns BaseResult.Error("ERROR", "로그인 실패")
        coEvery { mockCheckIsRegisteredUseCase() } returns false

        viewModel = createViewModel()

        // When
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.loginResult.value).isInstanceOf(BaseResult.Error::class.java)
    }

    @Test
    fun `showBottomNavigation - 호출하면 true가 설정된다`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.showBottomNavigation()

        // Then
        assertThat(viewModel.showBottomNavigation.value).isTrue()
    }

    @Test
    fun `hideBottomNavigation - 호출하면 false가 설정된다`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.hideBottomNavigation()

        // Then
        assertThat(viewModel.showBottomNavigation.value).isFalse()
    }

    @Test
    fun `showBottomNavigation과 hideBottomNavigation - 토글이 정상 동작한다`() = runTest {
        // Given
        viewModel = createViewModel()

        // When & Then
        viewModel.showBottomNavigation()
        assertThat(viewModel.showBottomNavigation.value).isTrue()

        viewModel.hideBottomNavigation()
        assertThat(viewModel.showBottomNavigation.value).isFalse()

        viewModel.showBottomNavigation()
        assertThat(viewModel.showBottomNavigation.value).isTrue()
    }
}
