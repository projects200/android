package com.project200.feature.matching.place

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.usecase.EditExercisePlaceUseCase
import com.project200.domain.usecase.RegisterExercisePlaceUseCase
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
class ExercisePlaceRegisterViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockRegisterExercisePlaceUseCase: RegisterExercisePlaceUseCase

    @MockK
    private lateinit var mockEditExercisePlaceUseCase: EditExercisePlaceUseCase

    private lateinit var viewModel: ExercisePlaceRegisterViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ExercisePlaceRegisterViewModel {
        return ExercisePlaceRegisterViewModel(
            registerExercisePlaceUseCase = mockRegisterExercisePlaceUseCase,
            editExercisePlace = mockEditExercisePlaceUseCase,
        )
    }

    @Test
    fun `initializePlaceInfo - 장소 정보가 초기화된다`() =
        runTest {
            viewModel = createViewModel()

            viewModel.initializePlaceInfo(
                id = -1L,
                placeName = "테스트 장소",
                placeAddress = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0,
            )

            assertThat(viewModel.customPlaceName.value).isEqualTo("테스트 장소")
        }

    @Test
    fun `onPlaceNameChanged - 장소명이 업데이트된다`() =
        runTest {
            viewModel = createViewModel()

            viewModel.initializePlaceInfo(-1L, "원래 이름", "주소", 37.5, 127.0)
            viewModel.onPlaceNameChanged("새 이름")

            assertThat(viewModel.customPlaceName.value).isEqualTo("새 이름")
        }

    @Test
    fun `confirmExercisePlace - 장소명이 비어있으면 아무것도 하지 않는다`() =
        runTest {
            viewModel = createViewModel()

            viewModel.initializePlaceInfo(-1L, "", "주소", 37.5, 127.0)
            viewModel.confirmExercisePlace()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { mockRegisterExercisePlaceUseCase(any()) }
            coVerify(exactly = 0) { mockEditExercisePlaceUseCase(any()) }
        }

    @Test
    fun `confirmExercisePlace - 새 장소(id=-1)인 경우 registerUseCase가 호출된다`() =
        runTest {
            coEvery { mockRegisterExercisePlaceUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initializePlaceInfo(-1L, "새 장소", "서울시 강남구", 37.5, 127.0)

            viewModel.confirmExercisePlace()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockRegisterExercisePlaceUseCase(any()) }
            coVerify(exactly = 0) { mockEditExercisePlaceUseCase(any()) }
        }

    @Test
    fun `confirmExercisePlace - 기존 장소(id!=-1)인 경우 editUseCase가 호출된다`() =
        runTest {
            coEvery { mockEditExercisePlaceUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initializePlaceInfo(100L, "기존 장소", "서울시 강남구", 37.5, 127.0)

            viewModel.confirmExercisePlace()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockEditExercisePlaceUseCase(any()) }
            coVerify(exactly = 0) { mockRegisterExercisePlaceUseCase(any()) }
        }

    @Test
    fun `confirmExercisePlace - 등록 성공 시 registrationResult가 업데이트된다`() =
        runTest {
            coEvery { mockRegisterExercisePlaceUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initializePlaceInfo(-1L, "새 장소", "서울시 강남구", 37.5, 127.0)

            viewModel.confirmExercisePlace()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.registrationResult.value).isInstanceOf(BaseResult.Success::class.java)
        }

    @Test
    fun `confirmExercisePlace - 수정 성공 시 editResult가 업데이트된다`() =
        runTest {
            coEvery { mockEditExercisePlaceUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initializePlaceInfo(100L, "기존 장소", "서울시 강남구", 37.5, 127.0)

            viewModel.confirmExercisePlace()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.editResult.value).isInstanceOf(BaseResult.Success::class.java)
        }

    @Test
    fun `confirmExercisePlace - 등록 실패 시 registrationResult에 Error가 설정된다`() =
        runTest {
            coEvery { mockRegisterExercisePlaceUseCase(any()) } returns BaseResult.Error("ERROR", "등록 실패")

            viewModel = createViewModel()
            viewModel.initializePlaceInfo(-1L, "새 장소", "서울시 강남구", 37.5, 127.0)

            viewModel.confirmExercisePlace()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.registrationResult.value).isInstanceOf(BaseResult.Error::class.java)
        }

    @Test
    fun `confirmExercisePlace - 변경된 이름으로 장소가 등록된다`() =
        runTest {
            coEvery { mockRegisterExercisePlaceUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initializePlaceInfo(-1L, "원래 이름", "서울시 강남구", 37.5, 127.0)
            viewModel.onPlaceNameChanged("변경된 이름")

            viewModel.confirmExercisePlace()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                mockRegisterExercisePlaceUseCase(match { it.name == "변경된 이름" })
            }
        }
}
