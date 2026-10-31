package com.project200.undabang.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.RegistrationStatus
import com.project200.domain.usecase.CheckIsRegisteredUseCase
import com.project200.domain.usecase.LoginSessionUseCase
import com.project200.undabang.auth.login.LoginViewModel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val check = mockk<CheckIsRegisteredUseCase>()
    private val session = mockk<LoginSessionUseCase>()
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { session.resumePendingLogin() } returns null
        coEvery { session.beginLogin() } just Runs
        coEvery { session.cancelLogin() } just Runs
        viewModel = LoginViewModel(check, session)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `승인 대기는 진입 결과를 내지 않고 오래된 승인과 중복 승인을 무시한다`() = runTest {
        coEvery { check() } returns RegistrationStatus.ConfirmationRequired("current")
        val release = CompletableDeferred<Unit>()
        coEvery { session.confirmAccountAdoption("current") } coAnswers {
            release.await()
            RegistrationStatus.Registered
        }
        viewModel.checkIsRegistered()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.ConfirmationRequired("current"))
        viewModel.confirmAccountAdoption("stale")
        coVerify(exactly = 0) { session.confirmAccountAdoption(any()) }
        viewModel.confirmAccountAdoption("current")
        viewModel.confirmAccountAdoption("current")
        dispatcher.scheduler.runCurrent()
        assertThat(viewModel.registrationResult.value).isNull()
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Registered)
        coVerify(exactly = 1) { session.confirmAccountAdoption("current") }
        viewModel.consumeResult()
        assertThat(viewModel.registrationResult.value).isNull()
    }

    @Test
    fun `가입 진행도 승인 후에만 허용하고 취소는 승인 경로를 실행하지 않는다`() = runTest {
        coEvery { check() } returns RegistrationStatus.ConfirmationRequired("signup")
        viewModel.checkIsRegistered()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.cancelLogin("old-dialog")
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.ConfirmationRequired("signup"))
        viewModel.cancelLogin("signup")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Cancelled)
        coVerify(exactly = 0) { session.confirmAccountAdoption(any()) }
        coVerify(exactly = 1) { session.cancelLogin() }
        coEvery { session.confirmAccountAdoption("signup") } returns RegistrationStatus.Unregistered
        viewModel.checkIsRegistered()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmAccountAdoption("signup")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Unregistered)
    }

    @Test
    fun `승인 확인 실패는 진입을 허용하지 않고 재시도로 새 승인을 요구한다`() = runTest {
        coEvery { check() } returnsMany listOf(
            RegistrationStatus.ConfirmationRequired("first"), RegistrationStatus.ConfirmationRequired("retry"),
        )
        coEvery { session.confirmAccountAdoption("first") } throws IllegalStateException("storage")
        viewModel.checkIsRegistered()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmAccountAdoption("first")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Indeterminate)
        viewModel.checkIsRegistered()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.ConfirmationRequired("retry"))
        viewModel.confirmAccountAdoption("first")
        coVerify(exactly = 0) { session.confirmAccountAdoption("retry") }
    }

    @Test
    fun `재생성된 ViewModel은 영속 대기를 다시 확인하고 자동 승인하지 않는다`() = runTest {
        coEvery { session.resumePendingLogin() } returns RegistrationStatus.ConfirmationRequired("restored")
        val restored = LoginViewModel(check, session)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(restored.registrationResult.value).isEqualTo(RegistrationStatus.ConfirmationRequired("restored"))
        restored.confirmAccountAdoption("before-recreation")
        restored.cancelLogin("before-recreation")
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { session.cancelLogin() }
        coVerify(exactly = 0) { session.confirmAccountAdoption(any()) }
        restored.cancelLogin("restored")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(restored.registrationResult.value).isEqualTo(RegistrationStatus.Cancelled)
        coVerify(exactly = 1) { session.cancelLogin() }
    }

    @Test
    fun `연속 로그인 탭은 요청을 중복 시작하지 않고 취소는 늦은 자격 증명 콜백을 무효화한다`() = runTest {
        assertThat(viewModel.beginLogin()).isTrue()
        val first = viewModel.loginGeneration()
        assertThat(viewModel.beginLogin()).isFalse()
        assertThat(viewModel.isLoginActive(first)).isTrue()
        coVerify(exactly = 1) { session.beginLogin() }
        viewModel.cancelLogin()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.isLoginActive(first)).isFalse()
        assertThat(viewModel.beginLogin()).isTrue()
        assertThat(viewModel.isLoginActive(first)).isFalse()
        assertThat(viewModel.isLoginActive(viewModel.loginGeneration())).isTrue()
    }

    @Test
    fun `서버 확인 중 취소는 늦은 성공으로 화면을 전환하지 않는다`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { check() } coAnswers {
            release.await()
            RegistrationStatus.Registered
        }
        viewModel.checkIsRegistered()
        dispatcher.scheduler.runCurrent()
        viewModel.cancelLogin()
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Cancelled)
        coVerify(exactly = 1) { session.cancelLogin() }
    }

    @Test
    fun `로그인 게이트 저장 대기 중 취소하면 늦은 완료로 인증 화면을 열지 않는다`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { session.beginLogin() } coAnswers {
            entered.complete(Unit)
            release.await()
        }
        val beginning = async { viewModel.beginLogin() }
        dispatcher.scheduler.runCurrent()
        entered.await()
        viewModel.cancelLogin()
        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(beginning.await()).isFalse()
        assertThat(viewModel.isLoginActive(viewModel.loginGeneration())).isFalse()
        assertThat(viewModel.registrationResult.value).isEqualTo(RegistrationStatus.Cancelled)
    }

    @Test
    fun `취소한 A의 늦은 인증 응답은 새 로그인이나 토큰 저장 권한을 만들지 않는다`() = runTest {
        assertThat(viewModel.beginLogin()).isTrue()
        val first = viewModel.loginGeneration()
        assertThat(viewModel.bindAuthorizationRequest(first, "state-a")).isTrue()
        viewModel.cancelLogin()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.consumeAuthorizationResponse("state-a")).isNull()
        assertThat(viewModel.isLoginActive(first)).isFalse()
        assertThat(viewModel.hasLoginToCancel).isFalse()
        coVerify(exactly = 1) { session.beginLogin() }
    }

    @Test
    fun `B를 기다릴 때 A의 응답과 state 없는 응답은 B의 인증 권한을 소비하지 않는다`() = runTest {
        viewModel.beginLogin()
        viewModel.bindAuthorizationRequest(viewModel.loginGeneration(), "state-a")
        viewModel.cancelLogin()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.beginLogin()
        val second = viewModel.loginGeneration()
        assertThat(viewModel.bindAuthorizationRequest(second, "state-b")).isTrue()

        assertThat(viewModel.consumeAuthorizationResponse("state-a")).isNull()
        assertThat(viewModel.consumeAuthorizationResponse(null)).isNull()
        assertThat(viewModel.consumeAuthorizationResponse("")).isNull()
        assertThat(viewModel.consumeAuthorizationResponse("state-b")).isEqualTo(second)
        assertThat(viewModel.loginGeneration()).isEqualTo(second)
        coVerify(exactly = 2) { session.beginLogin() }
    }

    @Test
    fun `인증 응답은 한 번만 소비하고 교환 중 취소는 기존 콜백도 무효화한다`() = runTest {
        viewModel.beginLogin()
        val generation = viewModel.loginGeneration()
        viewModel.bindAuthorizationRequest(generation, "state")
        assertThat(viewModel.consumeAuthorizationResponse("state")).isEqualTo(generation)
        assertThat(viewModel.consumeAuthorizationResponse("state")).isNull()
        assertThat(viewModel.isLoginActive(generation)).isTrue()

        viewModel.cancelLogin()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.isLoginActive(generation)).isFalse()
        coVerify(exactly = 1) { session.beginLogin() }
    }

    @Test
    fun `발신 요청을 보유하지 않은 새 ViewModel은 수신 응답으로 로그인을 시작하지 않는다`() = runTest {
        val restored = LoginViewModel(check, session)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(restored.consumeAuthorizationResponse("before-process-death")).isNull()
        assertThat(restored.isLoginActive(restored.loginGeneration())).isFalse()
        assertThat(restored.hasLoginToCancel).isFalse()
        coVerify(exactly = 0) { session.beginLogin() }
    }

    @Test
    fun `브라우저 실행 전 시작 취소는 정리 완료 뒤 다시 로그인을 허용한다`() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { session.cancelLogin() } coAnswers { release.await() }
        viewModel.beginLogin()
        val generation = viewModel.loginGeneration()
        viewModel.cancelAuthorizationInitiation(generation)
        dispatcher.scheduler.runCurrent()
        assertThat(viewModel.isLoginActive(generation)).isFalse()
        assertThat(viewModel.beginLogin()).isFalse()
        assertThat(viewModel.hasLoginToCancel).isTrue()

        release.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.hasLoginToCancel).isFalse()
        assertThat(viewModel.beginLogin()).isTrue()
        assertThat(viewModel.loginGeneration()).isNotEqualTo(generation)
    }

    @Test
    fun `영속 게이트 저장 중 코루틴 취소는 취소를 전파하고 다음 시작을 막지 않는다`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { session.beginLogin() } coAnswers {
            entered.complete(Unit)
            release.await()
        }
        val beginning = async { viewModel.beginLogin() }
        dispatcher.scheduler.runCurrent()
        entered.await()
        beginning.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(beginning.isCancelled).isTrue()
        assertThat(viewModel.hasLoginToCancel).isFalse()
        assertThat(viewModel.isLoginActive(viewModel.loginGeneration())).isFalse()
        coVerify(exactly = 1) { session.cancelLogin() }

        coEvery { session.beginLogin() } just Runs
        assertThat(viewModel.beginLogin()).isTrue()
    }

    @Test
    fun `이전 시작 코루틴의 취소는 새 시도나 이미 실행한 브라우저를 취소하지 않는다`() = runTest {
        viewModel.beginLogin()
        val first = viewModel.loginGeneration()
        viewModel.cancelAuthorizationInitiation(first)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.beginLogin()
        val second = viewModel.loginGeneration()
        viewModel.cancelAuthorizationInitiation(first)
        assertThat(viewModel.isLoginActive(second)).isTrue()
        assertThat(viewModel.bindAuthorizationRequest(first, "state-a")).isFalse()
        assertThat(viewModel.bindAuthorizationRequest(second, "state-b")).isTrue()
        viewModel.cancelAuthorizationInitiation(second)
        assertThat(viewModel.consumeAuthorizationResponse("state-b")).isEqualTo(second)
        viewModel.cancelAuthorizationInitiation(second)
        assertThat(viewModel.isLoginActive(second)).isTrue()
        coVerify(exactly = 1) { session.cancelLogin() }
    }

    @Test
    fun `유휴 Back은 위임하고 로그인과 승인 대기 중에만 취소할 작업이 있다`() = runTest {
        assertThat(viewModel.hasLoginToCancel).isFalse()
        viewModel.beginLogin()
        assertThat(viewModel.hasLoginToCancel).isTrue()
        viewModel.cancelLogin()
        assertThat(viewModel.hasLoginToCancel).isTrue()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.hasLoginToCancel).isFalse()

        coEvery { check() } returns RegistrationStatus.ConfirmationRequired("pending")
        viewModel.checkIsRegistered()
        assertThat(viewModel.hasLoginToCancel).isTrue()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.hasLoginToCancel).isTrue()
        viewModel.cancelLogin("pending")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.hasLoginToCancel).isFalse()
    }
}
