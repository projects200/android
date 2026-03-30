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
class AddProfileImageUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: AddProfileImageUseCase

    private val sampleImageUri = "content://media/external/images/media/12345"

    @Before
    fun setUp() {
        useCase = AddProfileImageUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 프로필 이미지 추가 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.addProfileImage(sampleImageUri) } returns successResult

        // When
        val result = useCase(sampleImageUri)

        // Then
        coVerify(exactly = 1) { mockRepository.addProfileImage(sampleImageUri) }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `프로필 이미지 추가 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("UPLOAD_FAILED", "Failed to upload image")
        coEvery { mockRepository.addProfileImage(sampleImageUri) } returns errorResult

        // When
        val result = useCase(sampleImageUri)

        // Then
        assertThat(result).isEqualTo(errorResult)
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("UPLOAD_FAILED")
    }

    @Test
    fun `다른 이미지 URI로 추가`() = runTest {
        // Given
        val anotherUri = "content://media/external/images/media/67890"
        val successResult = BaseResult.Success(Unit)
        coEvery { mockRepository.addProfileImage(anotherUri) } returns successResult

        // When
        val result = useCase(anotherUri)

        // Then
        coVerify(exactly = 1) { mockRepository.addProfileImage(anotherUri) }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `네트워크 오류로 실패`() = runTest {
        // Given
        val networkError = BaseResult.Error("NETWORK_ERROR", "Network connection failed")
        coEvery { mockRepository.addProfileImage(sampleImageUri) } returns networkError

        // When
        val result = useCase(sampleImageUri)

        // Then
        assertThat((result as BaseResult.Error).message).isEqualTo("Network connection failed")
    }
}
