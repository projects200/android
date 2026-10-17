package com.project200.feature.matching.place

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExercisePlace
import com.project200.domain.usecase.DeleteExercisePlaceUseCase
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.feature.matching.utils.ExercisePlaceErrorType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
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

@ExperimentalCoroutinesApi
class ExercisePlaceViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetExercisePlaceUseCase: GetExercisePlaceUseCase

    @MockK
    private lateinit var mockDeleteExercisePlaceUseCase: DeleteExercisePlaceUseCase

    private lateinit var viewModel: ExercisePlaceViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val samplePlaces =
        listOf(
            ExercisePlace(id = 1L, name = "헬스장", address = "서울시 강남구", latitude = 37.5, longitude = 127.0),
            ExercisePlace(id = 2L, name = "수영장", address = "서울시 서초구", latitude = 37.4, longitude = 127.1),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ExercisePlaceViewModel {
        return ExercisePlaceViewModel(
            getExercisePlaceUseCase = mockGetExercisePlaceUseCase,
            deleteExercisePlaceUseCase = mockDeleteExercisePlaceUseCase,
        )
    }

    @Test
    fun `getExercisePlaces - 성공 시 places가 업데이트된다`() =
        runTest {
            coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(samplePlaces)

            viewModel = createViewModel()
            viewModel.getExercisePlaces()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.places.value).isEqualTo(samplePlaces)
            assertThat(viewModel.loadState.value).isEqualTo(ExercisePlaceLoadState.READY)
        }

    @Test
    fun `getExercisePlaces - 실패 시 errorToast가 LOAD_FAILED로 설정된다`() =
        runTest {
            coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Error("ERROR", "로드 실패")

            viewModel = createViewModel()

            viewModel.errorToast.test {
                viewModel.getExercisePlaces()
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(ExercisePlaceErrorType.LOAD_FAILED)
                assertThat(viewModel.loadState.value).isEqualTo(ExercisePlaceLoadState.ERROR)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteExercisePlace - 성공 시 해당 장소가 리스트에서 제거된다`() =
        runTest {
            coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(samplePlaces)
            coEvery { mockDeleteExercisePlaceUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.getExercisePlaces()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.deleteExercisePlace(1L)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.places.value).hasSize(1)
            assertThat(viewModel.places.value.none { it.id == 1L }).isTrue()
        }

    @Test
    fun `deleteExercisePlace - 실패 시 errorToast가 DELETE_FAILED로 설정된다`() =
        runTest {
            coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(samplePlaces)
            coEvery { mockDeleteExercisePlaceUseCase(any()) } returns BaseResult.Error("ERROR", "삭제 실패")

            viewModel = createViewModel()
            viewModel.getExercisePlaces()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.errorToast.test {
                viewModel.deleteExercisePlace(1L)
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(ExercisePlaceErrorType.DELETE_FAILED)
                assertThat(viewModel.loadState.value).isEqualTo(ExercisePlaceLoadState.ERROR)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteExercisePlace - 성공 시 원본 리스트는 변경되지 않는다`() =
        runTest {
            coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(samplePlaces)
            coEvery { mockDeleteExercisePlaceUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.getExercisePlaces()
            testDispatcher.scheduler.advanceUntilIdle()

            val originalSize = samplePlaces.size

            viewModel.deleteExercisePlace(1L)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(samplePlaces).hasSize(originalSize)
            coVerify { mockDeleteExercisePlaceUseCase(1L) }
        }

    @Test
    fun `빈 초기 목록은 조회 완료 상태가 아니다`() {
        viewModel = createViewModel()
        assertThat(viewModel.places.value).isEmpty()
        assertThat(viewModel.loadState.value).isEqualTo(ExercisePlaceLoadState.LOADING)
    }

    @Test
    fun `삭제 응답을 받기 전에는 목록과 완료 상태를 변경하지 않는다`() =
        runTest {
            val deletion = CompletableDeferred<BaseResult<Unit>>()
            coEvery { mockGetExercisePlaceUseCase() } returns BaseResult.Success(samplePlaces)
            coEvery { mockDeleteExercisePlaceUseCase(1L) } coAnswers { deletion.await() }
            viewModel = createViewModel()
            viewModel.getExercisePlaces()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.deleteExercisePlace(1L)
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.loadState.value).isEqualTo(ExercisePlaceLoadState.LOADING)
            assertThat(viewModel.places.value).isEqualTo(samplePlaces)

            deletion.complete(BaseResult.Success(Unit))
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.loadState.value).isEqualTo(ExercisePlaceLoadState.READY)
            assertThat(viewModel.places.value.map { it.id }).containsExactly(2L)
        }
}
