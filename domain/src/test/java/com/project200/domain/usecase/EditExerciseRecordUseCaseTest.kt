package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class EditExerciseRecordUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    // 하위 UseCase들을 Mock으로 생성
    @RelaxedMockK
    private lateinit var mockUpdateExerciseRecordUseCase: UpdateExerciseRecordUseCase
    @RelaxedMockK
    private lateinit var mockDeleteExerciseRecordImagesUseCase: DeleteExerciseRecordImagesUseCase
    @RelaxedMockK
    private lateinit var mockUploadExerciseRecordImagesUseCase: UploadExerciseRecordImagesUseCase

    private lateinit var useCase: EditExerciseRecordUseCase

    // 테스트용 데이터
    private val recordId = 1L
    private val recordToUpdate = ExerciseRecord(
        title = "title", detail = "detail", personalType = "type",
        startedAt = LocalDateTime.now(), endedAt = LocalDateTime.now(), location = "loc", pictures = null
    )
    private val imagesToDelete = listOf(10L, 20L)
    private val newImages = listOf("path/to/image1.jpg", "path/to/image2.jpg")

    @Before
    fun setUp() {
        useCase = EditExerciseRecordUseCase(
            updateExerciseRecordUseCase = mockUpdateExerciseRecordUseCase,
            deleteExerciseRecordImagesUseCase = mockDeleteExerciseRecordImagesUseCase,
            uploadExerciseRecordImagesUseCase = mockUploadExerciseRecordImagesUseCase
        )
    }

    @Test
    fun `변경사항 없을 시 Success 반환 및 하위 usecase 미호출`() = runTest {
        // When
        val result = useCase(
            recordId = recordId,
            recordToUpdate = recordToUpdate,
            isContentChanges = false,
            imagesToDelete = emptyList(),
            newImages = emptyList()
        )

        // Then
        assertThat(result).isInstanceOf(ExerciseEditResult.Success::class.java)
        coVerify(exactly = 0) { mockUpdateExerciseRecordUseCase(any(), any()) }
        coVerify(exactly = 0) { mockDeleteExerciseRecordImagesUseCase(any(), any()) }
        coVerify(exactly = 0) { mockUploadExerciseRecordImagesUseCase(any(), any()) }
    }

    @Test
    fun `내용 및 이미지 수정 모두 성공`() = runTest {
        // Given: 모든 하위 UseCase가 성공을 반환하도록 설정
        coEvery { mockUpdateExerciseRecordUseCase(recordId, recordToUpdate) } returns BaseResult.Success(recordId)
        coEvery { mockDeleteExerciseRecordImagesUseCase(recordId, imagesToDelete) } returns BaseResult.Success(Unit)
        coEvery { mockUploadExerciseRecordImagesUseCase(recordId, newImages) } returns BaseResult.Success(recordId)

        // When
        val result = useCase(
            recordId = recordId,
            recordToUpdate = recordToUpdate,
            isContentChanges = true,
            imagesToDelete = imagesToDelete,
            newImages = newImages
        )

        // Then
        assertThat(result).isInstanceOf(ExerciseEditResult.Success::class.java)
        coVerify(exactly = 1) { mockUpdateExerciseRecordUseCase(recordId, recordToUpdate) }
        coVerify(exactly = 1) { mockDeleteExerciseRecordImagesUseCase(recordId, imagesToDelete) }
        coVerify(exactly = 1) { mockUploadExerciseRecordImagesUseCase(recordId, newImages) }
    }

    @Test
    fun `내용 수정 실패 시 ContentFailure 반환`() = runTest {
        // Given: 내용 수정 UseCase만 실패하도록 설정
        val error = BaseResult.Error("CONTENT_FAIL", "내용 수정 실패")
        coEvery { mockUpdateExerciseRecordUseCase(recordId, recordToUpdate) } returns error
        coEvery { mockDeleteExerciseRecordImagesUseCase(any(), any()) } returns BaseResult.Success(Unit)

        // When
        val result = useCase(
            recordId = recordId,
            recordToUpdate = recordToUpdate,
            isContentChanges = true,
            imagesToDelete = imagesToDelete,
            newImages = newImages
        )

        // Then
        assertThat(result).isInstanceOf(ExerciseEditResult.ContentFailure::class.java)
        assertThat((result as ExerciseEditResult.ContentFailure).message).isEqualTo(error.message)
    }

    @Test
    fun `이미지 삭제 실패 시 ImageFailure 반환`() = runTest {
        // Given: 이미지 삭제 UseCase가 실패하도록 설정
        val error = BaseResult.Error("DELETE_FAIL", "이미지 삭제 실패")
        coEvery { mockUpdateExerciseRecordUseCase(any(), any()) } returns BaseResult.Success(recordId)
        coEvery { mockDeleteExerciseRecordImagesUseCase(recordId, imagesToDelete) } returns error

        // When
        val result = useCase(
            recordId = recordId,
            recordToUpdate = recordToUpdate,
            isContentChanges = true,
            imagesToDelete = imagesToDelete,
            newImages = newImages
        )

        // Then
        assertThat(result).isInstanceOf(ExerciseEditResult.ImageFailure::class.java)
        assertThat((result as ExerciseEditResult.ImageFailure).message).isEqualTo(error.message)
        // 이미지 삭제 실패 시 업로드는 호출되지 않음
        coVerify(exactly = 0) { mockUploadExerciseRecordImagesUseCase(any(), any()) }
    }

    @Test
    fun `이미지 업로드 실패 시 ImageFailure 반환`() = runTest {
        // Given: 이미지 업로드 UseCase가 실패하도록 설정
        val error = BaseResult.Error("UPLOAD_FAIL", "이미지 업로드 실패")
        coEvery { mockUpdateExerciseRecordUseCase(any(), any()) } returns BaseResult.Success(recordId)
        coEvery { mockDeleteExerciseRecordImagesUseCase(any(), any()) } returns BaseResult.Success(Unit)
        coEvery { mockUploadExerciseRecordImagesUseCase(recordId, newImages) } returns error

        // When
        val result = useCase(
            recordId = recordId,
            recordToUpdate = recordToUpdate,
            isContentChanges = true,
            imagesToDelete = imagesToDelete,
            newImages = newImages
        )

        // Then
        assertThat(result).isInstanceOf(ExerciseEditResult.ImageFailure::class.java)
        assertThat((result as ExerciseEditResult.ImageFailure).message).isEqualTo(error.message)
    }
}