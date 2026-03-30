package com.project200.feature.timer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimerValidationResult
import com.project200.domain.model.Step
import com.project200.domain.usecase.CreateCustomTimerUseCase
import com.project200.domain.usecase.EditCustomTimerUseCase
import com.project200.domain.usecase.GetCustomTimerUseCase
import com.project200.domain.usecase.ValidateCustomTimerUseCase
import com.project200.feature.timer.custom.CustomTimerFormViewModel
import com.project200.feature.timer.custom.ToastMessageType
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
import com.project200.domain.model.CustomTimer

@ExperimentalCoroutinesApi
class CustomTimerFormViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var validateCustomTimerUseCase: ValidateCustomTimerUseCase

    @MockK
    private lateinit var getCustomTimerUseCase: GetCustomTimerUseCase

    @MockK
    private lateinit var createCustomTimerUseCase: CreateCustomTimerUseCase

    @MockK
    private lateinit var editCustomTimerUseCase: EditCustomTimerUseCase

    private lateinit var viewModel: CustomTimerFormViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleStep = Step(
        id = 1L,
        order = 0,
        time = 60,
        name = "스텝1"
    )

    private val sampleTimer = CustomTimer(
        id = 1L,
        name = "테스트 타이머",
        steps = listOf(sampleStep)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CustomTimerFormViewModel(
            validateCustomTimerUseCase,
            getCustomTimerUseCase,
            createCustomTimerUseCase,
            editCustomTimerUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init - 초기 상태에서 Footer 아이템만 존재한다`() {
        // Then
        assertThat(viewModel.uiState.value?.listItems).hasSize(1)
    }

    @Test
    fun `isEditMode - 생성 모드에서는 false`() {
        // Then
        assertThat(viewModel.isEditMode).isFalse()
    }

    @Test
    fun `loadData - 수정 모드로 로드하면 기존 데이터를 불러온다`() = runTest {
        // Given
        coEvery { getCustomTimerUseCase(1L) } returns BaseResult.Success(sampleTimer)

        // When
        viewModel.loadData(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.isEditMode).isTrue()
        assertThat(viewModel.uiState.value?.title).isEqualTo("테스트 타이머")
    }

    @Test
    fun `loadData - 조회 실패 시 토스트를 표시한다`() = runTest {
        // Given
        coEvery { getCustomTimerUseCase(1L) } returns BaseResult.Error("ERROR", "Failed")

        // When
        viewModel.loadData(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.toast.value).isEqualTo(ToastMessageType.GET_ERROR)
    }

    @Test
    fun `updateTimerTitle - 타이머 제목을 업데이트한다`() {
        // When
        viewModel.updateTimerTitle("새 타이머")

        // Then
        assertThat(viewModel.uiState.value?.title).isEqualTo("새 타이머")
    }

    @Test
    fun `updateNewStepName - 새 스텝 이름을 업데이트한다`() {
        // When
        viewModel.updateNewStepName("스텝 이름")

        // Then
        val footer = viewModel.uiState.value?.listItems?.lastOrNull()
        assertThat(footer).isNotNull()
    }

    @Test
    fun `updateNewStepTime - 새 스텝 시간을 업데이트한다`() {
        // When
        viewModel.updateNewStepTime(120)

        // Then
        val footer = viewModel.uiState.value?.listItems?.lastOrNull()
        assertThat(footer).isNotNull()
    }

    @Test
    fun `addStep - 스텝을 추가한다`() {
        // Given
        viewModel.updateNewStepName("테스트 스텝")

        // When
        viewModel.addStep()

        // Then
        assertThat(viewModel.uiState.value?.listItems).hasSize(2)
    }

    @Test
    fun `removeStep - 스텝을 제거한다`() {
        // Given
        viewModel.updateNewStepName("테스트 스텝")
        viewModel.addStep()
        val stepId = viewModel.getStepsWithFinalOrder().firstOrNull()?.id ?: return

        // When
        viewModel.removeStep(stepId)

        // Then
        assertThat(viewModel.uiState.value?.listItems).hasSize(1)
    }

    @Test
    fun `submitCustomTimer - 유효성 검사 실패 시 토스트를 표시한다`() = runTest {
        // Given
        every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.EmptyTitle

        // When
        viewModel.submitCustomTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.toast.value).isEqualTo(ToastMessageType.EMPTY_TITLE)
    }

    @Test
    fun `submitCustomTimer - 스텝 없으면 NO_STEPS 토스트`() = runTest {
        // Given
        viewModel.updateTimerTitle("타이머")
        every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.NoSteps

        // When
        viewModel.submitCustomTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.toast.value).isEqualTo(ToastMessageType.NO_STEPS)
    }

    @Test
    fun `submitCustomTimer - 생성 모드에서 성공 시 submitResult를 업데이트한다`() = runTest {
        // Given
        viewModel.updateTimerTitle("새 타이머")
        viewModel.updateNewStepName("스텝1")
        viewModel.addStep()
        every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.Success
        coEvery { createCustomTimerUseCase(any(), any()) } returns BaseResult.Success(1L)

        // When
        viewModel.submitCustomTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.submitResult.value).isEqualTo(1L)
        coVerify { createCustomTimerUseCase(any(), any()) }
    }

    @Test
    fun `submitCustomTimer - 생성 실패 시 CREATE_ERROR 토스트`() = runTest {
        // Given
        viewModel.updateTimerTitle("새 타이머")
        viewModel.updateNewStepName("스텝1")
        viewModel.addStep()
        every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.Success
        coEvery { createCustomTimerUseCase(any(), any()) } returns BaseResult.Error("ERROR", "Failed")

        // When
        viewModel.submitCustomTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.toast.value).isEqualTo(ToastMessageType.CREATE_ERROR)
    }

    @Test
    fun `submitCustomTimer - 수정 모드에서 변경 없으면 NO_CHANGES 토스트`() = runTest {
        // Given
        coEvery { getCustomTimerUseCase(1L) } returns BaseResult.Success(sampleTimer)
        viewModel.loadData(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.Success

        // When
        viewModel.submitCustomTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.toast.value).isEqualTo(ToastMessageType.NO_CHANGES)
    }

    @Test
    fun `getStepsWithFinalOrder - order가 인덱스에 맞게 부여된다`() {
        // Given
        viewModel.updateNewStepName("스텝1")
        viewModel.addStep()
        viewModel.updateNewStepName("스텝2")
        viewModel.addStep()

        // When
        val steps = viewModel.getStepsWithFinalOrder()

        // Then
        assertThat(steps[0].order).isEqualTo(0)
        assertThat(steps[1].order).isEqualTo(1)
    }
}
