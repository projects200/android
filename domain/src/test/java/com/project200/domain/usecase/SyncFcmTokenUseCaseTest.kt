package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.FcmTokenSyncResult
import com.project200.domain.repository.AuthRepository
import com.project200.domain.repository.FcmRepository
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
class SyncFcmTokenUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mockAuthRepository: AuthRepository

    @MockK
    private lateinit var mockFcmRepository: FcmRepository

    private lateinit var useCase: SyncFcmTokenUseCase

    @Before
    fun setUp() {
        useCase = SyncFcmTokenUseCase(mockAuthRepository, mockFcmRepository)
    }

    @Test
    fun `로그인 전이면 전송하지 않고 SKIPPED를 반환한다`() = runTest {
        // Given: 회원ID가 없다 = 로그인 전
        coEvery { mockAuthRepository.getMemberId() } returns null

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(FcmTokenSyncResult.SKIPPED)
        coVerify(exactly = 0) { mockAuthRepository.login() }
    }

    @Test
    fun `저장된 토큰이 없으면 전송하지 않고 SKIPPED를 반환한다`() = runTest {
        // Given
        coEvery { mockAuthRepository.getMemberId() } returns "member-1"
        coEvery { mockFcmRepository.getFcmTokenFromPrefs() } returns null

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(FcmTokenSyncResult.SKIPPED)
        coVerify(exactly = 0) { mockAuthRepository.login() }
    }

    @Test
    fun `전송에 성공하면 SUCCESS를 반환한다`() = runTest {
        // Given
        coEvery { mockAuthRepository.getMemberId() } returns "member-1"
        coEvery { mockFcmRepository.getFcmTokenFromPrefs() } returns "token-1"
        coEvery { mockAuthRepository.login() } returns BaseResult.Success(Unit)

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(FcmTokenSyncResult.SUCCESS)
        coVerify(exactly = 1) { mockAuthRepository.login() }
    }

    @Test
    fun `전송에 실패하면 FAILURE를 반환한다`() = runTest {
        // Given
        coEvery { mockAuthRepository.getMemberId() } returns "member-1"
        coEvery { mockFcmRepository.getFcmTokenFromPrefs() } returns "token-1"
        coEvery { mockAuthRepository.login() } returns BaseResult.Error("NETWORK_ERROR", "네트워크 오류")

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(FcmTokenSyncResult.FAILURE)
    }

    @Test
    fun `토큰이 없어 로그인이 막히면 FAILURE를 반환한다`() = runTest {
        // Given: 조회 직후 토큰이 지워져 login()이 NO_FCM_TOKEN으로 막힌 경우
        coEvery { mockAuthRepository.getMemberId() } returns "member-1"
        coEvery { mockFcmRepository.getFcmTokenFromPrefs() } returns "token-1"
        coEvery { mockAuthRepository.login() } returns
            BaseResult.Error("NO_FCM_TOKEN", "FCM 토큰이 없어 서버 로그인을 보내지 않았습니다.")

        // When
        val result = useCase()

        // Then
        assertThat(result).isEqualTo(FcmTokenSyncResult.FAILURE)
    }

    @Test
    fun `대기 중 토큰이 갱신되면 호출 시점의 값을 읽는다`() = runTest {
        // Given: 예약 시점의 token-1이 아니라 실행 시점의 token-2가 저장소에 있다
        coEvery { mockAuthRepository.getMemberId() } returns "member-1"
        coEvery { mockFcmRepository.getFcmTokenFromPrefs() } returns "token-1" andThen "token-2"
        coEvery { mockAuthRepository.login() } returns BaseResult.Success(Unit)
        useCase() // 첫 실행이 token-1을 읽는다

        // When: 두 번째 실행
        val result = useCase()

        // Then: 저장소를 매번 다시 읽는다
        assertThat(result).isEqualTo(FcmTokenSyncResult.SUCCESS)
        coVerify(exactly = 2) { mockFcmRepository.getFcmTokenFromPrefs() }
    }
}
