package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.repository.MemberRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DeleteProfileImageUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: DeleteProfileImageUseCase

    private val samplePictureId = 123L

    @Before
    fun setUp() {
        useCase = DeleteProfileImageUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 프로필 이미지 삭제 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteProfileImages(samplePictureId) } returns successResult

        // When
        val result = useCase(samplePictureId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteProfileImages(samplePictureId) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `프로필 이미지 삭제 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("DELETE_FAILED", "Failed to delete image")
        coEvery { mockRepository.deleteProfileImages(samplePictureId) } returns errorResult

        // When
        val result = useCase(samplePictureId)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("DELETE_FAILED")
    }

    @Test
    fun `존재하지 않는 이미지 삭제 시 에러`() = runTest {
        // Given
        val nonExistentId = 999L
        val errorResult = BaseResult.Error("NOT_FOUND", "Image not found")
        coEvery { mockRepository.deleteProfileImages(nonExistentId) } returns errorResult

        // When
        val result = useCase(nonExistentId)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `다른 이미지 ID로 삭제`() = runTest {
        // Given
        val anotherId = 456L
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.deleteProfileImages(anotherId) } returns successResult

        // When
        val result = useCase(anotherId)

        // Then
        coVerify(exactly = 1) { mockRepository.deleteProfileImages(anotherId) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }
}
