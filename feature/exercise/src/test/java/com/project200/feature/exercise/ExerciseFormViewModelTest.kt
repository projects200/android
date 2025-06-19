package com.project200.feature.exercise

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseRecordPicture
import com.project200.domain.model.SubmissionResult
import com.project200.domain.usecase.CreateExerciseRecordUseCase
import com.project200.domain.usecase.EditExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.domain.usecase.UploadExerciseRecordImagesUseCase
import com.project200.feature.exercise.form.ExerciseFormViewModel
import com.project200.feature.exercise.form.ExerciseImageListItem
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

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ExerciseFormViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val now: LocalDateTime = LocalDateTime.now()
    private val sampleRecord = ExerciseRecord(
        title = "테스트 운동", detail = "테스트 상세", personalType = "테스트",
        startedAt = now.minusHours(1), endedAt = now, location = "테스트 장소", pictures = null
    )
    private val recordId = 123L
    private val imageUriString = "content://image/1"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setupViewModelForCreateMode() {
        savedStateHandle = SavedStateHandle().apply { set("recordId", -1L) }
        viewModel = ExerciseFormViewModel(
            savedStateHandle,
            mockGetDetailUseCase,
            mockCreateUseCase,
            mockUploadUseCase,
            mockEditUseCase
        )
        viewModel.setStartTime(now.minusHours(1))
        viewModel.setEndTime(now)
    }

    private fun setupViewModelForEditMode() {
        savedStateHandle = SavedStateHandle().apply { set("recordId", recordId) }
        viewModel = ExerciseFormViewModel(
            savedStateHandle, mockGetDetailUseCase, mockCreateUseCase, mockUploadUseCase, mockEditUseCase
        )
    }

    /** 생성 모드 테스트
     * 이 테스트들은 기록 생성과 관련된 기능을 검증합니다.
     * 이미지 업로드와 관련된 부분도 포함되어 있습니다.
     */
    @Test
    fun `submitRecord 호출 시 기록 생성 성공 (이미지 없음)`() = runTest(testDispatcher) {
        // Given
        setupViewModelForCreateMode()
        coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(recordId)

        // When
        viewModel.submitRecord("제목", "타입", "장소", "상세")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockCreateUseCase(any()) }
        coVerify(exactly = 0) { mockUploadUseCase(any(), any()) }
        val actual = viewModel.createResult.value
        assertThat(actual).isInstanceOf(SubmissionResult.Success::class.java)
        assertThat((actual as SubmissionResult.Success).recordId).isEqualTo(recordId)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `submitRecord 호출 시 기록 생성 성공 및 이미지 업로드 성공`() = runTest(testDispatcher) {
        // Given
        setupViewModelForCreateMode()
        val mockUri = mockk<Uri>()
        coEvery { mockUri.toString() } returns imageUriString
        coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(recordId)
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
    }

    @Test
    fun `submitRecord 호출 시 기록 생성 성공 but 이미지 업로드 실패`() = runTest(testDispatcher) {
        // Given
        setupViewModelForCreateMode()
        val mockUri = mockk<Uri>()
        coEvery { mockUri.toString() } returns imageUriString
        coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(recordId)
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
    fun `수정 모드에서 loadInitialRecord 호출 시 상세 정보 로드 성공`() = runTest(testDispatcher) {
        // Given: 수정 모드로 ViewModel을 설정하고, getDetailUseCase가 성공을 반환하도록 설정
        setupViewModelForEditMode()
        coEvery { mockGetDetailUseCase(recordId) } returns BaseResult.Success(sampleRecord)

        // When: 초기 데이터 로드 함수를 호출
        viewModel.loadInitialRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: useCase가 호출되고, LiveData가 올바른 데이터로 업데이트되었는지 검증
        coVerify(exactly = 1) { mockGetDetailUseCase(recordId) }
        assertThat(viewModel.initialDataLoaded.value).isEqualTo(sampleRecord)
        assertThat(viewModel.startTime.value).isEqualTo(sampleRecord.startedAt)
        assertThat(viewModel.endTime.value).isEqualTo(sampleRecord.endedAt)
    }

    @Test
    fun `submitRecord 호출 시 수정 성공 (콘텐츠 변경)`() = runTest(testDispatcher) {
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
    fun `submitRecord 호출 시 수정 모드에서 변경 사항이 없을 경우`() = runTest(testDispatcher) {
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
    fun `removeImage 호출 시 기존 이미지는 삭제 목록에 추가되고 submit 시 반영`() = runTest(testDispatcher) {
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
    fun `submitRecord 호출 시 수정 실패하면 Failure 결과를 반영`() = runTest(testDispatcher) {
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
}