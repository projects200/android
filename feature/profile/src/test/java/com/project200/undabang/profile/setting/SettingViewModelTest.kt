package com.project200.undabang.profile.setting

import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.LogoutUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
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
class SettingViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockLogoutUseCase: LogoutUseCase

    @MockK
    private lateinit var mockClearSessionUseCase: ClearSessionUseCase

    private lateinit var viewModel: SettingViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            SettingViewModel(
                logoutUseCase = mockLogoutUseCase,
                clearSessionUseCase = mockClearSessionUseCase,
            )
    }

    @Test
    fun `logout - logoutUseCase가 호출된다`() =
        runTest {
            coEvery { mockLogoutUseCase() } returns BaseResult.Success(Unit)

            createViewModel()

            viewModel.logout()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockLogoutUseCase() }
        }

    @Test
    fun `clearLocalSession - clearSessionUseCase가 호출된다`() =
        runTest {
            coEvery { mockClearSessionUseCase() } just Runs

            createViewModel()

            viewModel.clearLocalSession()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockClearSessionUseCase() }
        }
}
