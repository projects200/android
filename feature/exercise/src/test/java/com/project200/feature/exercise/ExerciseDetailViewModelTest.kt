package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.usecase.DeleteExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.feature.exercise.detail.ExerciseDetailViewModel
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class ExerciseDetailViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetExerciseUseCase: GetExerciseRecordDetailUseCase

    @MockK
    private lateinit var mockDeleteExerciseUseCase: DeleteExerciseRecordUseCase

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ExerciseDetailViewModel

    private val testDispatcher = StandardTestDispatcher()

    // 테스트용 샘플 데이터
    private val now: LocalDateTime = LocalDateTime.now()
    private val sampleRecord =
        ExerciseRecord(
            title = "아침 조깅",
            detail = "5km 여의도 공원 조깅",
            personalType = "조깅",
            startedAt = now.minusHours(1),
            endedAt = now,
            location = "여의도 공원",
            pictures = listOf(ExerciseRecordPicture(1L, "http://example.com/img1.jpg")),
        )
    private val recordId = 123L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle().apply { set("recordId", recordId) }
        viewModel = ExerciseDetailViewModel(mockGetExerciseUseCase, mockDeleteExerciseUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getExerciseRecord 호출 시 UseCase를 실행하고 성공 결과를 LiveData에 반영`() =
        runTest(testDispatcher) {
            // Given
            val successResult = BaseResult.Success(sampleRecord)
            coEvery { mockGetExerciseUseCase.invoke(recordId) } returns successResult

            // When
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockGetExerciseUseCase.invoke(recordId) }
            val actualResult = viewModel.exerciseRecord.value
            assertThat(actualResult).isEqualTo(successResult)
            assertThat((actualResult as BaseResult.Success<ExerciseRecord>).data.title).isEqualTo("아침 조깅")
        }

    @Test
    fun `getExerciseRecord 호출 시 UseCase가 에러를 반환하면 LiveData에 에러 상태 반영`() =
        runTest(testDispatcher) {
            // Given
            val errorResult = BaseResult.Error("500", "Network error")
            coEvery { mockGetExerciseUseCase.invoke(recordId) } returns errorResult

            // When
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockGetExerciseUseCase.invoke(recordId) }
            val actualResult = viewModel.exerciseRecord.value
            assertThat(actualResult).isEqualTo(errorResult)
            assertThat((actualResult as BaseResult.Error).message).isEqualTo("Network error")
        }

    @Test
    fun `deleteExerciseRecord 호출 시 UseCase를 실행하고 성공 결과를 LiveData에 반영`() =
        runTest(testDispatcher) {
            // Given
            val successResult = BaseResult.Success(Unit)
            coEvery { mockDeleteExerciseUseCase.invoke(recordId) } returns successResult

            // When
            viewModel.deleteExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockDeleteExerciseUseCase.invoke(recordId) }
            val actualResult = viewModel.deleteResult.value
            assertThat(actualResult).isEqualTo(successResult)
            assertThat(actualResult).isInstanceOf(BaseResult.Success::class.java)
        }

    @Test
    fun `deleteExerciseRecord 호출 시 UseCase가 에러를 반환하면 LiveData에 에러 상태 반영`() =
        runTest(testDispatcher) {
            // Given
            val errorResult = BaseResult.Error("DELETE_FAIL", "삭제 실패")
            coEvery { mockDeleteExerciseUseCase.invoke(recordId) } returns errorResult

            // When
            viewModel.deleteExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockDeleteExerciseUseCase.invoke(recordId) }
            val actualResult = viewModel.deleteResult.value
            assertThat(actualResult).isEqualTo(errorResult)
            assertThat((actualResult as BaseResult.Error).message).isEqualTo("삭제 실패")
        }
}
