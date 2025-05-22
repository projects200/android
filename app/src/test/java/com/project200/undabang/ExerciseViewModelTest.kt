package com.project200.undabang

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.feature.exercise.ExerciseViewModel
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
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class ExerciseViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockUseCase: GetExerciseRecordDetailUseCase

    // SavedStateHandle은 mockk()로 직접 생성하거나 @MockK 사용 가능
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ExerciseViewModel

    private val testDispatcher = StandardTestDispatcher()

    // 테스트용 샘플 데이터
    private val now: LocalDateTime = LocalDateTime.now()
    private val sampleRecord = ExerciseRecord(
        title = "아침 조깅",
        detail = "5km 여의도 공원 조깅",
        personalType = "조깅",
        startedAt = now.minusHours(1),
        endedAt = now,
        location = "여의도 공원",
        pictureUrls = listOf("http://example.com/img1.jpg")
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
    fun `getExerciseRecord 호출 시 UseCase를 실행하고 성공 결과를 LiveData에 반영`() = runTest(testDispatcher) {
        // Given
        val recordId = 123
        savedStateHandle = SavedStateHandle().apply { set("recordId", recordId) }
        viewModel = ExerciseViewModel(savedStateHandle, mockUseCase)

        val successResult = BaseResult.Success(sampleRecord)

        coEvery { mockUseCase.invoke(recordId) } returns successResult

        // When
        viewModel.getExerciseRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockUseCase.invoke(recordId) } // 정확히 1번 호출되었는지 검증

        val actualResult = viewModel.exerciseRecord.value
        assertThat(actualResult).isEqualTo(successResult)
        assertThat((actualResult as BaseResult.Success).data.title).isEqualTo("아침 조깅")
    }

    @Test
    fun `getExerciseRecord 호출 시 UseCase가 에러를 반환하면 LiveData에 에러 상태 반영`() = runTest(testDispatcher) {
        // Given
        val recordId = 456
        savedStateHandle = SavedStateHandle().apply { set("recordId", recordId) }
        viewModel = ExerciseViewModel(savedStateHandle, mockUseCase)

        val errorResult = BaseResult.Error("500", "Network error")
        coEvery { mockUseCase.invoke(recordId) } returns errorResult

        // When
        viewModel.getExerciseRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockUseCase.invoke(recordId) }
        val actualResult = viewModel.exerciseRecord.value
        assertThat(actualResult).isEqualTo(errorResult)
        assertThat((actualResult as BaseResult.Error).message).isEqualTo("Network error")
    }
}