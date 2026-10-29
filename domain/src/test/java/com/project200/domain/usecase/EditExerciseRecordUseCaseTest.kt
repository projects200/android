package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.ExerciseWriteInput
import com.project200.domain.model.LocalExerciseSaveResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime

class EditExerciseRecordUseCaseTest {
    private val update = mockk<UpdateExerciseRecordUseCase>()
    private val delete = mockk<DeleteExerciseRecordImagesUseCase>()
    private val upload = mockk<UploadExerciseRecordImagesUseCase>()
    private val useCase = EditExerciseRecordUseCase(update, delete, upload)
    private val id = "record-1"
    private val time = LocalDateTime.parse("2026-09-10T10:00:00")
    private val record = ExerciseRecord("제목", "본문", "WEIGHT", time, time.plusHours(1), "장소", null)
    private val input = ExerciseWriteInput(record, listOf("photo"), setOf(10L, 20L))

    @Test
    fun `변경이 없으면 저장이나 사진 요청 없이 기존 화면을 유지한다`() = runTest {
        assertThat(useCase(id, ExerciseWriteInput(record), false)).isEqualTo(ExerciseEditResult.Success(id))
        coVerify(exactly = 0) { update(any(), any()) }
        coVerify(exactly = 0) { delete(any(), any()) }
        coVerify(exactly = 0) { upload(any(), any()) }
    }

    @Test
    fun `본문만 로컬 저장할 때 점수나 사진 응답 없이 편집 완료한다`() = runTest {
        coEvery { update(id, ExerciseWriteInput(record)) } returns BaseResult.Success(LocalExerciseSaveResult(id))
        assertThat(useCase(id, ExerciseWriteInput(record), true)).isEqualTo(ExerciseEditResult.Success(id))
        coVerify(exactly = 0) { delete(any(), any()) }
        coVerify(exactly = 0) { upload(any(), any()) }
    }

    @Test
    fun `본문 저장 실패는 원인과 입력을 유지하고 사진 부작용을 시작하지 않는다`() = runTest {
        val cause = IllegalStateException("DB 저장 실패")
        coEvery { update(id, input) } returns BaseResult.Error("CACHE_ERROR", "로컬 저장 실패", cause)
        val result = useCase(id, input, true) as ExerciseEditResult.ContentFailure
        assertThat(result.recordId).isEqualTo(id)
        assertThat(result.message).isEqualTo("로컬 저장 실패")
        assertThat(result.cause).isSameInstanceAs(cause)
        assertThat(input.record).isEqualTo(record)
        assertThat(input.removedImageIds).containsExactly(10L, 20L)
        coVerify(exactly = 0) { delete(any(), any()) }
        coVerify(exactly = 0) { upload(any(), any()) }
    }

    @Test
    fun `잘못된 사진 삭제 ID는 본문과 사진 작업 전에 실패한다`() = runTest {
        assertThat(useCase(id, input.copy(removedImageIds = setOf(0L, -1L)), true))
            .isInstanceOf(ExerciseEditResult.Failure::class.java)
        coVerify(exactly = 0) { update(any(), any()) }
        coVerify(exactly = 0) { delete(any(), any()) }
        coVerify(exactly = 0) { upload(any(), any()) }
    }

    @Test
    fun `본문 성공 후 사진 삭제가 끝나야 업로드하고 최초 캐시 경고를 보존한다`() = runTest {
        val warning = BaseResult.Error("CACHE_ERROR", "본문 반영 실패")
        coEvery { update(id, input) } returns BaseResult.Success(LocalExerciseSaveResult(id), warning)
        coEvery { delete(id, listOf(10L, 20L)) } returns BaseResult.Success(Unit)
        coEvery { upload(id, listOf("photo")) } returns BaseResult.Success(id)
        val result = useCase(id, input, true) as ExerciseEditResult.Success
        assertThat(result.refreshError).isSameInstanceAs(warning)
        coVerifyOrder {
            update(id, input)
            delete(id, listOf(10L, 20L))
            upload(id, listOf("photo"))
        }
    }

    @Test
    fun `사진만 삭제할 때 첫 실패는 부분 완료가 아니며 업로드를 시작하지 않는다`() = runTest {
        for (code in listOf("PHOTO_NOT_READY", "NETWORK_ERROR")) {
            val cause = IllegalStateException(code)
            coEvery { delete(id, any()) } returns BaseResult.Error(code, "사진 삭제 실패", cause)

            val result = useCase(id, input, false) as ExerciseEditResult.Failure

            assertThat(result.message).isEqualTo("사진 삭제 실패")
            assertThat(result.cause).isSameInstanceAs(cause)
        }
        coVerify(exactly = 0) { update(any(), any()) }
        coVerify(exactly = 0) { upload(any(), any()) }
    }

