package com.project200.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.UpdateCheckResult
import com.project200.domain.model.UpdateInfo // 테스트를 위해 UpdateInfo 모델이 필요합니다.
import com.project200.domain.repository.AppUpdateRepository
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CheckForUpdateUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var appUpdateRepository: AppUpdateRepository

    private lateinit var checkForUpdateUseCase: CheckForUpdateUseCase

    @Before
    fun setUp() {
        checkForUpdateUseCase = CheckForUpdateUseCase(appUpdateRepository)
    }

    @Test
    fun `invoke 호출 시 최신 버전이라 업데이트가 필요 없는 경우 NoUpdateNeeded를 반환한다`() = runTest {
        // Given: 현재 버전 코드가 10이고, 서버의 최신/최소 버전도 10 이하인 상황
        val currentVersionCode = 10L
        val updateInfo = UpdateInfo(latestVersionCode = 10, minRequiredVersionCode = 9)

        coEvery { appUpdateRepository.getCurrentVersionCode() } returns currentVersionCode
        coEvery { appUpdateRepository.getUpdateInfo() } returns Result.success(updateInfo)

        // When: UseCase를 실행
        val result = checkForUpdateUseCase()

        // Then: 성공적으로 NoUpdateNeeded 결과를 반환하는지 검증
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(UpdateCheckResult.NoUpdateNeeded)
    }

    @Test
    fun `invoke 호출 시 선택적 업데이트가 필요한 경우 UpdateAvailable(isForceUpdate=false)를 반환한다`() = runTest {
        // Given: 현재 버전(10)이 최소 요구 버전(10)보다는 크거나 같지만, 최신 버전(11)보다는 낮은 상황
        val currentVersionCode = 10L
        val updateInfo = UpdateInfo(latestVersionCode = 11, minRequiredVersionCode = 10)

        coEvery { appUpdateRepository.getCurrentVersionCode() } returns currentVersionCode
        coEvery { appUpdateRepository.getUpdateInfo() } returns Result.success(updateInfo)

        // When: UseCase를 실행
        val result = checkForUpdateUseCase()

        // Then: 성공적으로 선택적 업데이트 결과를 반환하는지 검증
        assertThat(result.isSuccess).isTrue()
        val updateCheckResult = result.getOrNull()
        assertThat(updateCheckResult).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        assertThat((updateCheckResult as UpdateCheckResult.UpdateAvailable).isForceUpdate).isFalse()
    }

    @Test
    fun `invoke 호출 시 강제 업데이트가 필요한 경우 UpdateAvailable(isForceUpdate=true)를 반환한다`() = runTest {
        // Given: 현재 버전(10)이 최소 요구 버전(11)보다 낮은 상황
        val currentVersionCode = 10L
        val updateInfo = UpdateInfo(latestVersionCode = 12, minRequiredVersionCode = 11)

        coEvery { appUpdateRepository.getCurrentVersionCode() } returns currentVersionCode
        coEvery { appUpdateRepository.getUpdateInfo() } returns Result.success(updateInfo)

        // When: UseCase를 실행
        val result = checkForUpdateUseCase()

        // Then: 성공적으로 강제 업데이트 결과를 반환하는지 검증
        assertThat(result.isSuccess).isTrue()
        val updateCheckResult = result.getOrNull()
        assertThat(updateCheckResult).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        assertThat((updateCheckResult as UpdateCheckResult.UpdateAvailable).isForceUpdate).isTrue()
    }

    @Test
    fun `invoke 호출 시 레포지토리에서 에러가 발생하면 Result_failure를 반환한다`() = runTest {
        // Given: getUpdateInfo 호출 시 예외가 발생하는 상황
        val networkException = RuntimeException("Network Error")
        coEvery { appUpdateRepository.getUpdateInfo() } returns Result.failure(networkException)

        // When: UseCase를 실행
        val result = checkForUpdateUseCase()

        // Then: 실패 결과를 그대로 반환하는지 검증
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(networkException)
    }
}