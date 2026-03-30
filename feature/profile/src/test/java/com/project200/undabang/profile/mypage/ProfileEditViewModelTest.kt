package com.project200.undabang.profile.mypage

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.PreferredExercise
import com.project200.domain.model.UserProfile
import com.project200.domain.usecase.AddProfileImageUseCase
import com.project200.domain.usecase.CheckNicknameDuplicatedUseCase
import com.project200.domain.usecase.EditProfileUseCase
import com.project200.domain.usecase.GetUserProfileUseCase
import com.project200.domain.usecase.ValidateNicknameUseCase
import com.project200.undabang.profile.utils.NicknameValidationState
import com.project200.undabang.profile.utils.ProfileEditErrorType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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

@ExperimentalCoroutinesApi
class ProfileEditViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockValidateNicknameUseCase: ValidateNicknameUseCase

    @MockK
    private lateinit var mockGetUserProfileUseCase: GetUserProfileUseCase

    @MockK
    private lateinit var mockCheckNicknameDuplicatedUseCase: CheckNicknameDuplicatedUseCase

    @MockK
    private lateinit var mockEditProfileUseCase: EditProfileUseCase

    @MockK
    private lateinit var mockAddProfileImageUseCase: AddProfileImageUseCase

    private lateinit var viewModel: ProfileEditViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleProfile =
        UserProfile(
            profileThumbnailUrl = "https://example.com/thumb.jpg",
            profileImageUrl = "https://example.com/image.jpg",
            nickname = "기존닉네임",
            gender = "MALE",
            birthDate = "1990-01-01",
            bio = "기존 소개",
            yearlyExerciseDays = 100,
            exerciseCountInLast30Days = 15,
            exerciseScore = 80,
            preferredExercises =
                listOf(
                    PreferredExercise(
                        preferredExerciseId = 1L,
                        exerciseTypeId = 1L,
                        name = "헬스",
                        skillLevel = "BEGINNER",
                        daysOfWeek = listOf(true, false, true, false, true, false, false),
                        imageUrl = "https://example.com/exercise.jpg",
                    ),
                ),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            ProfileEditViewModel(
                validateNicknameUseCase = mockValidateNicknameUseCase,
                getUserProfileUseCase = mockGetUserProfileUseCase,
                checkNicknameDuplicatedUseCase = mockCheckNicknameDuplicatedUseCase,
                editProfileUseCase = mockEditProfileUseCase,
                addProfileImageUseCase = mockAddProfileImageUseCase,
            )
    }

    @Test
    fun `init - ViewModel 초기화 시 getProfile이 호출된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)

            // When
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { mockGetUserProfileUseCase() }
        }

    @Test
    fun `getProfile - 성공 시 initProfile이 업데이트된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)

            // When
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.initProfile.value).isEqualTo(sampleProfile)
        }

    @Test
    fun `getProfile - 실패 시 LOAD_FAILED 에러가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Error("ERROR", "로드 실패")

            // When
            createViewModel()

            viewModel.errorType.test {
                testDispatcher.scheduler.advanceUntilIdle()

                // Then
                assertThat(awaitItem()).isEqualTo(ProfileEditErrorType.LOAD_FAILED)
            }
        }

    @Test
    fun `updateNickname - 닉네임 변경 시 isNicknameChecked가 false로 리셋된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.updateNickname("새닉네임")

            // Then
            assertThat(viewModel.isNicknameChecked.value).isFalse()
            assertThat(viewModel.nicknameValidationState.value).isEqualTo(NicknameValidationState.INVISIBLE)
        }

    @Test
    fun `updateNickname - 동일한 닉네임으로 변경 시 상태가 변하지 않는다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNickname("테스트닉네임")
            val initialState = viewModel.nicknameValidationState.value

            // When - 동일한 값으로 다시 호출
            viewModel.updateNickname("테스트닉네임")

            // Then - 상태 변경 없음
            assertThat(viewModel.nicknameValidationState.value).isEqualTo(initialState)
        }

    @Test
    fun `selectGender - 성별 선택 시 gender가 업데이트된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.selectGender("FEMALE")

            // Then
            assertThat(viewModel.gender.value).isEqualTo("FEMALE")
        }

    @Test
    fun `updateProfileImageUri - 프로필 이미지 URI가 업데이트된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val mockUri = mockk<Uri>()

            // When
            viewModel.updateProfileImageUri(mockUri)

            // Then
            assertThat(viewModel.newProfileImageUri.value).isEqualTo(mockUri)
        }

    @Test
    fun `checkIsNicknameDuplicated - 기존 닉네임과 동일하면 SAME_AS_ORIGINAL 에러가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNickname(sampleProfile.nickname)

            // When & Then
            viewModel.errorType.test {
                viewModel.checkIsNicknameDuplicated()
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(ProfileEditErrorType.SAME_AS_ORIGINAL)
            }
        }

    @Test
    fun `checkIsNicknameDuplicated - 닉네임 유효성 검사 실패 시 INVALID 상태가 설정된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            every { mockValidateNicknameUseCase(any()) } returns false
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNickname("유효하지않은닉네임!@#")

            // When
            viewModel.checkIsNicknameDuplicated()

            // Then
            assertThat(viewModel.nicknameValidationState.value).isEqualTo(NicknameValidationState.INVALID)
        }

    @Test
    fun `checkIsNicknameDuplicated - 사용 가능한 닉네임이면 AVAILABLE 상태가 설정된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            every { mockValidateNicknameUseCase(any()) } returns true
            coEvery { mockCheckNicknameDuplicatedUseCase(any()) } returns BaseResult.Success(true)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNickname("새닉네임")

            // When
            viewModel.checkIsNicknameDuplicated()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.nicknameValidationState.value).isEqualTo(NicknameValidationState.AVAILABLE)
            assertThat(viewModel.isNicknameChecked.value).isTrue()
        }

    @Test
    fun `checkIsNicknameDuplicated - 중복된 닉네임이면 DUPLICATED 상태가 설정된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            every { mockValidateNicknameUseCase(any()) } returns true
            coEvery { mockCheckNicknameDuplicatedUseCase(any()) } returns BaseResult.Success(false)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNickname("중복닉네임")

            // When
            viewModel.checkIsNicknameDuplicated()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.nicknameValidationState.value).isEqualTo(NicknameValidationState.DUPLICATED)
            assertThat(viewModel.isNicknameChecked.value).isFalse()
        }

    @Test
    fun `checkIsNicknameDuplicated - API 에러 시 CHECK_DUPLICATE_FAILED 에러가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            every { mockValidateNicknameUseCase(any()) } returns true
            coEvery { mockCheckNicknameDuplicatedUseCase(any()) } returns BaseResult.Error("ERROR", "서버 오류")
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNickname("새닉네임")

            // When & Then
            viewModel.errorType.test {
                viewModel.checkIsNicknameDuplicated()
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(ProfileEditErrorType.CHECK_DUPLICATE_FAILED)
            }
        }

    @Test
    fun `completeEditProfile - 변경사항 없으면 NO_CHANGE 에러가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // 초기 프로필과 동일하게 설정
            viewModel.updateNickname(sampleProfile.nickname)
            viewModel.selectGender(sampleProfile.gender)
            viewModel.updateIntroduction(sampleProfile.bio.orEmpty())

            // When & Then
            viewModel.errorType.test {
                viewModel.completeEditProfile()
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(ProfileEditErrorType.NO_CHANGE)
            }
        }

    @Test
    fun `completeEditProfile - 닉네임 변경 시 중복확인 안하면 NO_DUPLICATE_CHECKED 에러가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNickname("새닉네임")
            viewModel.selectGender(sampleProfile.gender)
            viewModel.updateIntroduction(sampleProfile.bio.orEmpty())

            // When & Then
            viewModel.errorType.test {
                viewModel.completeEditProfile()
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(ProfileEditErrorType.NO_DUPLICATE_CHECKED)
            }
        }

    @Test
    fun `completeEditProfile - 이미지와 프로필 모두 변경 성공 시 true가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            every { mockValidateNicknameUseCase(any()) } returns true
            coEvery { mockCheckNicknameDuplicatedUseCase(any()) } returns BaseResult.Success(true)
            coEvery { mockAddProfileImageUseCase(any()) } returns BaseResult.Success(Unit)
            coEvery { mockEditProfileUseCase(any(), any(), any()) } returns BaseResult.Success(Unit)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns "content://image/test"

            viewModel.updateNickname("새닉네임")
            viewModel.checkIsNicknameDuplicated()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.selectGender("FEMALE")
            viewModel.updateProfileImageUri(mockUri)

            // When & Then
            viewModel.editResult.test {
                viewModel.completeEditProfile()
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isTrue()
            }

            coVerify { mockAddProfileImageUseCase(any()) }
            coVerify { mockEditProfileUseCase("새닉네임", "FEMALE", any()) }
        }

    @Test
    fun `completeEditProfile - 이미지만 변경 시 editProfileUseCase는 호출되지 않는다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            coEvery { mockAddProfileImageUseCase(any()) } returns BaseResult.Success(Unit)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns "content://image/test"

            viewModel.updateNickname(sampleProfile.nickname)
            viewModel.selectGender(sampleProfile.gender)
            viewModel.updateIntroduction(sampleProfile.bio.orEmpty())
            viewModel.updateProfileImageUri(mockUri)

            // When & Then
            viewModel.editResult.test {
                viewModel.completeEditProfile()
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isTrue()
            }

            coVerify { mockAddProfileImageUseCase(any()) }
            coVerify(exactly = 0) { mockEditProfileUseCase(any(), any(), any()) }
        }

    @Test
    fun `completeEditProfile - 이미지 업로드 실패 시 false가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            coEvery { mockAddProfileImageUseCase(any()) } returns BaseResult.Error("ERROR", "업로드 실패")
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns "content://image/test"

            viewModel.updateNickname(sampleProfile.nickname)
            viewModel.selectGender(sampleProfile.gender)
            viewModel.updateIntroduction(sampleProfile.bio.orEmpty())
            viewModel.updateProfileImageUri(mockUri)

            // When & Then
            viewModel.editResult.test {
                viewModel.completeEditProfile()
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isFalse()
            }
        }

    @Test
    fun `postImageError - 이미지 에러가 emit된다`() =
        runTest {
            // Given
            coEvery { mockGetUserProfileUseCase() } returns BaseResult.Success(sampleProfile)
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.errorType.test {
                viewModel.postImageError(ProfileEditErrorType.LOAD_FAILED)
                testDispatcher.scheduler.advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(ProfileEditErrorType.LOAD_FAILED)
            }
        }
}