    @Test
    fun `사진만 추가할 때 첫 실패는 저장 없는 실패로 원인을 보존한다`() = runTest {
        for (code in listOf("PHOTO_NOT_READY", "NETWORK_ERROR")) {
            val cause = IllegalStateException(code)
            coEvery { upload(id, any()) } returns BaseResult.Error(code, "사진 추가 실패", cause)

            val result = useCase(id, input.copy(removedImageIds = emptySet()), false) as ExerciseEditResult.Failure

            assertThat(result.message).isEqualTo("사진 추가 실패")
            assertThat(result.cause).isSameInstanceAs(cause)
        }
        coVerify(exactly = 0) { update(any(), any()) }
        coVerify(exactly = 0) { delete(any(), any()) }
    }

    @Test
    fun `본문 저장 뒤 첫 사진 추가 실패는 실제 부분 완료와 본문 경고를 전달한다`() = runTest {
        val warning = BaseResult.Error("CACHE_ERROR", "본문 반영 실패")
        val photoInput = input.copy(removedImageIds = emptySet())
        coEvery { update(id, photoInput) } returns BaseResult.Success(LocalExerciseSaveResult(id), warning)
        coEvery { upload(id, any()) } returns BaseResult.Error("NETWORK_ERROR", "사진 추가 실패")

        val result = useCase(id, photoInput, true) as ExerciseEditResult.ImageFailure

        assertThat(result.recordId).isEqualTo(id)
        assertThat(result.message).isEqualTo("사진 추가 실패")
        assertThat(result.refreshError).isSameInstanceAs(warning)
        coVerify(exactly = 0) { delete(any(), any()) }
    }

    @Test
    fun `삭제 실패는 업로드를 막고 본문 경고와 사진 오류를 함께 전달한다`() = runTest {
        val warning = BaseResult.Error("CACHE_ERROR", "본문 반영 실패")
        coEvery { update(id, input) } returns BaseResult.Success(LocalExerciseSaveResult(id), warning)
        coEvery { delete(id, any()) } returns BaseResult.Error("PHOTO_NOT_READY", "사진 변경 불가")
        val result = useCase(id, input, true) as ExerciseEditResult.ImageFailure
        assertThat(result.message).isEqualTo("사진 변경 불가")
        assertThat(result.refreshError).isSameInstanceAs(warning)
        coVerify(exactly = 0) { upload(any(), any()) }
    }

    @Test
    fun `업로드 실패는 먼저 완료된 삭제의 캐시 경고를 잃지 않는다`() = runTest {
        val warning = BaseResult.Error("CACHE_ERROR", "삭제 반영 실패")
        coEvery { delete(id, any()) } returns BaseResult.Success(Unit, warning)
        coEvery { upload(id, any()) } returns BaseResult.Error("UPLOAD_FAIL", "업로드 실패")
        val result = useCase(id, input, false) as ExerciseEditResult.ImageFailure
        assertThat(result.message).isEqualTo("업로드 실패")
        assertThat(result.refreshError).isSameInstanceAs(warning)
        coVerify(exactly = 0) { update(any(), any()) }
    }

    @Test
    fun `사진만 삭제하거나 업로드해도 각 작업의 경고를 전달한다`() = runTest {
        val warning = BaseResult.Error("CACHE_ERROR", "사진 반영 실패")
        coEvery { delete(id, any()) } returns BaseResult.Success(Unit, warning)
        coEvery { upload(id, any()) } returns BaseResult.Success(id, warning)
        val deleted = useCase(id, input.copy(addedImageUris = emptyList()), false) as ExerciseEditResult.Success
        val uploaded = useCase(id, input.copy(removedImageIds = emptySet()), false) as ExerciseEditResult.Success
        assertThat(deleted.refreshError).isSameInstanceAs(warning)
        assertThat(uploaded.refreshError).isSameInstanceAs(warning)
        coVerify(exactly = 0) { update(any(), any()) }
    }

    @Test
    fun `사진 처리 예외도 앞선 저장이 있을 때만 부분 완료로 전달한다`() = runTest {
        val cause = IllegalStateException("사진 처리 예외")
        coEvery { upload(id, any()) } throws cause
        val first = useCase(id, input.copy(removedImageIds = emptySet()), false) as ExerciseEditResult.Failure
        assertThat(first.cause).isSameInstanceAs(cause)

        val warning = BaseResult.Error("CACHE_ERROR", "삭제 반영 실패")
        coEvery { delete(id, any()) } returns BaseResult.Success(Unit, warning)
        val partial = useCase(id, input, false) as ExerciseEditResult.ImageFailure
        assertThat(partial.recordId).isEqualTo(id)
        assertThat(partial.cause).isSameInstanceAs(cause)
        assertThat(partial.refreshError).isSameInstanceAs(warning)
        coVerify(exactly = 0) { update(any(), any()) }
    }

    @Test
    fun `예상하지 못한 저장 예외는 실패로 전달하며 취소는 다시 던진다`() = runTest {
        coEvery { update(id, any()) } throws IllegalStateException("저장 예외")
        val result = useCase(id, ExerciseWriteInput(record), true) as ExerciseEditResult.Failure
        assertThat(result.cause).isInstanceOf(IllegalStateException::class.java)
        coEvery { update(id, any()) } throws CancellationException("취소")
        assertThat(runCatching { useCase(id, ExerciseWriteInput(record), true) }.exceptionOrNull())
            .isInstanceOf(CancellationException::class.java)
    }
}
