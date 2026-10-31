package com.project200.undabang.profile.setting

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SessionExitReason
import com.project200.domain.usecase.ClearSessionUseCase
import com.project200.domain.usecase.LogoutUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val logout = mockk<LogoutUseCase>()
    private val clear = mockk<ClearSessionUseCase>()
    private lateinit var viewModel: SettingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { clear(SessionExitReason.USER_INITIATED) } just Runs
        viewModel = SettingViewModel(logout, clear)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `원격 실패와 반복 확인에도 로컬 정리 뒤 완료는 한 번만 전달한다`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { logout() } coAnswers {
            release.await()
            BaseResult.Error("NETWORK_ERROR", "offline")
        }
        var browserCalls = 0
        assertThat(viewModel.logoutBlocking.value).isFalse()
        viewModel.confirmLogout { browserCalls++ }
        viewModel.confirmLogout { browserCalls++ }
        assertThat(viewModel.logoutBlocking.value).isTrue()
        dispatcher.scheduler.runCurrent()
        assertThat(viewModel.logoutCompleted.value).isNull()
        coVerify(exactly = 0) { clear(any()) }
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(browserCalls).isEqualTo(1)
        assertThat(viewModel.logoutCompleted.value).isTrue()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        coVerify(exactly = 1) { clear(SessionExitReason.USER_INITIATED) }
        viewModel.consumeLogoutCompletion()
        assertThat(viewModel.logoutBlocking.value).isFalse()
        viewModel.confirmLogout { browserCalls++ }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.logoutCompleted.value).isNull()
        coVerify(exactly = 1) { logout() }
    }

    @Test
    fun `원격 예외와 브라우저 실행 실패도 명시적 세션 정리를 건너뛰지 않는다`() = runTest {
        coEvery { logout() } throws IllegalStateException("remote")
        var cleaned = false
        coEvery { clear(SessionExitReason.USER_INITIATED) } answers { cleaned = true }
        viewModel.confirmLogout { throw IllegalStateException("browser") }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(cleaned).isTrue()
        assertThat(viewModel.logoutCompleted.value).isTrue()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        viewModel.consumeLogoutCompletion()
        assertThat(viewModel.logoutBlocking.value).isFalse()
    }

    @Test
    fun `로컬 정리가 끝나기 전에는 로그인 이동용 완료를 전달하지 않는다`() = runTest {
        coEvery { logout() } returns BaseResult.Success(Unit)
        val release = CompletableDeferred<Unit>()
        coEvery { clear(SessionExitReason.USER_INITIATED) } coAnswers { release.await() }
        viewModel.confirmLogout { }
        dispatcher.scheduler.runCurrent()
        assertThat(viewModel.logoutCompleted.value).isNull()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        viewModel.consumeLogoutCompletion()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.logoutCompleted.value).isFalse()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        viewModel.consumeLogoutCompletion()
        assertThat(viewModel.logoutBlocking.value).isFalse()
    }

    @Test
    fun `로컬 정리 실패도 완료를 처리한 뒤에만 화면 차단을 해제한다`() = runTest {
        coEvery { logout() } returns BaseResult.Success(Unit)
        coEvery { clear(SessionExitReason.USER_INITIATED) } throws IllegalStateException("storage")
        viewModel.confirmLogout { }
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.logoutCompleted.value).isTrue()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        viewModel.consumeLogoutCompletion()
        assertThat(viewModel.logoutCompleted.value).isNull()
        assertThat(viewModel.logoutBlocking.value).isFalse()
    }

    @Test
    fun `뷰 관찰자가 없는 동안 끝난 로그아웃도 새 관찰자 처리까지 차단을 유지한다`() = runTest {
        coEvery { logout() } returns BaseResult.Success(Unit)
        val release = CompletableDeferred<Unit>()
        coEvery { clear(SessionExitReason.USER_INITIATED) } coAnswers { release.await() }
        viewModel.confirmLogout { }
        dispatcher.scheduler.runCurrent()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        var completion: Boolean? = null
        val observer = androidx.lifecycle.Observer<Boolean?> { completion = it }
        viewModel.logoutCompleted.observeForever(observer)
        assertThat(completion).isFalse()
        assertThat(viewModel.logoutBlocking.value).isTrue()
        viewModel.consumeLogoutCompletion()
        assertThat(viewModel.logoutBlocking.value).isFalse()
        viewModel.logoutCompleted.removeObserver(observer)
    }
}
