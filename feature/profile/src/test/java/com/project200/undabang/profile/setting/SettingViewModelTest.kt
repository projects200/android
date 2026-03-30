package com.project200.undabang.profile.setting

import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.LogoutUseCase
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
class SettingViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockLogoutUseCase: LogoutUseCase

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
        viewModel = SettingViewModel(
            logoutUseCase = mockLogoutUseCase
        )
    }

    @Test
    fun `logout - logoutUseCase가 호출된다`() = runTest {
        coEvery { mockLogoutUseCase() } returns BaseResult.Success(Unit)

        createViewModel()

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockLogoutUseCase() }
    }
}
