package com.project200.feature.timer

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.CustomTimer
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

@ExperimentalCoroutinesApi
class CustomTimerFormViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

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

    private val sampleStep =
        Step(
            order = 0,
            time = 60,
            name = "스텝1",
        )

    private val sampleTimer =
        CustomTimer(
            localId = "local-1",
            name = "테스트 타이머",
            steps = listOf(sampleStep),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel =
            CustomTimerFormViewModel(
                validateCustomTimerUseCase,
                getCustomTimerUseCase,
                createCustomTimerUseCase,
                editCustomTimerUseCase,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init - 초기 상태에서 Footer 아이템만 존재한다`() {
        // Then
        assertThat(viewModel.uiState.value.listItems).hasSize(1)
    }

    @Test
    fun `isEditMode - 생성 모드에서는 false`() {
        // Then
        assertThat(viewModel.isEditMode).isFalse()
    }

    @Test
    fun `loadData - localId로 로드하면 수정 모드가 되고 기존 데이터를 불러온다`() =
        runTest {
            // Given
            coEvery { getCustomTimerUseCase("local-1") } returns BaseResult.Success(sampleTimer)

            // When
            viewModel.loadData("local-1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.isEditMode).isTrue()
            assertThat(viewModel.uiState.value.title).isEqualTo("테스트 타이머")
        }

    @Test
    fun `loadData - 조회 실패 시 토스트를 표시한다`() =
        runTest {
            // Given
            coEvery { getCustomTimerUseCase("local-1") } returns BaseResult.Error("ERROR", "Failed")

            // When & Then
            viewModel.toast.test {
                viewModel.loadData("local-1")
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(ToastMessageType.GET_ERROR)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `updateTimerTitle - 타이머 제목을 업데이트한다`() {
        // When
        viewModel.updateTimerTitle("새 타이머")

        // Then
        assertThat(viewModel.uiState.value.title).isEqualTo("새 타이머")
    }

    @Test
    fun `addStep - 스텝을 추가한다`() {
        // Given
        viewModel.updateNewStepName("테스트 스텝")

        // When
        viewModel.addStep()

        // Then
        assertThat(viewModel.uiState.value.listItems).hasSize(2)
    }

    @Test
    fun `removeStep - order로 스텝을 제거한다`() {
        // Given
        viewModel.updateNewStepName("테스트 스텝")
        viewModel.addStep()
        val order = viewModel.getStepsWithFinalOrder().firstOrNull()?.order ?: return

        // When
        viewModel.removeStep(order)

        // Then
        assertThat(viewModel.uiState.value.listItems).hasSize(1)
    }

    @Test
    fun `submitCustomTimer - 유효성 검사 실패 시 토스트를 표시한다`() =
        runTest {
            // Given
            every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.EmptyTitle

            // When & Then
            viewModel.toast.test {
                viewModel.submitCustomTimer()
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(ToastMessageType.EMPTY_TITLE)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submitCustomTimer - 스텝 없으면 NO_STEPS 토스트`() =
        runTest {
            // Given
            viewModel.updateTimerTitle("타이머")
            every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.NoSteps

            // When & Then
            viewModel.toast.test {
                viewModel.submitCustomTimer()
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(ToastMessageType.NO_STEPS)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submitCustomTimer - 생성 모드에서 성공 시 UseCase가 돌려준 localId로 submitResult를 업데이트한다`() =
        runTest {
            // Given
            viewModel.updateTimerTitle("새 타이머")
            viewModel.updateNewStepName("스텝1")
            viewModel.addStep()
            every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.Success
            coEvery { createCustomTimerUseCase(any(), any()) } returns BaseResult.Success("new-local-id")

            // When & Then
            viewModel.submitResult.test {
                viewModel.submitCustomTimer()
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo("new-local-id")
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { createCustomTimerUseCase(any(), any()) }
        }

    @Test
    fun `submitCustomTimer - 생성 실패 시 CREATE_ERROR 토스트`() =
        runTest {
            // Given
            viewModel.updateTimerTitle("새 타이머")
            viewModel.updateNewStepName("스텝1")
            viewModel.addStep()
            every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.Success
            coEvery { createCustomTimerUseCase(any(), any()) } returns BaseResult.Error("ERROR", "Failed")

            // When & Then
            viewModel.toast.test {
                viewModel.submitCustomTimer()
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(ToastMessageType.CREATE_ERROR)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submitCustomTimer - 수정 모드에서 변경 없으면 NO_CHANGES 토스트`() =
        runTest {
            // Given
            coEvery { getCustomTimerUseCase("local-1") } returns BaseResult.Success(sampleTimer)
            viewModel.loadData("local-1")
            testDispatcher.scheduler.advanceUntilIdle()
            every { validateCustomTimerUseCase(any(), any()) } returns CustomTimerValidationResult.Success

            // When & Then
            viewModel.toast.test {
                viewModel.submitCustomTimer()
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(ToastMessageType.NO_CHANGES)
                cancelAndIgnoreRemainingEvents()
            }
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
