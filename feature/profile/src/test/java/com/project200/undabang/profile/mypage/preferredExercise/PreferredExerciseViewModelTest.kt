package com.project200.undabang.profile.mypage.preferredExercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.usecase.CreatePreferredExerciseUseCase
import com.project200.domain.usecase.DeletePreferredExerciseUseCase
import com.project200.domain.usecase.EditPreferredExerciseUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.presentation.utils.SkillLevel
import com.project200.undabang.profile.utils.CompletionState
import com.project200.undabang.profile.utils.PreferredExerciseUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
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
class PreferredExerciseViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetPreferredExerciseUseCase: GetPreferredExerciseUseCase

    @MockK
    private lateinit var mockGetPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase

    @MockK
    private lateinit var mockCreatePreferredExerciseUseCase: CreatePreferredExerciseUseCase

    @MockK
    private lateinit var mockEditPreferredExerciseUseCase: EditPreferredExerciseUseCase

    @MockK
    private lateinit var mockDeletePreferredExerciseUseCase: DeletePreferredExerciseUseCase

    private lateinit var viewModel: PreferredExerciseViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val mockObserver: Observer<List<PreferredExerciseUiModel>> = mockk(relaxed = true)
    private val mockSelectedObserver: Observer<List<PreferredExerciseUiModel>> = mockk(relaxed = true)

    private val sampleExerciseTypes =
        listOf(
            PreferredExercise(
                preferredExerciseId = 100L,
                exerciseTypeId = 1L,
                name = "헬스",
                skillLevel = "",
                daysOfWeek = listOf(false, false, false, false, false, false, false),
                imageUrl = "https://example.com/health.jpg",
            ),
            PreferredExercise(
                preferredExerciseId = 0L,
                exerciseTypeId = 2L,
                name = "수영",
                skillLevel = "",
                daysOfWeek = listOf(false, false, false, false, false, false, false),
                imageUrl = "https://example.com/swimming.jpg",
            ),
        )

    private val samplePreferredExercises =
        listOf(
            PreferredExercise(
                preferredExerciseId = 100L,
                exerciseTypeId = 1L,
                name = "헬스",
                skillLevel = "BEGINNER",
                daysOfWeek = listOf(true, false, true, false, true, false, false),
                imageUrl = "https://example.com/health.jpg",
            ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            PreferredExerciseViewModel(
                getPreferredExerciseUseCase = mockGetPreferredExerciseUseCase,
                getPreferredExerciseTypesUseCase = mockGetPreferredExerciseTypesUseCase,
                createPreferredExerciseUseCase = mockCreatePreferredExerciseUseCase,
                editPreferredExerciseUseCase = mockEditPreferredExerciseUseCase,
                deletePreferredExerciseUseCase = mockDeletePreferredExerciseUseCase,
            )
        viewModel.exerciseUiModels.observeForever(mockObserver)
    }

    @Test
    fun `init - ViewModel 초기화 시 fetchInitialData가 호출된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(samplePreferredExercises)

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockGetPreferredExerciseTypesUseCase() }
            coVerify(exactly = 1) { mockGetPreferredExerciseUseCase() }
        }

    @Test
    fun `fetchInitialData - 성공 시 exerciseUiModels가 업데이트된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(samplePreferredExercises)

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val uiModels = viewModel.exerciseUiModels.value
            assertThat(uiModels).isNotNull()
            assertThat(uiModels).hasSize(2)
            assertThat(uiModels!!.first { it.exercise.exerciseTypeId == 1L }.isSelected).isTrue()
            assertThat(uiModels.first { it.exercise.exerciseTypeId == 2L }.isSelected).isFalse()
        }

    @Test
    fun `initNickname - 닉네임이 설정된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.initNickname("테스트유저")

            assertThat(viewModel.nickname).isEqualTo("테스트유저")
        }

    @Test
    fun `updateDaySelection - 해당 인덱스의 요일이 토글된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateDaySelection(1L, 0)

            val uiModel = viewModel.exerciseUiModels.value?.find { it.exercise.exerciseTypeId == 1L }
            assertThat(uiModel?.selectedDays?.get(0)).isTrue()
        }

    @Test
    fun `updateSkillLevel - 스킬 레벨이 설정된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateSkillLevel(1L, SkillLevel.BEGINNER)

            val uiModel = viewModel.exerciseUiModels.value?.find { it.exercise.exerciseTypeId == 1L }
            assertThat(uiModel?.skillLevel).isEqualTo(SkillLevel.BEGINNER)
        }

    @Test
    fun `updateSkillLevel - 동일한 스킬 레벨 선택 시 null로 토글된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateSkillLevel(1L, SkillLevel.BEGINNER)
            viewModel.updateSkillLevel(1L, SkillLevel.BEGINNER)

            val uiModel = viewModel.exerciseUiModels.value?.find { it.exercise.exerciseTypeId == 1L }
            assertThat(uiModel?.skillLevel).isNull()
        }

    @Test
    fun `updateSelectedExercise - 운동 선택이 토글된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val exerciseToSelect = sampleExerciseTypes[1]
            viewModel.updateSelectedExercise(exerciseToSelect)

            val uiModel = viewModel.exerciseUiModels.value?.find { it.exercise.exerciseTypeId == 2L }
            assertThat(uiModel?.isSelected).isTrue()
        }

    @Test
    fun `completePreferredExerciseChanges - 선택된 운동 없으면 NoneSelected 상태가 된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.completePreferredExerciseChanges()

            assertThat(viewModel.completionState.value).isEqualTo(CompletionState.NoneSelected)
        }

    @Test
    fun `completePreferredExerciseChanges - 요일이나 숙련도 누락 시 IncompleteSelection 상태가 된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateSelectedExercise(sampleExerciseTypes[0])

            viewModel.completePreferredExerciseChanges()

            assertThat(viewModel.completionState.value).isEqualTo(CompletionState.IncompleteSelection)
        }

    @Test
    fun `completePreferredExerciseChanges - 변경사항 없으면 NoChanges 상태가 된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(samplePreferredExercises)

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.completePreferredExerciseChanges()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.completionState.value).isEqualTo(CompletionState.NoChanges)
        }

    @Test
    fun `completePreferredExerciseChanges - 새 운동 추가 시 createUseCase가 호출된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())
            coEvery { mockCreatePreferredExerciseUseCase(any()) } returns BaseResult.Success(Unit)

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateSelectedExercise(sampleExerciseTypes[0])
            viewModel.updateDaySelection(1L, 0)
            viewModel.updateSkillLevel(1L, SkillLevel.BEGINNER)

            viewModel.completePreferredExerciseChanges()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockCreatePreferredExerciseUseCase(any()) }
            assertThat(viewModel.completionState.value).isEqualTo(CompletionState.Success)
        }

    @Test
    fun `completePreferredExerciseChanges - 운동 삭제 시 deleteUseCase가 호출된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(samplePreferredExercises)
            coEvery { mockDeletePreferredExerciseUseCase(any()) } returns BaseResult.Success(Unit)
            coEvery { mockCreatePreferredExerciseUseCase(any()) } returns BaseResult.Success(Unit)

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // 새 운동(수영) 선택 및 설정
            viewModel.updateSelectedExercise(sampleExerciseTypes[1])
            viewModel.updateDaySelection(2L, 0)
            viewModel.updateSkillLevel(2L, SkillLevel.BEGINNER)
            // 기존 운동(헬스) 선택 해제 -> 삭제 대상
            viewModel.updateSelectedExercise(sampleExerciseTypes[0])

            viewModel.completePreferredExerciseChanges()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockDeletePreferredExerciseUseCase(any()) }
        }

    @Test
    fun `completePreferredExerciseChanges - API 에러 시 Error 상태가 된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())
            coEvery { mockCreatePreferredExerciseUseCase(any()) } returns BaseResult.Error("ERROR", "생성 실패")

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateSelectedExercise(sampleExerciseTypes[0])
            viewModel.updateDaySelection(1L, 0)
            viewModel.updateSkillLevel(1L, SkillLevel.BEGINNER)

            viewModel.completePreferredExerciseChanges()
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.completionState.value).isInstanceOf(CompletionState.Error::class.java)
        }

    @Test
    fun `consumeCompletionState - 상태가 Idle로 리셋된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(emptyList())

            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.completePreferredExerciseChanges()
            assertThat(viewModel.completionState.value).isNotEqualTo(CompletionState.Idle)

            viewModel.consumeCompletionState()

            assertThat(viewModel.completionState.value).isEqualTo(CompletionState.Idle)
        }

    @Test
    fun `selectedExerciseUiModels - 선택된 운동만 필터링된다`() =
        runTest {
            coEvery { mockGetPreferredExerciseTypesUseCase() } returns BaseResult.Success(sampleExerciseTypes)
            coEvery { mockGetPreferredExerciseUseCase() } returns BaseResult.Success(samplePreferredExercises)

            createViewModel()
            viewModel.selectedExerciseUiModels.observeForever(mockSelectedObserver)
            testDispatcher.scheduler.advanceUntilIdle()

            val selectedModels = viewModel.selectedExerciseUiModels.value
            assertThat(selectedModels).hasSize(1)
            assertThat(selectedModels?.first()?.exercise?.exerciseTypeId).isEqualTo(1L)
        }
}
