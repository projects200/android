package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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

    private val testDispatcher = StandardTestDispatcher()

    // 테스트용 샘플 데이터 (수정된 모델 반영)
    private val today: LocalDate = LocalDate.now()
    private val tomorrow: LocalDate = today.plusDays(1)
    private val sampleList = listOf(
        ExerciseListItem(
            recordId = 1L,
            title = "테스트 운동 1",
            type = "테스트 타입 1",
            startTime = LocalDateTime.now(),
            endTime = LocalDateTime.now().plusHours(1),
            imageUrl = listOf("http://images.com/1.png", "http://images.com/2.png")
        ),
        ExerciseListItem(
            recordId = 2L,
            title = "테스트 운동 2",
            type = "테스트 타입 2",
            startTime = LocalDateTime.now().plusDays(1),
            endTime = LocalDateTime.now().plusDays(1).plusHours(1),
            imageUrl = null
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ExerciseListViewModel(mockGetExerciseRecordListUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadCurrentDateExercises 호출 시 오늘 날짜의 운동 목록을 성공적으로 로드`() = runTest(testDispatcher) {
        // Given
        // ViewModel 생성 시 currentDate는 LocalDate.now() (즉, today)로 초기화됨
        coEvery { mockGetExerciseRecordListUseCase(today) } returns BaseResult.Success(sampleList)

        // When
        viewModel.loadCurrentDateExercises() // 명시적으로 데이터 로드 함수 호출
        testDispatcher.scheduler.advanceUntilIdle() // 코루틴 작업 완료 대기

        // Then
        coVerify(exactly = 1) { mockGetExerciseRecordListUseCase(today) }
        assertThat(viewModel.currentDate.value).isEqualTo(today)
        assertThat(viewModel.exerciseList.value).isEqualTo(sampleList)
        assertThat(viewModel.toastMessage.value).isNull() // 성공 시 토스트 메시지 없음
    }

    @Test
    fun `loadCurrentDateExercises 호출 시 운동 목록 로드 실패 처리`() = runTest(testDispatcher) {
        // Given
        val errorMessage = "로드 실패"
        coEvery { mockGetExerciseRecordListUseCase(today) } returns BaseResult.Error("LOAD_ERR", errorMessage)

        // When
        viewModel.loadCurrentDateExercises() // 명시적으로 데이터 로드 함수 호출
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetExerciseRecordListUseCase(today) }
        assertThat(viewModel.currentDate.value).isEqualTo(today)
        assertThat(viewModel.exerciseList.value).isEmpty() // 실패 시 빈 리스트
        assertThat(viewModel.toastMessage.value).isEqualTo(errorMessage)
    }

    @Test
    fun `changeDate 호출 시 날짜 변경 및 운동 목록 다시 로드 성공`() = runTest(testDispatcher) {
        // Given
        coEvery { mockGetExerciseRecordListUseCase(tomorrow) } returns BaseResult.Success(sampleList)
        viewModel = ExerciseListViewModel(mockGetExerciseRecordListUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.changeDate(tomorrow.toString())
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetExerciseRecordListUseCase(tomorrow) }
        assertThat(viewModel.currentDate.value).isEqualTo(tomorrow)
        assertThat(viewModel.exerciseList.value).isEqualTo(sampleList)
    }

    @Test
    fun `changeDate 호출 시 날짜 변경 및 로드 실패 처리`() = runTest(testDispatcher) {
        // Given
        val errorMessage = "변경 실패"
        coEvery { mockGetExerciseRecordListUseCase(today) } returns BaseResult.Success(sampleList)
        coEvery { mockGetExerciseRecordListUseCase(tomorrow) } returns BaseResult.Error("CHANGE_ERR", errorMessage)
        viewModel = ExerciseListViewModel(mockGetExerciseRecordListUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.changeDate(tomorrow.toString())
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockGetExerciseRecordListUseCase(tomorrow) }
        assertThat(viewModel.currentDate.value).isEqualTo(tomorrow)
        assertThat(viewModel.exerciseList.value).isEmpty()
        assertThat(viewModel.toastMessage.value).isEqualTo(errorMessage)
    }
}