package com.project200.data.impl

import com.google.common.truth.Truth.assertThat
import com.project200.data.api.ApiService
import com.project200.data.dto.BaseResponse
import com.project200.data.dto.GetIsRegisteredData
import com.project200.data.local.PreferenceManager
import com.project200.domain.manager.SessionDataCleaner
import com.project200.domain.model.RegistrationStatus
import com.project200.undabang.oauth.AuthStateManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
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
    private lateinit var mockSessionDataCleaner: SessionDataCleaner

    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        every { mockPreferenceManager.saveMemberId(any()) } just Runs
        coEvery { mockSessionDataCleaner.clearAll() } just Runs

        repository =
            AuthRepositoryImpl(
                apiService = mockApiService,
                spManager = mockPreferenceManager,
                authStateManager = mockAuthStateManager,
                sessionDataCleaner = mockSessionDataCleaner,
                ioDispatcher = UnconfinedTestDispatcher(),
            )
    }

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
            verify(exactly = 1) { mockPreferenceManager.saveMemberId("member-b") }
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
            verify(exactly = 1) { mockPreferenceManager.saveMemberId("member-a") }
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
            verify(exactly = 0) { mockPreferenceManager.saveMemberId(any()) }
            coVerify(exactly = 0) { mockSessionDataCleaner.clearAll() }
        }
}
