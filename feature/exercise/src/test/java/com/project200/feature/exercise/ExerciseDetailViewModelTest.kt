package com.project200.feature.exercise

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseImageSyncStatus
import com.project200.domain.model.ExercisePointNotice
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.model.ExerciseSyncStatus
import com.project200.domain.usecase.DeleteExerciseRecordUseCase
import com.project200.domain.usecase.ExerciseSyncUiUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.feature.exercise.detail.ExerciseDetailViewModel
import com.project200.presentation.utils.UiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val syncUi = mockk<ExerciseSyncUiUseCase>()
    private val syncStatuses = MutableStateFlow<List<ExerciseSyncStatus>>(emptyList())

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
            pictures = listOf(ExerciseRecordPicture("server:1", 1L, "http://example.com/img1.jpg", ExerciseImageSyncStatus.SYNCED)),
        )
    private val recordId = "record-123"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { syncUi.observe() } returns syncStatuses
        coEvery { syncUi.consumePointNotice(any()) } returns null
        coEvery { syncUi.retry(any()) } returns false
        savedStateHandle = SavedStateHandle().apply { set("recordId", recordId) }
        viewModel = ExerciseDetailViewModel(mockGetExerciseUseCase, mockDeleteExerciseUseCase, syncUi)
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

            assertThat(actualResult).isInstanceOf(UiState.Success::class.java)

            val actualData = (actualResult as UiState.Success).data
            assertThat(actualData).isEqualTo(sampleRecord)
            assertThat(actualData.title).isEqualTo("아침 조깅")
        }

    @Test
    fun `getExerciseRecord 호출 시 UseCase가 에러를 반환하면 UiState Error로 LiveData에 에러 상태 반영`() =
        runTest(testDispatcher) {
            // Given
            val errorResult = BaseResult.Error("500", "Server error")
            coEvery { mockGetExerciseUseCase.invoke(recordId) } returns errorResult

            // When
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockGetExerciseUseCase.invoke(recordId) }
            val actualResult = viewModel.exerciseRecord.value
            assertThat(actualResult).isInstanceOf(UiState.Error::class.java)

            val actualMessage =
                (actualResult as UiState.Error).failure.let {
                    when (it) {
                        is com.project200.presentation.utils.Failure.ServerError -> it.message
                        else -> null
                    }
                }
            assertThat(actualMessage).isEqualTo("Server error")
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
            val actualResult = viewModel.deleteResult.replayCache.firstOrNull()
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
            val actualResult = viewModel.deleteResult.replayCache.firstOrNull()
            assertThat(actualResult).isEqualTo(errorResult)
            assertThat((actualResult as BaseResult.Error).message).isEqualTo("삭제 실패")
        }

    @Test
    fun `회전이나 화면 재진입은 상세 조회를 반복하지 않는다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId) } returns BaseResult.Success(sampleRecord)
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 1) { mockGetExerciseUseCase(recordId) }
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(sampleRecord)
        }

    @Test
    fun `갱신 실패는 캐시 상세를 유지하고 알림을 한 번만 전달한다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId) } returns BaseResult.Success(sampleRecord, BaseResult.Error("401", "인증 실패"), true)
            coEvery { mockGetExerciseUseCase(recordId, refresh = true) } returns BaseResult.Error("401", "인증 실패")
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(sampleRecord)
            viewModel.refreshMessages.test {
                assertThat(awaitItem()).isEqualTo("인증 실패")
                viewModel.getExerciseRecord(recordId)
                testDispatcher.scheduler.advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `자신의 수정 후 상세는 서버 갱신 없이 로컬 값을 읽는다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId) } returns BaseResult.Success(sampleRecord)
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()
            val changed = sampleRecord.copy(title = "수정한 운동")
            coEvery { mockGetExerciseUseCase(recordId, false, true) } returns BaseResult.Success(changed, isCachedSnapshot = true)
            viewModel.onOwnWrite(recordId)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(changed)
            coVerify(exactly = 1) { mockGetExerciseUseCase(recordId, false, true) }
            coVerify(exactly = 0) { mockGetExerciseUseCase(recordId, true) }
        }

    @Test
    fun `첫 시작은 상세를 중복 조회하지 않고 복귀는 로컬 전용으로 한 번 읽는다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId) } returns BaseResult.Success(sampleRecord)
            viewModel.getExerciseRecord(recordId)
            viewModel.onScreenStarted(recordId)
            viewModel.onScreenStarted(recordId)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 1) { mockGetExerciseUseCase(recordId, false, false) }
            val changed = sampleRecord.copy(title = "복귀한 로컬 상세")
            coEvery { mockGetExerciseUseCase(recordId, false, true) } returns BaseResult.Success(changed, isCachedSnapshot = true)
            viewModel.onScreenStopped()
            viewModel.onScreenStarted(recordId)
            viewModel.onScreenStarted(recordId)
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(changed)
            coVerify(exactly = 1) { mockGetExerciseUseCase(recordId, false, true) }
            coVerify(exactly = 1) { mockGetExerciseUseCase(recordId, false, false) }
        }

    @Test
    fun `캐시 상세는 즉시 보이고 백그라운드 완료는 표시를 유지하며 명시 갱신만 바꾼다`() =
        runTest(testDispatcher) {
            val response = CompletableDeferred<BaseResult<ExerciseRecord>>()
            val updated = sampleRecord.copy(title = "서버 최신")
            coEvery { mockGetExerciseUseCase(recordId, false, false) } returns BaseResult.Success(sampleRecord, isCachedSnapshot = true)
            coEvery { mockGetExerciseUseCase(recordId, true, false) } coAnswers { response.await() }
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(sampleRecord)
            response.complete(BaseResult.Success(updated))
            testDispatcher.scheduler.runCurrent()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(sampleRecord)
            viewModel.getExerciseRecord(recordId, refresh = true, force = true)
            testDispatcher.scheduler.runCurrent()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(updated)
            coVerify(exactly = 2) { mockGetExerciseUseCase(recordId, true, false) }
        }

    @Test
    fun `초기 미적중이 늦어도 자신의 수정과 다른 기록 선택을 덮지 않는다`() =
        runTest(testDispatcher) {
            val response = CompletableDeferred<BaseResult<ExerciseRecord>>()
            coEvery { mockGetExerciseUseCase(recordId, false, false) } coAnswers { response.await() }
            val changed = sampleRecord.copy(title = "내 수정")
            coEvery { mockGetExerciseUseCase(recordId, false, true) } returns BaseResult.Success(changed, isCachedSnapshot = true)
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            viewModel.onOwnWrite(recordId)
            testDispatcher.scheduler.runCurrent()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(changed)
            val selected = sampleRecord.copy(title = "다른 기록")
            coEvery { mockGetExerciseUseCase("other", false, false) } returns BaseResult.Success(selected)
            viewModel.getExerciseRecord("other")
            testDispatcher.scheduler.runCurrent()
            response.complete(BaseResult.Success(sampleRecord))
            testDispatcher.scheduler.runCurrent()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(selected)
            coVerify(exactly = 0) { mockGetExerciseUseCase(any(), true, false) }
        }

    @Test
    fun `상태 갱신과 재시도는 상세 본문을 다시 읽지 않고 연속 탭을 막는다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId, false, false) } returns BaseResult.Success(sampleRecord)
            val scheduled = CompletableDeferred<Boolean>()
            coEvery { syncUi.retry(recordId) } coAnswers { scheduled.await() }
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            syncStatuses.value = listOf(ExerciseSyncStatus(recordId, canShowBodyRetry = true))
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.syncStatus.value.canShowBodyRetry).isTrue()
            viewModel.retryBody()
            viewModel.retryBody()
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.syncStatus.value.canShowBodyRetry).isFalse()
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isSameInstanceAs(sampleRecord)
            coVerify(exactly = 1) { syncUi.retry(recordId) }
            coVerify(exactly = 1) { mockGetExerciseUseCase(recordId, false, false) }
            scheduled.complete(true)
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.syncStatus.value.canShowBodyRetry).isFalse()
        }

    @Test
    fun `원격 삭제 안내는 본문을 유지하고 명시 확인 token만 폐기 계약에 보낸다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId, false, false) } returns BaseResult.Success(sampleRecord)
            coEvery { syncUi.discardRemoteDeleted(recordId, "account-revision") } returns BaseResult.Success(Unit)
            viewModel.getExerciseRecord(recordId)
            val status = ExerciseSyncStatus(recordId, remoteDeleted = true, remoteDeletionToken = "account-revision")
            syncStatuses.value = listOf(status)
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.syncStatus.value).isEqualTo(status)
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(sampleRecord)
            coVerify(exactly = 0) { syncUi.discardRemoteDeleted(any(), any()) }
            viewModel.discardRemoteDeleted(status)
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 1) { syncUi.discardRemoteDeleted(recordId, "account-revision") }
            assertThat(viewModel.deleteResult.replayCache.single()).isEqualTo(BaseResult.Success(Unit))
        }

    @Test
    fun `상세를 떠나 편집하는 동안 Worker 완료가 와도 점수 안내를 소비하지 않는다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId, false, false) } returns BaseResult.Success(sampleRecord)
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            viewModel.onScreenStopped()
            syncStatuses.value = listOf(ExerciseSyncStatus(recordId, complete = true))
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 0) { syncUi.consumePointNotice(any()) }
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(sampleRecord)
        }

    @Test
    fun `상세 점수 안내는 소비한 양수 영수증만 한 번 전달하고 재진입은 본문 스냅샷을 유지한다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId, false, false) } returns BaseResult.Success(sampleRecord)
            coEvery { mockGetExerciseUseCase(recordId, false, true) } returns BaseResult.Success(sampleRecord)
            coEvery { syncUi.consumePointNotice(setOf(recordId)) } returnsMany listOf(ExercisePointNotice(recordId, 3), null)
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            viewModel.refreshMessages.test {
                viewModel.onScreenStarted(recordId)
                testDispatcher.scheduler.runCurrent()
                assertThat(awaitItem()).isEqualTo("3점 획득했어요!")
                viewModel.onScreenStopped()
                viewModel.onScreenStarted(recordId)
                testDispatcher.scheduler.runCurrent()
                expectNoEvents()
            }
            assertThat((viewModel.exerciseRecord.value as UiState.Success).data).isEqualTo(sampleRecord)
            viewModel.onScreenStopped()
        }

    @Test
    fun `상세의 확정 0점은 소비하지만 획득 안내를 만들지 않는다`() =
        runTest(testDispatcher) {
            coEvery { mockGetExerciseUseCase(recordId, false, false) } returns BaseResult.Success(sampleRecord)
            coEvery { syncUi.consumePointNotice(setOf(recordId)) } returns ExercisePointNotice(recordId, 0)
            viewModel.getExerciseRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            viewModel.refreshMessages.test {
                viewModel.onScreenStarted(recordId)
                testDispatcher.scheduler.runCurrent()
                expectNoEvents()
            }
            coVerify(exactly = 1) { syncUi.consumePointNotice(setOf(recordId)) }
            viewModel.onScreenStopped()
        }
}
