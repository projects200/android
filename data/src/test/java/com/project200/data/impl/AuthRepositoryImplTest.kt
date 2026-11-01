package com.project200.data.impl

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.dto.PostSignUpData
import com.project200.data.local.PreferenceManager
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.BaseResult
import com.project200.domain.model.RegistrationStatus
import com.project200.undabang.oauth.AuthStateManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryImplTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockApiService: ApiService

    @MockK
    private lateinit var mockPreferenceManager: PreferenceManager

    @MockK
    private lateinit var mockAuthStateManager: AuthStateManager

    @MockK
    private lateinit var mockAuthState: AuthState

    @MockK
    private lateinit var mockSessionDataCleaner: SessionDataCleaner

    private lateinit var repository: AuthRepositoryImpl
    private var token: String? = null

    @Before
    fun setUp() {
        token = jwt("cognito-subject")
        every { mockAuthStateManager.getCurrent() } returns mockAuthState
        every { mockAuthState.idToken } answers { token }
        every { mockPreferenceManager.saveMemberId(any(), any()) } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } just Runs

        repository =
            AuthRepositoryImpl(
                apiService = mockApiService,
                spManager = mockPreferenceManager,
                authStateManager = mockAuthStateManager,
                sessionDataCleaner = mockSessionDataCleaner,
                ioDispatcher = UnconfinedTestDispatcher(),
                imageFileStore = mockk(relaxed = true),
            )
    }

    private fun jwt(subject: String) =
        "e30." +
            Base64.encodeToString(
                "{\"sub\":\"$subject\"}".toByteArray(),
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
            ) + ".signature"

    private fun stubIsRegistered(
        memberId: String,
        isRegistered: Boolean = true,
    ) {
        coEvery { mockApiService.getIsRegistered() } returns
            BaseResponse(
                succeed = true,
                code = "SUCCESS",
                message = "",
                data = GetIsRegisteredData(memberId = memberId, isRegistered = isRegistered),
            )
    }

    // ── 계정 경계 ────────────────────────────────

    @Test
    fun `가입 확인 - 다른 계정으로 바뀌면 저장 전에 로컬 캐시를 지운다`() =
        runTest {
            // Given: 이전 계정이 기기에 남아 있음
            every { mockPreferenceManager.getMemberId() } returns "member-a"
            stubIsRegistered("member-b")

            // When
            val result = repository.checkIsRegistered()

            // Then
            assertThat(result).isEqualTo(RegistrationStatus.Registered)
            coVerify(exactly = 1) { mockSessionDataCleaner.clearAll() }
            verify(exactly = 1) { mockPreferenceManager.saveMemberId("member-b", "cognito-subject") }
        }

    @Test
    fun `가입 확인 - 같은 계정이면 로컬 캐시를 지우지 않는다`() =
        runTest {
            // Given
            every { mockPreferenceManager.getMemberId() } returns "member-a"
            stubIsRegistered("member-a")

            // When
            val result = repository.checkIsRegistered()

            // Then
            assertThat(result).isEqualTo(RegistrationStatus.Registered)
            coVerify(exactly = 0) { mockSessionDataCleaner.clearAll() }
            verify(exactly = 1) { mockPreferenceManager.saveMemberId("member-a", "cognito-subject") }
        }

    @Test
    fun `가입 확인 - 요청 중 토큰 subject가 바뀌면 subject를 저장하지 않는다`() =
        runTest {
            every { mockPreferenceManager.getMemberId() } returns "member-a"
            coEvery { mockApiService.getIsRegistered() } answers {
                token = jwt("other-subject")
                BaseResponse(
                    succeed = true,
                    code = "SUCCESS",
                    message = "",
                    data = GetIsRegisteredData(memberId = "member-a", isRegistered = true),
                )
            }

            val result = repository.checkIsRegistered()

            assertThat(result).isEqualTo(RegistrationStatus.Registered)
            coVerify(exactly = 0) { mockSessionDataCleaner.clearAll() }
            verify(exactly = 1) { mockPreferenceManager.saveMemberId("member-a", null) }
        }

    @Test
    fun `가입 확인 - 이전 회원ID가 없으면 로컬 캐시를 지운다`() =
        runTest {
            // Given: 로그아웃 도중 캐시 삭제만 실패한 상태를 흡수한다
            every { mockPreferenceManager.getMemberId() } returns null
            stubIsRegistered("member-b")

            // When
            repository.checkIsRegistered()

            // Then
            coVerify(exactly = 1) { mockSessionDataCleaner.clearAll() }
        }

    @Test
    fun `가입 확인 - 미가입이면 회원ID를 저장하지 않고 캐시도 건드리지 않는다`() =
        runTest {
            // Given
            stubIsRegistered("member-b", isRegistered = false)

            // When
            val result = repository.checkIsRegistered()

            // Then
            assertThat(result).isEqualTo(RegistrationStatus.Unregistered)
            verify(exactly = 0) { mockPreferenceManager.saveMemberId(any(), any()) }
            coVerify(exactly = 0) { mockSessionDataCleaner.clearAll() }
        }

    @Test
    fun `회원가입 - 요청 전후 토큰 subject가 같으면 서버 회원ID와 함께 저장한다`() =
        runTest {
            val birth = LocalDate.of(2000, 1, 1)
            coEvery { mockApiService.postSignUp(any()) } returns
                BaseResponse(
                    succeed = true,
                    code = "SUCCESS",
                    message = "",
                    data =
                        PostSignUpData(
                            memberId = "member-b",
                            memberEmail = "member@example.test",
                            memberGender = "FEMALE",
                            memberBday = birth,
                            memberNickname = "회원",
                            memberDesc = null,
                            memberScore = null,
                            memberCreatedAt = LocalDateTime.of(2026, 10, 30, 0, 0),
                        ),
                )

            val result = repository.signUp("FEMALE", "회원", birth)

            assertThat(result).isEqualTo(BaseResult.Success(Unit))
            verify(exactly = 1) { mockPreferenceManager.saveMemberId("member-b", "cognito-subject") }
        }

    @Test
    fun `회원가입 - 요청 중 토큰 subject가 바뀌면 subject를 저장하지 않는다`() =
        runTest {
            val birth = LocalDate.of(2000, 1, 1)
            coEvery { mockApiService.postSignUp(any()) } answers {
                token = jwt("other-subject")
                BaseResponse(
                    succeed = true,
                    code = "SUCCESS",
                    message = "",
                    data =
                        PostSignUpData(
                            memberId = "member-b",
                            memberEmail = "member@example.test",
                            memberGender = "FEMALE",
                            memberBday = birth,
                            memberNickname = "회원",
                            memberDesc = null,
                            memberScore = null,
                            memberCreatedAt = LocalDateTime.of(2026, 10, 30, 0, 0),
                        ),
                )
            }

            val result = repository.signUp("FEMALE", "회원", birth)

            assertThat(result).isEqualTo(BaseResult.Success(Unit))
            verify(exactly = 1) { mockPreferenceManager.saveMemberId("member-b", null) }
        }
}
