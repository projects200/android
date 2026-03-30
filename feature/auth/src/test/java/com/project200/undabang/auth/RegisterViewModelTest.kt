package com.project200.undabang.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.SignUpUseCase
import com.project200.domain.usecase.ValidateNicknameUseCase
import com.project200.undabang.auth.register.RegisterViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import java.time.LocalDate

@ExperimentalCoroutinesApi
class RegisterViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var signUpUseCase: SignUpUseCase

    @MockK
    private lateinit var validateNicknameUseCase: ValidateNicknameUseCase

    private lateinit var viewModel: RegisterViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RegisterViewModel(signUpUseCase, validateNicknameUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateNickname - 닉네임 업데이트 시 LiveData가 변경된다`() {
        // Given
        val testNickname = "테스트닉네임"

        // When
        viewModel.updateNickname(testNickname)

        // Then
        assertThat(viewModel.nickname.value).isEqualTo(testNickname)
    }

    @Test
    fun `updateBirth - 생년월일 업데이트 시 LiveData가 변경된다`() {
        // Given
        val testBirth = "1990-01-01"

        // When
        viewModel.updateBirth(testBirth)

        // Then
        assertThat(viewModel.birth.value).isEqualTo(testBirth)
    }

    @Test
    fun `selectGender - 성별 선택 시 LiveData가 변경된다`() {
        // Given
        val testGender = "MALE"

        // When
        viewModel.selectGender(testGender)

        // Then
        assertThat(viewModel.gender.value).isEqualTo(testGender)
    }

    @Test
    fun `isFormValid - 모든 필드가 입력되면 true를 반환한다`() {
        // Given & When
        viewModel.updateNickname("테스트")
        viewModel.updateBirth("1990-01-01")
        viewModel.selectGender("MALE")

        // Then
        assertThat(viewModel.isFormValid.value).isTrue()
    }

    @Test
    fun `isFormValid - 닉네임이 비어있으면 false를 반환한다`() {
        // Given & When
        viewModel.updateNickname("")
        viewModel.updateBirth("1990-01-01")
        viewModel.selectGender("MALE")

        // Then
        assertThat(viewModel.isFormValid.value).isFalse()
    }

    @Test
    fun `isFormValid - 생년월일이 비어있으면 false를 반환한다`() {
        // Given & When
        viewModel.updateNickname("테스트")
        viewModel.selectGender("MALE")

        // Then
        assertThat(viewModel.isFormValid.value).isFalse()
    }

    @Test
    fun `isFormValid - 성별이 선택되지 않으면 false를 반환한다`() {
        // Given & When
        viewModel.updateNickname("테스트")
        viewModel.updateBirth("1990-01-01")

        // Then
        assertThat(viewModel.isFormValid.value).isFalse()
    }

    @Test
    fun `signUp - 닉네임 유효성 검사 실패 시 INVALID_NICKNAME 에러를 반환한다`() =
        runTest {
            // Given
            every { validateNicknameUseCase(any()) } returns false
            viewModel.updateNickname("잘못된닉네임")
            viewModel.updateBirth("1990-01-01")
            viewModel.selectGender("MALE")

            // When
            viewModel.signUp()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.signUpResult.value
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            assertThat((result as BaseResult.Error).errorCode).isEqualTo(RegisterViewModel.ERROR_CODE_INVALID_NICKNAME)
        }

    @Test
    fun `signUp - 성별이 null이면 FORM_INCOMPLETE 에러를 반환한다`() =
        runTest {
            // Given
            every { validateNicknameUseCase(any()) } returns true
            viewModel.updateNickname("테스트닉네임")
            viewModel.updateBirth("1990-01-01")
            // gender is not set

            // When
            viewModel.signUp()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.signUpResult.value
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            assertThat((result as BaseResult.Error).errorCode).isEqualTo(RegisterViewModel.FORM_INCOMPLETE)
        }

    @Test
    fun `signUp - 생년월일이 null이면 FORM_INCOMPLETE 에러를 반환한다`() =
        runTest {
            // Given
            every { validateNicknameUseCase(any()) } returns true
            viewModel.updateNickname("테스트닉네임")
            viewModel.selectGender("MALE")
            // birth is not set

            // When
            viewModel.signUp()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.signUpResult.value
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            assertThat((result as BaseResult.Error).errorCode).isEqualTo(RegisterViewModel.FORM_INCOMPLETE)
        }

    @Test
    fun `signUp - 회원가입 성공 시 Success 결과를 반환한다`() =
        runTest {
            // Given
            every { validateNicknameUseCase(any()) } returns true
            coEvery { signUpUseCase(any(), any(), any()) } returns BaseResult.Success(Unit)
            viewModel.updateNickname("테스트닉네임")
            viewModel.updateBirth("1990-01-01")
            viewModel.selectGender("MALE")

            // When
            viewModel.signUp()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.signUpResult.value).isInstanceOf(BaseResult.Success::class.java)
            coVerify(exactly = 1) { signUpUseCase("MALE", "테스트닉네임", LocalDate.of(1990, 1, 1)) }
        }

    @Test
    fun `signUp - 회원가입 실패 시 Error 결과를 반환한다`() =
        runTest {
            // Given
            every { validateNicknameUseCase(any()) } returns true
            coEvery { signUpUseCase(any(), any(), any()) } returns BaseResult.Error("SIGNUP_ERROR", "SignUp failed")
            viewModel.updateNickname("테스트닉네임")
            viewModel.updateBirth("1990-01-01")
            viewModel.selectGender("MALE")

            // When
            viewModel.signUp()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val result = viewModel.signUpResult.value
            assertThat(result).isInstanceOf(BaseResult.Error::class.java)
            assertThat((result as BaseResult.Error).errorCode).isEqualTo("SIGNUP_ERROR")
        }
}
