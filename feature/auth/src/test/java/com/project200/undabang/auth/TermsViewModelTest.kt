package com.project200.undabang.auth

import com.google.common.truth.Truth.assertThat
import com.project200.undabang.auth.register.TermsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class TermsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: TermsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TermsViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기 상태에서 서비스 약관은 체크되지 않음`() {
        assertThat(viewModel.serviceChecked.value).isFalse()
    }

    @Test
    fun `초기 상태에서 개인정보 약관은 체크되지 않음`() {
        assertThat(viewModel.privacyChecked.value).isFalse()
    }

    @Test
    fun `초기 상태에서 필수 약관 전체 동의는 false`() {
        assertThat(viewModel.isAllRequiredChecked.value).isFalse()
    }

    @Test
    fun `toggleService - 서비스 약관 토글 시 상태가 변경된다`() {
        assertThat(viewModel.serviceChecked.value).isFalse()

        viewModel.toggleService()

        assertThat(viewModel.serviceChecked.value).isTrue()
    }

    @Test
    fun `toggleService - 두 번 토글 시 원래 상태로 돌아온다`() {
        viewModel.toggleService()
        viewModel.toggleService()

        assertThat(viewModel.serviceChecked.value).isFalse()
    }

    @Test
    fun `togglePrivacy - 개인정보 약관 토글 시 상태가 변경된다`() {
        assertThat(viewModel.privacyChecked.value).isFalse()

        viewModel.togglePrivacy()

        assertThat(viewModel.privacyChecked.value).isTrue()
    }

    @Test
    fun `togglePrivacy - 두 번 토글 시 원래 상태로 돌아온다`() {
        viewModel.togglePrivacy()
        viewModel.togglePrivacy()

        assertThat(viewModel.privacyChecked.value).isFalse()
    }

    @Test
    fun `isAllRequiredChecked - 서비스 약관만 체크하면 false`() =
        runTest {
            viewModel.toggleService()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.isAllRequiredChecked.value).isFalse()
        }

    @Test
    fun `isAllRequiredChecked - 개인정보 약관만 체크하면 false`() =
        runTest {
            viewModel.togglePrivacy()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.isAllRequiredChecked.value).isFalse()
        }

    @Test
    fun `isAllRequiredChecked - 모든 필수 약관 체크 시 true`() =
        runTest {
            viewModel.toggleService()
            viewModel.togglePrivacy()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.isAllRequiredChecked.value).isTrue()
        }

    @Test
    fun `isAllRequiredChecked - 모든 약관 체크 후 하나 해제하면 false`() =
        runTest {
            viewModel.toggleService()
            viewModel.togglePrivacy()
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.isAllRequiredChecked.value).isTrue()

            viewModel.toggleService()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.isAllRequiredChecked.value).isFalse()
        }
}
