package com.project200.feature.exercise

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordCreationResult
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.model.ExpectedScoreInfo
import com.project200.domain.model.SubmissionResult
import com.project200.domain.model.ValidWindow
import com.project200.domain.usecase.CreateExerciseRecordUseCase
import com.project200.domain.usecase.EditExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.domain.usecase.GetExpectedScoreInfoUseCase
import com.project200.domain.usecase.UploadExerciseRecordImagesUseCase
import com.project200.feature.exercise.form.ExerciseFormViewModel
import com.project200.feature.exercise.form.ExerciseImageListItem
import com.project200.feature.exercise.form.ScoreGuidanceState
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
    private lateinit var mockUploadUseCase: UploadExerciseRecordImagesUseCase

    @MockK
    private lateinit var mockEditUseCase: EditExerciseRecordUseCase

    @MockK
    private lateinit var mockExpectedScoreInfoUseCase: GetExpectedScoreInfoUseCase

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ExerciseFormViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val now: LocalDateTime = LocalDateTime.now()
    private val sampleRecord =
        ExerciseRecord(
            title = "테스트 운동",
            detail = "테스트 상세",
            personalType = "테스트",
            startedAt = now.minusHours(1),
            endedAt = now,
            location = "테스트 장소",
            pictures = null,
        )
    private val recordId = 123L
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
        coEvery { mockExpectedScoreInfoUseCase.invoke() } returns BaseResult.Success(sampleExpectedScoreInfo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setupViewModelForCreateMode() {
        savedStateHandle = SavedStateHandle().apply { set("recordId", -1L) }
        viewModel =
            ExerciseFormViewModel(
                savedStateHandle = savedStateHandle,
                getExerciseRecordDetailUseCase = mockGetDetailUseCase,
                createExerciseRecordUseCase = mockCreateUseCase,
                uploadExerciseRecordImagesUseCase = mockUploadUseCase,
                editExerciseRecordUseCase = mockEditUseCase,
                getExpectedScoreInfoUseCase = mockExpectedScoreInfoUseCase,
            )
    }

    private fun setupViewModelForEditMode() {
        savedStateHandle = SavedStateHandle().apply { set("recordId", recordId) }
        viewModel =
            ExerciseFormViewModel(
                savedStateHandle,
                mockGetDetailUseCase,
                mockCreateUseCase,
                mockUploadUseCase,
                mockEditUseCase,
                mockExpectedScoreInfoUseCase,
            )
        // 수정 모드 테스트를 위해 초기 데이터를 미리 로드합니다.
        coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(sampleRecord)
        viewModel.loadInitialRecord()
        runTest { testDispatcher.scheduler.advanceUntilIdle() }
    }

    /** 폼 상태 관리 및 유효성 검증 테스트
     * 이 테스트들은 ViewModel의 상태 관리 및 유효성 검증 로직을 검증합니다.
     */

    @Test
    fun `loadInitialRecord - 생성 모드일 때 ViewModel이 올바르게 초기화된다`() {
        // Given: recordId가 -1L인 생성 모드
        setupViewModelForCreateMode()

        // When: 초기 데이터 로드 실행
        viewModel.loadInitialRecord()

        // Then: 수정과 관련된 데이터는 null 또는 초기 상태여야 함
        assertThat(viewModel.initialDataLoaded.value).isNull()
        assertThat(viewModel.startTime.value).isNull()
        assertThat(viewModel.imageItems.value).hasSize(1) // AddButton만 존재
    }

    @Test
    fun `setEndTime - 시작 시간보다 이전의 종료 시간을 설정하면 false를 반환하고 값이 변경되지 않는다`() {
        // Given: 시작 시간이 설정된 상태
        setupViewModelForCreateMode()
        val initialStartTime = now
        viewModel.setStartTime(initialStartTime)

        // Then: 현재 종료 시간이 null임을 확인
        assertThat(viewModel.endTime.value).isNull()

        // When: 시작 시간보다 이른 시간으로 종료 시간을 설정 시도
        val invalidEndTime = initialStartTime.minusHours(1) // invalidEndTime은 09:00
        val result = viewModel.setEndTime(invalidEndTime)

        // Then: false를 반환하고, 종료 시간은 여전히 null
        assertThat(result).isFalse()
        assertThat(viewModel.endTime.value).isNull()
    }

    @Test
    fun `setStartTime - 기존 종료 시간보다 늦은 시작 시간을 설정하면 종료 시간이 초기화된다`() {
        // Given: 시작과 종료 시간이 모두 설정된 상태
        setupViewModelForCreateMode()
        viewModel.setStartTime(now.minusHours(1))
        viewModel.setEndTime(now.minusMinutes(30))
        assertThat(viewModel.endTime.value).isNotNull()

        // When: 기존 종료 시간보다 늦은 시간으로 시작 시간을 재설정
        viewModel.setStartTime(now)

        // Then: 종료 시간이 null로 초기화되어야 함
        assertThat(viewModel.endTime.value).isNull()
    }

    @Test
    fun `removeImage - 새로 추가했던 이미지를 삭제하면 removedPictureIds에 추가되지 않는다`() {
        // Given: 수정 모드에서 새로운 이미지를 추가한 상태
        setupViewModelForEditMode()
        val newImageUri = mockk<Uri>()
        val newImageItem = ExerciseImageListItem.NewImageItem(newImageUri)
        viewModel.addImage(listOf(newImageUri))
        assertThat(viewModel.imageItems.value).contains(newImageItem)

        // When: 새로 추가했던 이미지를 다시 삭제
        viewModel.removeImage(newImageItem)

        // Then: removedPictureIds는 비어있어야 하고, submit 시에도 빈 리스트로 전달되어야 함
        coEvery { mockEditUseCase(any(), any(), any(), any(), any()) } returns ExerciseEditResult.Success(recordId)
        viewModel.submitRecord("제목 변경", "타입", "장소", "상세") // hasChanges=true 만들기
        runTest { testDispatcher.scheduler.advanceUntilIdle() }

        coVerify {
            mockEditUseCase(
                recordId = recordId,
                recordToUpdate = any(),
                isContentChanges = true,
                imagesToDelete = emptyList(),
                newImages = emptyList(),
            )
        }
    }

    @Test
    fun `hasContentChanges - 다른 내용은 그대로고 시간만 변경되어도 변경으로 감지한다`() =
        runTest {
            // Given: 수정 모드
            setupViewModelForEditMode()
            coEvery { mockEditUseCase(any(), any(), any(), any(), any()) } returns ExerciseEditResult.Success(recordId)

            // When: 다른 내용은 그대로 두고, 종료 시간만 변경하여 제출
            viewModel.setEndTime(sampleRecord.endedAt.plusHours(1))
            viewModel.submitRecord(
                title = sampleRecord.title,
                type = sampleRecord.personalType,
                location = sampleRecord.location,
                detail = sampleRecord.detail,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: useCase가 호출되어야 함 (변경 사항이 없다는 토스트 메시지가 뜨지 않아야 함)
            assertThat(viewModel.toastMessage.value).isNotEqualTo(ExerciseFormViewModel.NO_CHANGE)
            coVerify(exactly = 1) { mockEditUseCase(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `hasImageChanges - 내용 변경 없이 새 이미지만 추가해도 변경으로 감지한다`() =
        runTest {
            // Given: 수정 모드
            setupViewModelForEditMode()
            coEvery { mockEditUseCase(any(), any(), any(), any(), any()) } returns ExerciseEditResult.Success(recordId)

            // When: 내용 변경 없이, 새 이미지만 추가하여 제출
            viewModel.addImage(listOf(mockk()))
            viewModel.submitRecord(
                title = sampleRecord.title,
                type = sampleRecord.personalType,
                location = sampleRecord.location,
                detail = sampleRecord.detail,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: useCase가 호출되어야 함
            assertThat(viewModel.toastMessage.value).isNotEqualTo(ExerciseFormViewModel.NO_CHANGE)
            coVerify(exactly = 1) { mockEditUseCase(any(), any(), any(), any(), any()) }
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
     * 이미지 업로드와 관련된 부분도 포함되어 있습니다.
     */
    @Test
    fun `submitRecord 호출 시 기록 생성 성공 (이미지 없음)`() =
        runTest(testDispatcher) {
            // Given
            setupViewModelForCreateMode()

            val testStartTime = now.minusHours(1)
            val testEndTime = now
            val earnedPoints = 3
            viewModel.setStartTime(testStartTime)
            viewModel.setEndTime(testEndTime)

            coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(ExerciseRecordCreationResult(recordId, earnedPoints)) // 3점 획득

            // When
            viewModel.submitRecord("제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockCreateUseCase(any()) }
            coVerify(exactly = 0) { mockUploadUseCase(any(), any()) }
            val actual = viewModel.createResult.value
            assertThat(actual).isInstanceOf(SubmissionResult.Success::class.java)
            assertThat((actual as SubmissionResult.Success).recordId).isEqualTo(recordId)
            assertThat(actual.earnedPoints).isEqualTo(earnedPoints)
            assertThat(viewModel.isLoading.value).isFalse()
        }

    @Test
    fun `submitRecord 호출 시 기록 생성 성공 및 이미지 업로드 성공`() =
        runTest(testDispatcher) {
            // Given
            setupViewModelForCreateMode()
            val mockUri = mockk<Uri>()
            coEvery { mockUri.toString() } returns imageUriString

            val testStartTime = now.minusHours(1)
            val testEndTime = now
            val earnedPoints = 3
            viewModel.setStartTime(testStartTime)
            viewModel.setEndTime(testEndTime)

            coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(ExerciseRecordCreationResult(recordId, earnedPoints))
            coEvery { mockUploadUseCase(recordId, listOf(imageUriString)) } returns BaseResult.Success(recordId)

            // When
            viewModel.addImage(listOf(mockUri))
            viewModel.submitRecord("제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockCreateUseCase(any()) }
            coVerify(exactly = 1) { mockUploadUseCase(recordId, listOf(imageUriString)) }
            val actual = viewModel.createResult.value
            assertThat(actual).isInstanceOf(SubmissionResult.Success::class.java)
            assertThat((actual as SubmissionResult.Success).earnedPoints).isEqualTo(earnedPoints)
        }

    @Test
    fun `submitRecord 호출 시 기록 생성 성공 but 이미지 업로드 실패`() =
        runTest(testDispatcher) {
            // Given
            setupViewModelForCreateMode()
            val mockUri = mockk<Uri>()
            coEvery { mockUri.toString() } returns imageUriString

            val testStartTime = now.minusHours(1)
            val testEndTime = now
            viewModel.setStartTime(testStartTime)
            viewModel.setEndTime(testEndTime)

            coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(ExerciseRecordCreationResult(recordId, 3))
            coEvery { mockUploadUseCase(recordId, listOf(imageUriString)) } returns BaseResult.Error("UPLOAD_ERROR", "Upload failed")

            viewModel.addImage(listOf(mockUri))

            // When
            viewModel.submitRecord("제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val actual = viewModel.createResult.value
            assertThat(actual).isInstanceOf(SubmissionResult.PartialSuccess::class.java)
            assertThat((actual as SubmissionResult.PartialSuccess).recordId).isEqualTo(recordId)
            assertThat(actual.message).isEqualTo(ExerciseFormViewModel.UPLOAD_FAIL)
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
            viewModel.loadInitialRecord()
            testDispatcher.scheduler.advanceUntilIdle()

            // And: editUseCase가 recordId와 함께 Success를 반환하도록 설정
            coEvery { mockEditUseCase(recordId, any(), true, emptyList(), emptyList()) } returns ExerciseEditResult.Success(recordId)

            // When: 제목을 변경하여 기록 제출
            val updatedTitle = "Updated Title"
            viewModel.submitRecord(updatedTitle, sampleRecord.personalType, sampleRecord.location, sampleRecord.detail)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: editUseCase가 콘텐츠 변경 사항과 함께 올바르게 호출되었는지 검증
            coVerify(exactly = 1) { mockEditUseCase(recordId, any(), true, emptyList(), emptyList()) }
            val result = viewModel.editResult.value
            assertThat(result).isInstanceOf(ExerciseEditResult.Success::class.java)
            assertThat((result as ExerciseEditResult.Success).recordId).isEqualTo(recordId)
            assertThat(viewModel.isLoading.value).isFalse()
        }

    @Test
    fun `submitRecord 호출 시 수정 모드에서 변경 사항이 없을 경우`() =
        runTest(testDispatcher) {
            // Given: 수정 모드로 ViewModel을 설정하고, 초기 데이터를 로드
            setupViewModelForEditMode()
            coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(sampleRecord)
            viewModel.loadInitialRecord()
            testDispatcher.scheduler.advanceUntilIdle()

            // When: 변경 없이 동일한 내용으로 기록 제출
            viewModel.submitRecord(sampleRecord.title, sampleRecord.personalType, sampleRecord.location, sampleRecord.detail)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: 변경 사항이 없으므로 useCase가 호출되지 않고, 토스트 메시지가 표시되는지 검증
            coVerify(exactly = 0) { mockEditUseCase(any(), any(), any(), any(), any()) }
            assertThat(viewModel.toastMessage.value).isEqualTo(ExerciseFormViewModel.NO_CHANGE)
        }

    @Test
    fun `removeImage 호출 시 기존 이미지는 삭제 목록에 추가되고 submit 시 반영`() =
        runTest(testDispatcher) {
            // Given: 수정 모드에서 기존 이미지가 있는 기록을 로드
            val picture = ExerciseRecordPicture(id = 99L, url = "http://exist.com/1.png")
            val recordWithPicture = sampleRecord.copy(pictures = listOf(picture))
            setupViewModelForEditMode()
            coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(recordWithPicture)
            viewModel.loadInitialRecord()
            testDispatcher.scheduler.advanceUntilIdle()

            // And: editUseCase가 성공을 반환하도록 설정
            coEvery { mockEditUseCase(any(), any(), any(), any(), any()) } returns ExerciseEditResult.Success(recordId)

            // When: 로드된 기존 이미지를 삭제하고, 제목을 변경하여 제출
            val existingImageItem = viewModel.imageItems.value?.find { it is ExerciseImageListItem.ExistingImageItem }
            assertThat(existingImageItem).isNotNull()
            viewModel.removeImage(existingImageItem!!)
            viewModel.submitRecord("new title", "type", "loc", "detail")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: editUseCase가 호출될 때, 삭제된 이미지 ID 목록이 올바르게 전달되었는지 검증
            coVerify { mockEditUseCase(recordId, any(), true, listOf(99L), emptyList()) }
        }

    @Test
    fun `submitRecord 호출 시 수정 실패하면 Failure 결과를 반영`() =
        runTest(testDispatcher) {
            // Given: 수정 모드이고, 초기 데이터 로드 완료
            setupViewModelForEditMode()
            coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(sampleRecord)
            viewModel.loadInitialRecord()
            testDispatcher.scheduler.advanceUntilIdle()

            // And: editUseCase가 Failure를 반환하도록 설정
            val failureMessage = "수정 중 에러 발생"
            coEvery { mockEditUseCase(recordId, any(), true, emptyList(), emptyList()) } returns ExerciseEditResult.Failure(failureMessage)

            // When: 제목을 변경하여 기록 제출
            viewModel.submitRecord("달라진 제목", "타입", "장소", "상세")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: LiveData에 Failure 결과가 반영되었는지 검증
            val result = viewModel.editResult.value
            assertThat(result).isInstanceOf(ExerciseEditResult.Failure::class.java)
            assertThat((result as ExerciseEditResult.Failure).message).isEqualTo(failureMessage)
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
            val testStartTime = LocalDateTime.now()

            // When
            viewModel.setStartTime(testStartTime)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val guidanceState = viewModel.scoreGuidanceState.value
            assertThat(guidanceState).isInstanceOf(ScoreGuidanceState.Warning::class.java)
            assertThat((guidanceState as ScoreGuidanceState.Warning).message).isEqualTo(ExerciseFormViewModel.MAX_SCORE_REACHED)
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
            assertThat((guidanceState as ScoreGuidanceState.Warning).message).isEqualTo(ExerciseFormViewModel.ALREADY_SCORED_TODAY)
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
            assertThat((guidanceState as ScoreGuidanceState.Warning).message).isEqualTo(ExerciseFormViewModel.UPLOAD_PERIOD_EXPIRED)
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
            assertThat(viewModel.toastMessage.value).isEqualTo(ExerciseFormViewModel.FETCH_SCORE_INFO_FAIL)
            assertThat(viewModel.scoreGuidanceState.value).isEqualTo(ScoreGuidanceState.Hidden)
        }
}
