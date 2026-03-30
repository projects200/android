package com.project200.feature.exercise.share

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.feature.exercise.utils.StickerTransformInfo
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
class ExerciseShareEditViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockGetExerciseRecordDetailUseCase: GetExerciseRecordDetailUseCase

    private lateinit var viewModel: ExerciseShareEditViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleRecord = ExerciseRecord(
        title = "오늘의 운동",
        detail = "하체 운동 완료",
        personalType = "STRENGTH",
        startedAt = LocalDateTime.of(2025, 1, 15, 10, 0),
        endedAt = LocalDateTime.of(2025, 1, 15, 11, 30),
        location = "홈짐",
        pictures = listOf(
            ExerciseRecordPicture(id = 1L, url = "https://example.com/pic1.jpg"),
            ExerciseRecordPicture(id = 2L, url = "https://example.com/pic2.jpg")
        )
    )

    private val sampleRecordNoPictures = ExerciseRecord(
        title = "오늘의 운동",
        detail = "하체 운동 완료",
        personalType = "STRENGTH",
        startedAt = LocalDateTime.of(2025, 1, 15, 10, 0),
        endedAt = LocalDateTime.of(2025, 1, 15, 11, 30),
        location = "홈짐",
        pictures = null
    )

    private val sampleTransformInfo = StickerTransformInfo(
        translationXRatio = 0.5f,
        translationYRatio = 0.5f,
        stickerWidthRatio = 0.45f,
        rotationDegrees = 0f
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ExerciseShareEditViewModel {
        return ExerciseShareEditViewModel(
            getExerciseRecordDetailUseCase = mockGetExerciseRecordDetailUseCase
        )
    }

    @Test
    fun `init - 기본 테마는 DARK이다`() = runTest {
        // Given & When
        viewModel = createViewModel()

        // Then
        assertThat(viewModel.selectedTheme.value).isEqualTo(StickerTheme.DARK)
    }

    @Test
    fun `init - 초기 로딩 상태는 true이다`() = runTest {
        // Given & When
        viewModel = createViewModel()

        // Then
        assertThat(viewModel.isLoading.value).isTrue()
    }

    @Test
    fun `loadExerciseRecord - 성공하면 운동 기록이 로드된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()

        // When
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.exerciseRecord.value).isEqualTo(sampleRecord)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `loadExerciseRecord - 성공하면 첫 번째 사진이 배경 이미지로 설정된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()

        // When
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.backgroundImageUrl.value).isEqualTo("https://example.com/pic1.jpg")
    }

    @Test
    fun `loadExerciseRecord - 사진이 없으면 배경 이미지는 null이다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecordNoPictures)

        viewModel = createViewModel()

        // When
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.backgroundImageUrl.value).isNull()
    }

    @Test
    fun `loadExerciseRecord - 실패해도 로딩이 완료된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Error("ERROR", "로드 실패")

        viewModel = createViewModel()

        // When
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertThat(viewModel.exerciseRecord.value).isNull()
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `loadExerciseRecord - 올바른 recordId로 UseCase가 호출된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()

        // When
        viewModel.loadExerciseRecord(123L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockGetExerciseRecordDetailUseCase(123L) }
    }

    @Test
    fun `selectTheme - 테마 변경이 반영된다`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.selectTheme(StickerTheme.LIGHT)

        // Then
        assertThat(viewModel.selectedTheme.value).isEqualTo(StickerTheme.LIGHT)
    }

    @Test
    fun `selectTheme - 모든 테마로 변경 가능하다`() = runTest {
        // Given
        viewModel = createViewModel()

        // When & Then
        viewModel.selectTheme(StickerTheme.DARK)
        assertThat(viewModel.selectedTheme.value).isEqualTo(StickerTheme.DARK)

        viewModel.selectTheme(StickerTheme.LIGHT)
        assertThat(viewModel.selectedTheme.value).isEqualTo(StickerTheme.LIGHT)

        viewModel.selectTheme(StickerTheme.MINIMAL)
        assertThat(viewModel.selectedTheme.value).isEqualTo(StickerTheme.MINIMAL)
    }

    @Test
    fun `requestShare - 운동 기록이 있으면 shareEvent가 발생한다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        viewModel.shareEvent.test {
            viewModel.requestShare(sampleTransformInfo)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.record).isEqualTo(sampleRecord)
            assertThat(event.theme).isEqualTo(StickerTheme.DARK)
            assertThat(event.transformInfo).isEqualTo(sampleTransformInfo)
        }
    }

    @Test
    fun `requestShare - 운동 기록이 없으면 shareEvent가 발생하지 않는다`() = runTest {
        // Given
        viewModel = createViewModel()

        // When
        viewModel.requestShare(sampleTransformInfo)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.shareEvent.test {
            expectNoEvents()
        }
    }

    @Test
    fun `requestShare - 선택된 테마가 이벤트에 포함된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.selectTheme(StickerTheme.MINIMAL)

        // When & Then
        viewModel.shareEvent.test {
            viewModel.requestShare(sampleTransformInfo)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.theme).isEqualTo(StickerTheme.MINIMAL)
        }
    }

    @Test
    fun `requestShare - 로딩 상태가 true로 설정된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        viewModel.isLoading.test {
            // loadExerciseRecord 완료 후 초기값 false
            assertThat(awaitItem()).isFalse()

            viewModel.requestShare(sampleTransformInfo)
            testDispatcher.scheduler.advanceUntilIdle()

            // requestShare에서 true로 설정됨
            assertThat(awaitItem()).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShareCompleted - 로딩 상태가 false로 설정된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.requestShare(sampleTransformInfo)

        // When
        viewModel.onShareCompleted()

        // Then
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `stickerState - 운동 기록과 테마가 결합된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()

        // When
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.stickerState.test {
            val state = awaitItem()
            assertThat(state.record).isEqualTo(sampleRecord)
            assertThat(state.theme).isEqualTo(StickerTheme.DARK)
        }
    }

    @Test
    fun `stickerState - 테마 변경 시 새로운 상태가 emit된다`() = runTest {
        // Given
        coEvery { mockGetExerciseRecordDetailUseCase(any()) } returns BaseResult.Success(sampleRecord)

        viewModel = createViewModel()
        viewModel.loadExerciseRecord(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        viewModel.stickerState.test {
            val initialState = awaitItem()
            assertThat(initialState.theme).isEqualTo(StickerTheme.DARK)

            viewModel.selectTheme(StickerTheme.LIGHT)
            val newState = awaitItem()
            assertThat(newState.theme).isEqualTo(StickerTheme.LIGHT)
        }
    }
}
