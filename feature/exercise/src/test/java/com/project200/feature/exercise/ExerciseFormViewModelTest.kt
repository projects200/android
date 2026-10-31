package com.project200.feature.exercise

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.common.utils.ClockProvider
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseImageSyncStatus
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.LocalExerciseSaveResult
import com.project200.domain.model.SubmissionResult
import com.project200.domain.model.ValidWindow
import com.project200.domain.repository.ExerciseRecordRepository
import com.project200.domain.usecase.CreateExerciseRecordUseCase
import com.project200.domain.usecase.GetExercisePlaceUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.domain.usecase.GetExpectedScoreInfoUseCase
import com.project200.domain.usecase.GetPreferredExerciseTypesUseCase
import com.project200.domain.usecase.GetPreferredExerciseUseCase
import com.project200.domain.usecase.UpdateExerciseRecordUseCase
import com.project200.feature.exercise.form.ExerciseFormViewModel
import com.project200.feature.exercise.form.ExerciseImageListItem
import com.project200.feature.exercise.utils.ScoreGuidanceState
import com.project200.undabang.feature.exercise.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
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
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class ExerciseFormViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetDetailUseCase: GetExerciseRecordDetailUseCase

    @MockK
    private lateinit var mockCreateUseCase: CreateExerciseRecordUseCase

    @MockK
    private lateinit var mockUpdateUseCase: UpdateExerciseRecordUseCase

    @MockK
    private lateinit var mockExpectedScoreInfoUseCase: GetExpectedScoreInfoUseCase

    @MockK
    private lateinit var mockGetExercisePlaceUseCase: GetExercisePlaceUseCase

    @MockK
    private lateinit var mockGetPreferredExerciseUseCase: GetPreferredExerciseUseCase

    @MockK
    private lateinit var mockGetPreferredExerciseTypesUseCase: GetPreferredExerciseTypesUseCase

    @MockK
    private lateinit var mockClockProvider: ClockProvider

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ExerciseFormViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val now: LocalDateTime = LocalDateTime.of(2025, 12, 23, 10, 0, 0)
    private val sampleRecord =
        ExerciseRecord(
            title = "테스트 운동",
            detail = "테스트 상세",
            personalType = "테스트",
            startedAt = now.minusHours(5),
            endedAt = now.minusHours(4),
            location = "테스트 장소",
            pictures = null,
        )
    private val recordId = "record-123"
    private val imageUriString = "content://image/1"

    private val sampleExpectedScoreInfo =
        ExpectedScoreInfo(
            pointsPerExercise = 3,
            currentUserScore = 90,
            maxScore = 100,
            validWindow =
                ValidWindow(
                    startDateTime = now.minusDays(2),
                    endDateTime = now,
                ),
            earnableScoreDays =
                listOf(
                    now.minusDays(2).toLocalDate(),
                    now.minusDays(1).toLocalDate(),
                    now.toLocalDate(),
                ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockClockProvider.localDateTimeNow() } returns now
        coEvery { mockExpectedScoreInfoUseCase.invoke() } returns BaseResult.Success(sampleExpectedScoreInfo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setupViewModelForCreateMode(createUseCase: CreateExerciseRecordUseCase = mockCreateUseCase) {
        savedStateHandle = SavedStateHandle().apply { set("recordId", "") }
        viewModel =
            ExerciseFormViewModel(
                getExerciseRecordDetailUseCase = mockGetDetailUseCase,
                createExerciseRecordUseCase = createUseCase,
                updateExerciseRecordUseCase = mockUpdateUseCase,
                getExpectedScoreInfoUseCase = mockExpectedScoreInfoUseCase,
                getExercisePlaceUseCase = mockGetExercisePlaceUseCase,
                getPreferredExerciseUseCase = mockGetPreferredExerciseUseCase,
                getPreferredExerciseTypesUseCase = mockGetPreferredExerciseTypesUseCase,
                clockProvider = mockClockProvider,
            )
    }

    private fun setupViewModelForEditMode(
        record: ExerciseRecord = sampleRecord,
        updateUseCase: UpdateExerciseRecordUseCase = mockUpdateUseCase,
    ) {
        savedStateHandle = SavedStateHandle().apply { set("recordId", recordId) }
        viewModel =
            ExerciseFormViewModel(
                getExerciseRecordDetailUseCase = mockGetDetailUseCase,
                createExerciseRecordUseCase = mockCreateUseCase,
                updateExerciseRecordUseCase = updateUseCase,
                getExpectedScoreInfoUseCase = mockExpectedScoreInfoUseCase,
                getExercisePlaceUseCase = mockGetExercisePlaceUseCase,
                getPreferredExerciseUseCase = mockGetPreferredExerciseUseCase,
                getPreferredExerciseTypesUseCase = mockGetPreferredExerciseTypesUseCase,
                clockProvider = mockClockProvider,
            )
        // 수정 모드 테스트를 위해 초기 데이터를 미리 로드합니다.
        coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(record)
        viewModel.loadInitialRecord(recordId)
        runTest { testDispatcher.scheduler.advanceUntilIdle() }
    }

    /** 폼 상태 관리 및 유효성 검증 테스트
     * 이 테스트들은 ViewModel의 상태 관리 및 유효성 검증 로직을 검증합니다.
     */

    @Test
    fun `loadInitialRecord - 생성 모드일 때 ViewModel이 올바르게 초기화된다`() {
        // Given: 빈 recordId인 생성 모드
        setupViewModelForCreateMode()

        // When: 초기 데이터 로드 실행
        viewModel.loadInitialRecord("")

        // Then: 수정과 관련된 데이터는 null 또는 초기 상태여야 함
        assertThat(viewModel.initialDataLoaded.value).isNull()
        assertThat(viewModel.startTime.value).isEqualTo(now.minusHours(1))
        assertThat(viewModel.imageItems.value).hasSize(1) // AddButton만 존재
    }

    @Test
    fun `setEndTime - 시작 시간보다 이전의 종료 시간을 설정하면 시작 시간이 1시간 전으로 보정된다`() {
        setupViewModelForCreateMode()

        // Given: 시작 시간을 09:00으로 설정
        val start = now.minusHours(2) // 08:00
        viewModel.setStartTime(start)

        // When: 종료 시간을 07:00으로 설정 (시작 시간보다 빠름)
        val targetEnd = now.minusHours(3)
        viewModel.setEndTime(targetEnd)

        // Then: 보정 로직에 의해 시작 시간이 종료 시간 1시간 전인 07:00으로 바뀜
        assertThat(viewModel.endTime.value).isEqualTo(targetEnd)
        assertThat(viewModel.startTime.value).isEqualTo(targetEnd.minusHours(1))
    }

    @Test
    fun `setStartTime - 종료 시간보다 늦은 시작 시간을 설정하면 종료 시간이 1시간 후로 보정된다`() {
        setupViewModelForCreateMode()

        // Given: 종료 시간이 07:00인 상태
        viewModel.setEndTime(now.minusHours(3))

        // When: 시작 시간을 08:00으로 설정 (종료 시간보다 늦음)
        val targetStart = now.minusHours(2)
        viewModel.setStartTime(targetStart)

        // Then: 종료 시간이 시작 시간 1시간 후인 09:00으로 밀려남
        assertThat(viewModel.startTime.value).isEqualTo(targetStart)
        assertThat(viewModel.endTime.value).isEqualTo(targetStart.plusHours(1))
    }

    @Test
    fun `removeImage - 새로 추가했던 이미지를 삭제하면 기존 사진 삭제 의도는 남기지 않는다`() {
        // Given: 수정 모드에서 새로운 이미지를 추가한 상태
        setupViewModelForEditMode()
        val newImageUri = mockk<Uri>()
        val newImageItem = ExerciseImageListItem.NewImageItem(newImageUri)
        viewModel.addImage(listOf(newImageUri))
        assertThat(viewModel.imageItems.value).contains(newImageItem)

        // When: 새로 추가했던 이미지를 다시 삭제
        viewModel.removeImage(newImageItem)

        // Then: 제거한 신규 URI와 기존 사진 삭제 의도를 모두 보내지 않습니다.
        coEvery { mockUpdateUseCase(any(), any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))
        viewModel.submitRecord(recordId, "제목 변경", "타입", "장소", "상세") // hasChanges=true 만들기
        runTest { testDispatcher.scheduler.advanceUntilIdle() }

        coVerify {
            mockUpdateUseCase(
                recordId = recordId,
                input = match { it.removedImageLocalIds.isEmpty() && it.addedImageUris.isEmpty() },
            )
        }
    }

    @Test
    fun `hasContentChanges - 다른 내용은 그대로고 시간만 변경되어도 변경으로 감지한다`() =
        runTest {
            setupViewModelForEditMode()
            coEvery { mockUpdateUseCase(any(), any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))

            // When: 종료 시간 변경 (06:00 -> 07:00). 현재 시간 10시보다 전이므로 정상 반영됨
            val newEnd = sampleRecord.endedAt.plusHours(1)
            viewModel.setEndTime(newEnd)

            viewModel.submitRecord(recordId, sampleRecord.title, sampleRecord.personalType, sampleRecord.location, sampleRecord.detail)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 변경이 감지되어 USE CASE가 호출되어야 함
            coVerify(exactly = 1) { mockUpdateUseCase(recordId, match { it.record.endedAt == newEnd }) }
        }

    @Test
    fun `hasImageChanges - 내용 변경 없이 새 이미지만 추가해도 변경으로 감지한다`() =
        runTest {
            // Given: 수정 모드
            setupViewModelForEditMode()
            coEvery { mockUpdateUseCase(any(), any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))

            // When: 내용 변경 없이, 새 이미지만 추가하여 제출
            viewModel.addImage(listOf(mockk()))
            viewModel.submitRecord(
                recordId = recordId,
                title = sampleRecord.title,
                type = sampleRecord.personalType,
                location = sampleRecord.location,
                detail = sampleRecord.detail,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: useCase가 호출되어야 함
            coVerify(exactly = 1) { mockUpdateUseCase(any(), match { it.addedImageUris.size == 1 }) }
        }

    @Test
    fun `getCurrentPermittedImageCount - 이미지 개수에 따라 허용 개수를 올바르게 반환한다`() {
        // Given: 생성 모드
        setupViewModelForCreateMode()

        // When & Then: 초기 상태에서는 최대 개수를 반환
        assertThat(viewModel.getCurrentPermittedImageCount()).isEqualTo(MAX_IMAGE)

        // When & Then: 이미지 3개 추가 후에는 (최대-3)개 반환
        viewModel.addImage(listOf(mockk(), mockk(), mockk()))
        assertThat(viewModel.getCurrentPermittedImageCount()).isEqualTo(MAX_IMAGE - 3)
    }

    /** 생성 모드 테스트
     * 이 테스트들은 기록 생성과 관련된 기능을 검증합니다.
     * 본문과 사진의 단일 로컬 저장 결과를 포함합니다.
     */
    @Test
    fun `submitRecord 호출 시 점수 없이 로컬 저장 성공을 반환한다`() =
        runTest(testDispatcher) {
            // Given
            setupViewModelForCreateMode()

            val testStartTime = now.minusHours(1)
            val testEndTime = now
            viewModel.setStartTime(testStartTime)
            viewModel.setEndTime(testEndTime)

            coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))

            // When
            viewModel.submitRecord(recordId, "제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) {
                mockCreateUseCase(match { it.record.title == "제목" && it.addedImageUris.isEmpty() && it.removedImageLocalIds.isEmpty() })
            }
            val actual = viewModel.createResult.replayCache.firstOrNull()
            assertThat(actual).isInstanceOf(SubmissionResult.LocalSaved::class.java)
            assertThat((actual as SubmissionResult.LocalSaved).recordId).isEqualTo(recordId)
            assertThat(viewModel.isLoading.value).isFalse()
        }

    @Test
    fun `예상 점수 조회 실패는 로컬 기록 저장 완료를 막지 않는다`() =
        runTest(testDispatcher) {
            setupViewModelForCreateMode()
            coEvery { mockExpectedScoreInfoUseCase() } returns BaseResult.Error("NETWORK_ERROR", "오프라인")
            viewModel.setStartTime(now.minusHours(1))
            viewModel.setEndTime(now)
            coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))
            viewModel.submitRecord("", "제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat((viewModel.createResult.replayCache.single() as SubmissionResult.LocalSaved).recordId).isEqualTo(recordId)
            assertThat(viewModel.isLoading.value).isFalse()
        }

    @Test
    fun `로컬 저장 실패는 입력 시간과 사진을 유지하고 완료 결과를 만들지 않는다`() =
        runTest(testDispatcher) {
            setupViewModelForCreateMode()
            val start = now.minusHours(1)
            viewModel.setStartTime(start)
            viewModel.setEndTime(now)
            val uri = mockk<Uri>()
            every { uri.toString() } returns imageUriString
            viewModel.addImage(listOf(uri))
            val images = viewModel.imageItems.value
            coEvery { mockCreateUseCase(any()) } returns BaseResult.Error("CACHE_ERROR", "로컬 저장 실패")
            viewModel.submitRecord("", "제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.createResult.replayCache.single()).isInstanceOf(SubmissionResult.Failure::class.java)
            assertThat(viewModel.startTime.value).isEqualTo(start)
            assertThat(viewModel.endTime.value).isEqualTo(now)
            assertThat(viewModel.imageItems.value).isEqualTo(images)
            assertThat(viewModel.isLoading.value).isFalse()
        }

    @Test
    fun `본문과 선택 사진은 단일 로컬 요청으로 저장 완료한다`() =
        runTest(testDispatcher) {
            // Given
            setupViewModelForCreateMode()
            val mockUri = mockk<Uri>()
            coEvery { mockUri.toString() } returns imageUriString

            val testStartTime = now.minusHours(1)
            val testEndTime = now
            viewModel.setStartTime(testStartTime)
            viewModel.setEndTime(testEndTime)

            coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))

            // When
            viewModel.addImage(listOf(mockUri))
            viewModel.submitRecord(recordId, "제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockCreateUseCase(match { it.addedImageUris == listOf(imageUriString) }) }
            val actual = viewModel.createResult.replayCache.firstOrNull()
            assertThat(actual).isInstanceOf(SubmissionResult.LocalSaved::class.java)
            assertThat((actual as SubmissionResult.LocalSaved).recordId).isEqualTo(recordId)
        }

    /** 수정 모드 테스트
     * 이 테스트들은 기존 기록을 수정하는 기능을 검증합니다.
     * 상세 정보 로드, 수정 성공, 이미지 삭제 등의 기능을 포함합니다.
     */
    @Test
    fun `수정 모드에서 loadInitialRecord 호출 시 상세 정보 로드 성공`() =
        runTest(testDispatcher) {
            // Given: 수정 모드로 ViewModel을 설정하고, getDetailUseCase가 성공을 반환하도록 설정
            setupViewModelForEditMode()
            coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(sampleRecord)

            // Then: useCase가 호출되고, LiveData가 올바른 데이터로 업데이트되었는지 검증
            coVerify(exactly = 1) { mockGetDetailUseCase(recordId) }
            assertThat(viewModel.initialDataLoaded.value).isEqualTo(sampleRecord)
            assertThat(viewModel.startTime.value).isEqualTo(sampleRecord.startedAt)
            assertThat(viewModel.endTime.value).isEqualTo(sampleRecord.endedAt)
        }

    @Test
    fun `submitRecord 호출 시 수정 성공 (콘텐츠 변경)`() =
        runTest(testDispatcher) {
            // Given: 수정 모드로 ViewModel을 설정하고, 초기 데이터를 로드
            setupViewModelForEditMode()
            coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(sampleRecord)
            viewModel.loadInitialRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            // And: aggregate 저장이 로컬 ID를 반환합니다.
            coEvery { mockUpdateUseCase(recordId, any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))

            // When: 제목을 변경하여 기록 제출
            val updatedTitle = "Updated Title"
            viewModel.submitRecord(recordId, updatedTitle, sampleRecord.personalType, sampleRecord.location, sampleRecord.detail)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 수정 본문을 한 번 저장한 뒤 로컬 완료를 표시합니다.
            coVerify(exactly = 1) { mockUpdateUseCase(recordId, match { it.record.title == updatedTitle }) }
            val result = viewModel.editResult.replayCache.firstOrNull()
            assertThat(result).isInstanceOf(SubmissionResult.LocalSaved::class.java)
            assertThat((result as SubmissionResult.LocalSaved).recordId).isEqualTo(recordId)
            assertThat(viewModel.isLoading.value).isFalse()
        }

    @Test
    fun `submitRecord 호출 시 수정 모드에서 변경 사항이 없을 경우`() =
        runTest {
            setupViewModelForEditMode()

            // When: 아무것도 안 바꾸고 제출
            viewModel.submitRecord(recordId, sampleRecord.title, sampleRecord.personalType, sampleRecord.location, sampleRecord.detail)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: R.string.exercise_no_change 가 발생해야 함 (기존 테스트 오타 수정)
            assertThat(viewModel.toastMessage.replayCache.firstOrNull()).isEqualTo(R.string.exercise_no_change)
            coVerify(exactly = 0) { mockUpdateUseCase(any(), any()) }
        }

    @Test
    fun `removeImage 호출 시 기존 이미지는 삭제 목록에 추가되고 submit 시 반영`() =
        runTest(testDispatcher) {
            // Given: 수정 모드에서 기존 이미지가 있는 기록을 로드
            val picture = ExerciseRecordPicture("server:99", 99L, "http://exist.com/1.png", ExerciseImageSyncStatus.SYNCED)
            val recordWithPicture = sampleRecord.copy(pictures = listOf(picture))
            setupViewModelForEditMode(recordWithPicture)

            // And: aggregate 저장이 로컬 ID를 반환합니다.
            coEvery { mockUpdateUseCase(any(), any()) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))

            // When: 로드된 기존 이미지를 삭제하고, 제목을 변경하여 제출
            val existingImageItem = viewModel.imageItems.value.find { it is ExerciseImageListItem.ExistingImageItem }
            assertThat(existingImageItem).isNotNull()
            viewModel.removeImage(existingImageItem!!)
            viewModel.submitRecord(recordId, "new title", "type", "loc", "detail")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 사진의 안정적인 로컬 ID를 삭제 의도로 전달합니다.
            coVerify { mockUpdateUseCase(recordId, match { it.removedImageLocalIds == setOf("server:99") }) }
        }

    @Test
    fun `submitRecord 호출 시 수정 실패하면 Failure 결과를 반영`() =
        runTest(testDispatcher) {
            // Given: 수정 모드이고, 초기 데이터 로드 완료
            setupViewModelForEditMode()
            coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(sampleRecord)
            viewModel.loadInitialRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            // And: 로컬 저장이 실패합니다.
            val failureMessage = "수정 중 에러 발생"
            coEvery { mockUpdateUseCase(recordId, any()) } returns BaseResult.Error("CACHE_ERROR", failureMessage)

            // When: 제목을 변경하여 기록 제출
            viewModel.submitRecord(recordId, "달라진 제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: LiveData에 Failure 결과가 반영되었는지 검증
            val result = viewModel.editResult.replayCache.firstOrNull()
            assertThat(result).isInstanceOf(SubmissionResult.Failure::class.java)
            assertThat((result as SubmissionResult.Failure).message).isEqualTo(failureMessage)
            assertThat(viewModel.isLoading.value).isFalse()
        }

    /** 예상 점수 정보 테스트
     * 이 테스트들은 예상 점수 정보를 올바르게 로드하고 표시하는 기능을 검증합니다.
     */
    @Test
    fun `updateScoreGuidance - 시작 시간이 설정되면 점수 획득 가능 상태를 반영한다`() =
        runTest {
            // Given
            setupViewModelForCreateMode()
            val testStartTime = now.minusMinutes(30)

            // When
            viewModel.setStartTime(testStartTime)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val guidanceState = viewModel.scoreGuidanceState.value
            assertThat(guidanceState).isInstanceOf(ScoreGuidanceState.PointsAvailable::class.java)
            assertThat((guidanceState as ScoreGuidanceState.PointsAvailable).points).isEqualTo(3)
        }

    @Test
    fun `updateScoreGuidance - 이미 최대 점수에 도달하면 경고 메시지를 표시한다`() =
        runTest {
            // Given
            setupViewModelForCreateMode()
            coEvery { mockExpectedScoreInfoUseCase.invoke() } returns
                BaseResult.Success(
                    sampleExpectedScoreInfo.copy(currentUserScore = 100),
                )

            // When
            viewModel.setStartTime(now.minusMinutes(10))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val guidanceState = viewModel.scoreGuidanceState.value
            assertThat(guidanceState).isInstanceOf(ScoreGuidanceState.Warning::class.java)
            assertThat((guidanceState as ScoreGuidanceState.Warning).messageId).isEqualTo(R.string.exercise_max_score_reached)
        }

    @Test
    fun `updateScoreGuidance - 이미 점수를 획득한 날짜에 기록을 생성하면 경고 메시지를 표시한다`() =
        runTest {
            // Given
            setupViewModelForCreateMode()
            coEvery { mockExpectedScoreInfoUseCase.invoke() } returns
                BaseResult.Success(
                    sampleExpectedScoreInfo.copy(earnableScoreDays = emptyList()),
                )
            val testStartTime = now.minusHours(1)

            // When
            viewModel.setStartTime(testStartTime)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val guidanceState = viewModel.scoreGuidanceState.value
            assertThat(guidanceState).isInstanceOf(ScoreGuidanceState.Warning::class.java)
            assertThat((guidanceState as ScoreGuidanceState.Warning).messageId).isEqualTo(R.string.exercise_already_scored_today)
        }

    @Test
    fun `updateScoreGuidance - 유효 기간이 지난 날짜에 기록을 생성하면 경고 메시지를 표시한다`() =
        runTest {
            // Given
            setupViewModelForCreateMode()
            coEvery { mockExpectedScoreInfoUseCase.invoke() } returns
                BaseResult.Success(
                    sampleExpectedScoreInfo.copy(
                        validWindow =
                            ValidWindow(
                                startDateTime = LocalDateTime.now().minusDays(30),
                                endDateTime = LocalDateTime.now().minusDays(29),
                            ),
                    ),
                )
            val testStartTime = LocalDateTime.now()

            // When
            viewModel.setStartTime(testStartTime)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val guidanceState = viewModel.scoreGuidanceState.value
            assertThat(guidanceState).isInstanceOf(ScoreGuidanceState.Warning::class.java)
            assertThat((guidanceState as ScoreGuidanceState.Warning).messageId).isEqualTo(R.string.exercise_upload_period_expired)
        }

    @Test
    fun `updateScoreGuidance - 점수 정보 API 호출 실패 시 토스트 메시지를 표시한다`() =
        runTest {
            // Given
            setupViewModelForCreateMode()
            coEvery { mockExpectedScoreInfoUseCase.invoke() } returns BaseResult.Error("API_ERROR", "Failed to fetch")
            val testStartTime = LocalDateTime.now()

            // When
            viewModel.setStartTime(testStartTime)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.toastMessage.replayCache.firstOrNull()).isEqualTo(R.string.exercise_fetch_score_info_fail)
            assertThat(viewModel.scoreGuidanceState.value).isEqualTo(ScoreGuidanceState.Hidden)
        }

    @Test
    fun `같은 폼을 다시 바인딩해도 기록을 갱신하거나 작성 중 시간을 되돌리지 않는다`() =
        runTest(testDispatcher) {
            setupViewModelForEditMode()
            val changed = sampleRecord.startedAt.plusHours(1)
            viewModel.setStartTime(changed)
            val images = viewModel.imageItems.value
            viewModel.loadInitialRecord(recordId)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.startTime.value).isEqualTo(changed)
            assertThat(viewModel.imageItems.value).isEqualTo(images)
            coVerify(exactly = 1) { mockGetDetailUseCase(recordId) }
        }

    @Test
    fun `캐시로 폼을 한 번만 초기화하고 백그라운드 완료 뒤에도 작성 시간과 이미지를 유지한다`() =
        runTest(testDispatcher) {
            setupViewModelForCreateMode()
            val response = CompletableDeferred<BaseResult<ExerciseRecord>>()
            coEvery { mockGetDetailUseCase(recordId, false, false) } returns BaseResult.Success(sampleRecord, isCachedSnapshot = true)
            coEvery { mockGetDetailUseCase(recordId, true, false) } coAnswers { response.await() }
            viewModel.loadInitialRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.initialDataLoaded.value).isEqualTo(sampleRecord)
            val editedStart = sampleRecord.startedAt.minusHours(1)
            viewModel.setStartTime(editedStart)
            val image = mockk<Uri>()
            every { image.toString() } returns imageUriString
            viewModel.addImage(listOf(image))
            val draftImages = viewModel.imageItems.value
            response.complete(BaseResult.Success(sampleRecord.copy(title = "서버 최신", startedAt = now.minusHours(1))))
            testDispatcher.scheduler.runCurrent()
            viewModel.loadInitialRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.startTime.value).isEqualTo(editedStart)
            assertThat(viewModel.imageItems.value).isEqualTo(draftImages)
            assertThat(viewModel.initialDataLoaded.value).isEqualTo(sampleRecord)
            coVerify(exactly = 1) { mockGetDetailUseCase(recordId, false, false) }
            coVerify(exactly = 1) { mockGetDetailUseCase(recordId, true, false) }
        }

    @Test
    fun `이전 기록의 초기 미적중 응답은 새로 선택한 폼을 초기화하지 않는다`() =
        runTest(testDispatcher) {
            setupViewModelForCreateMode()
            val response = CompletableDeferred<BaseResult<ExerciseRecord>>()
            coEvery { mockGetDetailUseCase(recordId, false, false) } coAnswers { response.await() }
            val selected = sampleRecord.copy(title = "새 기록")
            coEvery { mockGetDetailUseCase("other", false, false) } returns BaseResult.Success(selected)
            viewModel.loadInitialRecord(recordId)
            testDispatcher.scheduler.runCurrent()
            viewModel.loadInitialRecord("other")
            testDispatcher.scheduler.runCurrent()
            response.complete(BaseResult.Success(sampleRecord))
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.initialDataLoaded.value).isEqualTo(selected)
            coVerify(exactly = 0) { mockGetDetailUseCase(any(), true, false) }
        }

    @Test
    fun `실제 편집 유스케이스의 로컬 저장 실패 뒤 폼 시간 사진과 삭제 의도를 재제출한다`() =
        runTest(testDispatcher) {
            val repository = mockk<ExerciseRecordRepository>()
            val captured = mutableListOf<ExerciseWriteInput>()
            coEvery { repository.updateExerciseRecord(capture(captured), recordId) } returns
                BaseResult.Error("CACHE_ERROR", "로컬 저장 실패")
            val update = UpdateExerciseRecordUseCase(repository)
            val picture = ExerciseRecordPicture("server:99", 99, "https://example.test/photo", ExerciseImageSyncStatus.SYNCED)
            setupViewModelForEditMode(sampleRecord.copy(pictures = listOf(picture)), update)
            val existing = viewModel.imageItems.value.filterIsInstance<ExerciseImageListItem.ExistingImageItem>().single()
            viewModel.removeImage(existing)
            val image = mockk<Uri>()
            every { image.toString() } returns imageUriString
            viewModel.addImage(listOf(image))
            val images = viewModel.imageItems.value
            val changedStart = sampleRecord.startedAt.minusMinutes(15)
            viewModel.setStartTime(changedStart)
            repeat(2) {
                viewModel.submitRecord(recordId, "작성한 제목", "작성한 타입", "작성한 장소", "작성한 본문")
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(viewModel.editResult.replayCache.single()).isInstanceOf(SubmissionResult.Failure::class.java)
                assertThat(viewModel.startTime.value).isEqualTo(changedStart)
                assertThat(viewModel.imageItems.value).isEqualTo(images)
                assertThat(viewModel.isLoading.value).isFalse()
                viewModel.consumeEditResult()
            }
            assertThat(captured).hasSize(2)
            assertThat(captured[0]).isEqualTo(captured[1])
            assertThat(captured[0].record.title).isEqualTo("작성한 제목")
            assertThat(captured[0].record.detail).isEqualTo("작성한 본문")
            assertThat(captured[0].record.personalType).isEqualTo("작성한 타입")
            assertThat(captured[0].record.location).isEqualTo("작성한 장소")
            assertThat(captured[0].removedImageLocalIds).containsExactly("server:99")
            assertThat(captured[0].addedImageUris).containsExactly(imageUriString)
        }

    @Test
    fun `실제 생성 유스케이스의 저장 실패를 재제출하면 점수 없이 저장 완료한다`() =
        runTest(testDispatcher) {
            val repository = mockk<ExerciseRecordRepository>()
            val submitted = mutableListOf<ExerciseWriteInput>()
            coEvery { repository.createExerciseRecord(capture(submitted)) } returnsMany
                listOf(BaseResult.Error("CACHE_ERROR", "저장 실패"), BaseResult.Success(LocalExerciseSaveResult("local-id")))
            setupViewModelForCreateMode(CreateExerciseRecordUseCase(repository))
            val start = now.minusHours(1)
            viewModel.setStartTime(start)
            viewModel.setEndTime(now)
            viewModel.submitRecord("", "사용자 제목", "타입", "장소", "사용자 본문")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.createResult.replayCache.single()).isInstanceOf(SubmissionResult.Failure::class.java)
            assertThat(viewModel.startTime.value).isEqualTo(start)
            viewModel.consumeCreateResult()
            viewModel.submitRecord("", "사용자 제목", "타입", "장소", "사용자 본문")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(viewModel.createResult.replayCache.single()).isEqualTo(SubmissionResult.LocalSaved("local-id"))
            assertThat(submitted).hasSize(2)
            assertThat(submitted[0]).isEqualTo(submitted[1])
            assertThat(submitted[1].record.detail).isEqualTo("사용자 본문")
            assertThat(viewModel.isLoading.value).isFalse()
        }

    @Test
    fun `로컬 사진을 다시 편집하면 전체 저장 응답 전까지 완료하지 않고 파일 실패 후 같은 입력으로 재시도한다`() =
        runTest(testDispatcher) {
            val original =
                ExerciseRecordPicture(
                    "owned-photo",
                    null,
                    "file:///private/exercise-images/photo.png",
                    ExerciseImageSyncStatus.PENDING,
                )
            setupViewModelForEditMode(sampleRecord.copy(pictures = listOf(original), syncPending = true))
            val existing = viewModel.imageItems.value.filterIsInstance<ExerciseImageListItem.ExistingImageItem>().single()
            assertThat(existing.key).isEqualTo(original.localId)
            assertThat(existing.source).isEqualTo(original.source)
            viewModel.removeImage(existing)
            val selected = mockk<Uri>()
            every { selected.toString() } returns imageUriString
            viewModel.addImage(listOf(selected))
            val draft = viewModel.imageItems.value
            val requests = mutableListOf<ExerciseWriteInput>()
            val pending = CompletableDeferred<BaseResult<LocalExerciseSaveResult>>()
            coEvery { mockUpdateUseCase(recordId, capture(requests)) } coAnswers { pending.await() }
            viewModel.submitRecord(recordId, "보존 제목", "보존 타입", "보존 장소", "보존 본문")
            testDispatcher.scheduler.runCurrent()
            assertThat(viewModel.isLoading.value).isTrue()
            assertThat(viewModel.editResult.replayCache).isEmpty()
            val cause = java.io.IOException("파일 저장 실패")
            pending.complete(BaseResult.Error("CACHE_ERROR", "사진 저장 실패", cause))
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat((viewModel.editResult.replayCache.single() as SubmissionResult.Failure).cause).isSameInstanceAs(cause)
            assertThat(viewModel.imageItems.value).isEqualTo(draft)
            assertThat(viewModel.startTime.value).isEqualTo(sampleRecord.startedAt)
            assertThat(viewModel.endTime.value).isEqualTo(sampleRecord.endedAt)
            assertThat(viewModel.isLoading.value).isFalse()
            viewModel.consumeEditResult()
            coEvery { mockUpdateUseCase(recordId, capture(requests)) } returns BaseResult.Success(LocalExerciseSaveResult(recordId))
            viewModel.submitRecord(recordId, "보존 제목", "보존 타입", "보존 장소", "보존 본문")
            testDispatcher.scheduler.advanceUntilIdle()
            assertThat(requests).hasSize(2)
            assertThat(requests[1]).isEqualTo(requests[0])
            assertThat(requests[0].removedImageLocalIds).containsExactly("owned-photo")
            assertThat(requests[0].addedImageUris).containsExactly(imageUriString)
            assertThat(viewModel.editResult.replayCache.single()).isEqualTo(SubmissionResult.LocalSaved(recordId))
        }
}
