package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseListItem
import com.project200.domain.usecase.GetExerciseRecordListUseCase
import com.project200.feature.exercise.list.ExerciseListViewModel
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
import java.time.LocalDate
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class ExerciseListViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetExerciseRecordListUseCase: GetExerciseRecordListUseCase

    private lateinit var viewModel: ExerciseListViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private val testDispatcher = StandardTestDispatcher()

    private val today: LocalDate = LocalDate.now()
    private val tomorrow: LocalDate = today.plusDays(1)
    private val sampleList = listOf(
        ExerciseListItem(
            recordId = 1L, title = "테스트 운동 1", type = "테스트 타입 1",
            startTime = LocalDateTime.now(), endTime = LocalDateTime.now().plusHours(1),
            imageUrl = listOf("http://images.com/1.png")
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ViewModel 초기화 시 SavedStateHandle에 날짜가 없으면 오늘 날짜로 초기화`() {
        // Given
        savedStateHandle = SavedStateHandle()

        // When
        viewModel = ExerciseListViewModel(savedStateHandle, mockGetExerciseRecordListUseCase)

        // Then
        assertThat(viewModel.currentDate.value).isEqualTo(today)
        // init에서는 load를 호출하지 않으므로 use case는 호출되지 않아야 함
        coVerify(exactly = 0) { mockGetExerciseRecordListUseCase(any()) }
    }

    @Test
    fun `ViewModel 초기화 시 SavedStateHandle에 날짜가 있으면 해당 날짜로 초기화`() = runTest {
        // Given
        val initialDate = LocalDate.of(2025, 1, 1)
        savedStateHandle = SavedStateHandle().apply { set("date", initialDate) }

        // When
        viewModel = ExerciseListViewModel(savedStateHandle, mockGetExerciseRecordListUseCase)

        // Then
        assertThat(viewModel.currentDate.value).isEqualTo(initialDate)
        coVerify(exactly = 0) { mockGetExerciseRecordListUseCase(any()) }

        // When (데이터 로드 수동 호출)
        coEvery { mockGetExerciseRecordListUseCase(initialDate) } returns BaseResult.Success(sampleList)
        viewModel.loadCurrentDateExercises()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then (데이터가 정상적으로 로드됨)
        coVerify(exactly = 1) { mockGetExerciseRecordListUseCase(initialDate) }
        assertThat(viewModel.exerciseList.value).isEqualTo(sampleList)
    }

    @Test
    fun `loadCurrentDateExercises 호출 시 오늘 날짜의 운동 목록을 성공적으로 로드`() = runTest(testDispatcher) {
        savedStateHandle = SavedStateHandle() // today로 초기화
        viewModel = ExerciseListViewModel(savedStateHandle, mockGetExerciseRecordListUseCase)
        coEvery { mockGetExerciseRecordListUseCase(today) } returns BaseResult.Success(sampleList)

        viewModel.loadCurrentDateExercises()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockGetExerciseRecordListUseCase(today) }
        assertThat(viewModel.exerciseList.value).isEqualTo(sampleList)
    }

    @Test
    fun `changeDate 호출 시 날짜 변경 및 운동 목록 다시 로드 성공`() = runTest(testDispatcher) {
        savedStateHandle = SavedStateHandle()
        viewModel = ExerciseListViewModel(savedStateHandle, mockGetExerciseRecordListUseCase)
        coEvery { mockGetExerciseRecordListUseCase(tomorrow) } returns BaseResult.Success(sampleList)

        viewModel.changeDate(tomorrow.toString())
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockGetExerciseRecordListUseCase(tomorrow) }
        assertThat(viewModel.currentDate.value).isEqualTo(tomorrow)
        assertThat(viewModel.exerciseList.value).isEqualTo(sampleList)
    }
}