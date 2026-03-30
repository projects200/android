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
class EditProfileUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockRepository: MemberRepository

    private lateinit var useCase: EditProfileUseCase

    private val sampleNickname = "운동왕"
    private val sampleGender = "M"
    private val sampleIntroduction = "매일 운동하는 직장인입니다"

    @Before
    fun setUp() {
        useCase = EditProfileUseCase(mockRepository)
    }

    @Test
    fun `invoke 호출 시 프로필 수정 성공`() = runTest {
        // Given
        val successResult = BaseResult.Success(Unit)
        coEvery {
            mockRepository.editUserProfile(sampleNickname, sampleGender, sampleIntroduction)
        } returns successResult

        // When
        val result = useCase(sampleNickname, sampleGender, sampleIntroduction)

        // Then
        coVerify(exactly = 1) {
            mockRepository.editUserProfile(sampleNickname, sampleGender, sampleIntroduction)
        }
        assertThat(result).isEqualTo(successResult)
    }

    @Test
    fun `프로필 수정 실패`() = runTest {
        // Given
        val errorResult = BaseResult.Error("EDIT_FAILED", "Failed to edit profile")
        coEvery {
            mockRepository.editUserProfile(sampleNickname, sampleGender, sampleIntroduction)
        } returns errorResult

        // When
        val result = useCase(sampleNickname, sampleGender, sampleIntroduction)

        // Then
        assertThat(result).isEqualTo(errorResult)
    }

    @Test
    fun `빈 소개글로 수정`() = runTest {
        // Given
        val emptyIntro = ""
        val successResult = BaseResult.Success(Unit)
        coEvery {
            mockRepository.editUserProfile(sampleNickname, sampleGender, emptyIntro)
        } returns successResult

        // When
        val result = useCase(sampleNickname, sampleGender, emptyIntro)

        // Then
        coVerify(exactly = 1) {
            mockRepository.editUserProfile(sampleNickname, sampleGender, emptyIntro)
        }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `여성으로 성별 변경`() = runTest {
        // Given
        val femaleGender = "F"
        val successResult = BaseResult.Success(Unit)
        coEvery {
            mockRepository.editUserProfile(sampleNickname, femaleGender, sampleIntroduction)
        } returns successResult

        // When
        val result = useCase(sampleNickname, femaleGender, sampleIntroduction)

        // Then
        coVerify(exactly = 1) {
            mockRepository.editUserProfile(sampleNickname, femaleGender, sampleIntroduction)
        }
        assertThat(result).isInstanceOf(BaseResult.Success::class.java)
    }

    @Test
    fun `중복된 닉네임으로 수정 시 에러`() = runTest {
        // Given
        val duplicateNickname = "이미존재하는닉네임"
        val errorResult = BaseResult.Error("DUPLICATE_NICKNAME", "Nickname already exists")
        coEvery {
            mockRepository.editUserProfile(duplicateNickname, sampleGender, sampleIntroduction)
        } returns errorResult

        // When
        val result = useCase(duplicateNickname, sampleGender, sampleIntroduction)

        // Then
        assertThat((result as BaseResult.Error).errorCode).isEqualTo("DUPLICATE_NICKNAME")
    }
}
