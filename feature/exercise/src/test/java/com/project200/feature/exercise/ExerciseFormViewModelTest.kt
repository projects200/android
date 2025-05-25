package com.project200.feature.exercise

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import com.project200.domain.usecase.CreateExerciseRecordUseCase
import com.project200.domain.usecase.GetExerciseRecordDetailUseCase
import com.project200.domain.usecase.UploadExerciseRecordImagesUseCase
import com.project200.feature.exercise.form.ExerciseFormViewModel
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
        // 생성 모드로 테스트하기 위해 recordId를 -1L로 설정
        savedStateHandle = SavedStateHandle().apply { set("recordId", -1L) }
        viewModel = ExerciseFormViewModel(
            savedStateHandle,
            mockGetDetailUseCase,
            mockCreateUseCase,
            mockUploadUseCase
        )
        // 테스트 시작 시 시간 설정 (유효성 검사 통과 목적)
        viewModel.setStartTime(now.minusHours(1))
        viewModel.setEndTime(now)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitRecord 호출 시 기록 생성 성공 (이미지 없음)`() = runTest(testDispatcher) {
        // Given
        coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(recordId)

        // When
        viewModel.submitRecord("제목", "타입", "장소", "상세")
        testDispatcher.scheduler.advanceUntilIdle() // Coroutine 작업 완료 대기

        // Then
        coVerify(exactly = 1) { mockCreateUseCase(any()) }
        coVerify(exactly = 0) { mockUploadUseCase(any(), any()) } // 이미지 업로드 호출 안됨

        val actual = viewModel.createResult.value
        assertThat(actual).isInstanceOf(SubmissionResult.Success::class.java)
        assertThat((actual as SubmissionResult.Success).recordId).isEqualTo(recordId)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `submitRecord 호출 시 기록 생성 성공 및 이미지 업로드 성공`() = runTest(testDispatcher) {
        // Given
        val mockUri = mockk<Uri>() // Uri 모킹
        coEvery { mockUri.toString() } returns imageUriString // toString() 동작 정의
        coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(recordId)
        coEvery { mockUploadUseCase(recordId, listOf(imageUriString)) } returns BaseResult.Success(recordId)

        viewModel.addImage(listOf(mockUri)) // 이미지 추가

        // When
        viewModel.submitRecord("제목", "타입", "장소", "상세")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockCreateUseCase(any()) }
        coVerify(exactly = 1) { mockUploadUseCase(recordId, listOf(imageUriString)) }

        val actual = viewModel.createResult.value
        assertThat(actual).isInstanceOf(SubmissionResult.Success::class.java)
        assertThat((actual as SubmissionResult.Success).recordId).isEqualTo(recordId)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `submitRecord 호출 시 기록 생성 성공 but 이미지 업로드 실패`() = runTest(testDispatcher) {
        // Given
        val mockUri = mockk<Uri>()
        coEvery { mockUri.toString() } returns imageUriString
        coEvery { mockCreateUseCase(any()) } returns BaseResult.Success(recordId)
        coEvery { mockUploadUseCase(recordId, listOf(imageUriString)) } returns BaseResult.Error(
            "UPLOAD_ERROR", "Upload failed"
        )

        viewModel.addImage(listOf(mockUri))

        // When
        viewModel.submitRecord("제목", "타입", "장소", "상세")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockCreateUseCase(any()) }
        coVerify(exactly = 1) { mockUploadUseCase(recordId, listOf(imageUriString)) }

        val actual = viewModel.createResult.value
        assertThat(actual).isInstanceOf(SubmissionResult.PartialSuccess::class.java)
        assertThat((actual as SubmissionResult.PartialSuccess).recordId).isEqualTo(recordId)
        assertThat(actual.message).isEqualTo(ExerciseFormViewModel.UPLOAD_FAIL)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `submitRecord 호출 시 기록 생성 실패`() = runTest(testDispatcher) {
        // Given
        coEvery { mockCreateUseCase(any()) } returns BaseResult.Error("CREATE_ERROR", "Create failed")

        // When
        viewModel.submitRecord("제목", "타입", "장소", "상세")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockCreateUseCase(any()) }
        coVerify(exactly = 0) { mockUploadUseCase(any(), any()) }

        val actual = viewModel.createResult.value
        assertThat(actual).isInstanceOf(SubmissionResult.Failure::class.java)
        assertThat((actual as SubmissionResult.Failure).message).isEqualTo(ExerciseFormViewModel.CREATE_FAIL)
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `submitRecord 호출 시 유효성 검사 실패`() = runTest(testDispatcher) {
        // Given: 제목을 비워둠
        // When
        viewModel.submitRecord("", "타입", "장소", "상세")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 0) { mockCreateUseCase(any()) } // Usecase 호출 안됨
        coVerify(exactly = 0) { mockUploadUseCase(any(), any()) }

        val actualToast = viewModel.toastMessage.value
        assertThat(actualToast).isEqualTo(ExerciseFormViewModel.INVALID_INPUT)
        assertThat(viewModel.isLoading.value).isNull() // 로딩 시작 안 함
    }
}