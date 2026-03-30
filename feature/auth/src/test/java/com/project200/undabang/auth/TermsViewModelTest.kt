package com.project200.undabang.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.undabang.auth.register.TermsViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TermsViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: TermsViewModel

    @Before
    fun setUp() {
        viewModel = TermsViewModel()
    }

    @Test
    fun `초기 상태에서 서비스 약관은 체크되지 않음`() {
        // Then
        assertThat(viewModel.serviceChecked.value).isFalse()
    }

    @Test
    fun `초기 상태에서 개인정보 약관은 체크되지 않음`() {
        // Then
        assertThat(viewModel.privacyChecked.value).isFalse()
    }

    @Test
    fun `초기 상태에서 필수 약관 전체 동의는 false`() {
        // Then
        assertThat(viewModel.isAllRequiredChecked.value).isFalse()
    }

    @Test
    fun `toggleService - 서비스 약관 토글 시 상태가 변경된다`() {
        // Given
        assertThat(viewModel.serviceChecked.value).isFalse()

        // When
        viewModel.toggleService()

        // Then
        assertThat(viewModel.serviceChecked.value).isTrue()
    }

    @Test
    fun `toggleService - 두 번 토글 시 원래 상태로 돌아온다`() {
        // When
        viewModel.toggleService()
        viewModel.toggleService()

        // Then
        assertThat(viewModel.serviceChecked.value).isFalse()
    }

    @Test
    fun `togglePrivacy - 개인정보 약관 토글 시 상태가 변경된다`() {
        // Given
        assertThat(viewModel.privacyChecked.value).isFalse()

        // When
        viewModel.togglePrivacy()

        // Then
        assertThat(viewModel.privacyChecked.value).isTrue()
    }

    @Test
    fun `togglePrivacy - 두 번 토글 시 원래 상태로 돌아온다`() {
        // When
        viewModel.togglePrivacy()
        viewModel.togglePrivacy()

        // Then
        assertThat(viewModel.privacyChecked.value).isFalse()
    }

    @Test
    fun `isAllRequiredChecked - 서비스 약관만 체크하면 false`() {
        // When
        viewModel.toggleService()

        // Then
        assertThat(viewModel.isAllRequiredChecked.value).isFalse()
    }

    @Test
    fun `isAllRequiredChecked - 개인정보 약관만 체크하면 false`() {
        // When
        viewModel.togglePrivacy()

        // Then
        assertThat(viewModel.isAllRequiredChecked.value).isFalse()
    }

    @Test
    fun `isAllRequiredChecked - 모든 필수 약관 체크 시 true`() {
        // When
        viewModel.toggleService()
        viewModel.togglePrivacy()

        // Then
        assertThat(viewModel.isAllRequiredChecked.value).isTrue()
    }

    @Test
    fun `isAllRequiredChecked - 모든 약관 체크 후 하나 해제하면 false`() {
        // Given
        viewModel.toggleService()
        viewModel.togglePrivacy()
        assertThat(viewModel.isAllRequiredChecked.value).isTrue()

        // When
        viewModel.toggleService()

        // Then
        assertThat(viewModel.isAllRequiredChecked.value).isFalse()
    }
}
