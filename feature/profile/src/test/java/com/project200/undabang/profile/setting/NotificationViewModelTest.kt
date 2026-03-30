package com.project200.undabang.profile.setting

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.NotificationState
import com.project200.domain.model.NotificationType
import com.project200.domain.usecase.GetNotificationStateUseCase
import com.project200.domain.usecase.UpdateNotificationStateUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
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
class NotificationViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetNotiStateUseCase: GetNotificationStateUseCase

    @MockK
    private lateinit var mockUpdateNotiSettingUseCase: UpdateNotificationStateUseCase

    private lateinit var viewModel: NotificationViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleNotificationStates =
        listOf(
            NotificationState(NotificationType.WORKOUT_REMINDER, true),
            NotificationState(NotificationType.CHAT_MESSAGE, false),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NotificationViewModel {
        return NotificationViewModel(
            getNotiStateUseCase = mockGetNotiStateUseCase,
            updateNotiSettingUseCase = mockUpdateNotiSettingUseCase,
        )
    }

    @Test
    fun `initNotiState - 권한 있을 때 getNotificationState가 호출된다`() =
        runTest {
            coEvery { mockGetNotiStateUseCase() } returns BaseResult.Success(sampleNotificationStates)

            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockGetNotiStateUseCase() }
            assertThat(viewModel.notificationStates.value).isEqualTo(sampleNotificationStates)
        }

    @Test
    fun `initNotiState - 권한 없을 때 모든 알림이 비활성화된 기본 상태가 설정된다`() =
        runTest {
            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = false)

            val states = viewModel.notificationStates.value
            assertThat(states).hasSize(2)
            assertThat(states.all { !it.enabled }).isTrue()
        }

    @Test
    fun `initNotiState - API 에러 시 toastMessage가 설정된다`() =
        runTest {
            coEvery { mockGetNotiStateUseCase() } returns BaseResult.Error("ERROR", "로드 실패")

            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.toastMessage.value).isEqualTo("로드 실패")
        }

    @Test
    fun `updateNotiStateByPermission - 권한 거부 시 모든 알림이 비활성화된다`() =
        runTest {
            coEvery { mockGetNotiStateUseCase() } returns BaseResult.Success(sampleNotificationStates)

            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.updateNotiStateByPermission(isGranted = false)

            val states = viewModel.notificationStates.value
            assertThat(states.all { !it.enabled }).isTrue()
        }

    @Test
    fun `updateNotiStateByPermission - 권한 거부에서 허용으로 변경 시 대기 중인 설정이 적용된다`() =
        runTest {
            coEvery { mockUpdateNotiSettingUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = false)

            viewModel.onSwitchToggled(NotificationType.WORKOUT_REMINDER, true)
            assertThat(viewModel.permissionRequestTrigger.value).isTrue()

            viewModel.updateNotiStateByPermission(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockUpdateNotiSettingUseCase(any()) }
        }

    @Test
    fun `onSwitchToggled - 권한 없이 활성화 시도 시 권한 요청 트리거가 발생한다`() =
        runTest {
            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = false)

            viewModel.onSwitchToggled(NotificationType.WORKOUT_REMINDER, true)

            assertThat(viewModel.permissionRequestTrigger.value).isTrue()
        }

    @Test
    fun `onSwitchToggled - 권한 있을 때 updateSetting이 호출된다`() =
        runTest {
            coEvery { mockGetNotiStateUseCase() } returns BaseResult.Success(sampleNotificationStates)
            coEvery { mockUpdateNotiSettingUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onSwitchToggled(NotificationType.CHAT_MESSAGE, true)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { mockUpdateNotiSettingUseCase(any()) }
        }

    @Test
    fun `updateSetting - 성공 시 UI가 업데이트된다`() =
        runTest {
            coEvery { mockGetNotiStateUseCase() } returns BaseResult.Success(sampleNotificationStates)
            coEvery { mockUpdateNotiSettingUseCase(any()) } returns BaseResult.Success(Unit)

            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onSwitchToggled(NotificationType.CHAT_MESSAGE, true)
            testDispatcher.scheduler.advanceUntilIdle()

            val chatMessageState = viewModel.notificationStates.value.find { it.type == NotificationType.CHAT_MESSAGE }
            assertThat(chatMessageState?.enabled).isTrue()
        }

    @Test
    fun `updateSetting - 실패 시 UI가 원상복구되고 토스트 메시지가 설정된다`() =
        runTest {
            coEvery { mockGetNotiStateUseCase() } returns BaseResult.Success(sampleNotificationStates)
            coEvery { mockUpdateNotiSettingUseCase(any()) } returns BaseResult.Error("ERROR", "업데이트 실패")

            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            val originalState = viewModel.notificationStates.value.find { it.type == NotificationType.CHAT_MESSAGE }?.enabled

            viewModel.onSwitchToggled(NotificationType.CHAT_MESSAGE, true)
            testDispatcher.scheduler.advanceUntilIdle()

            val revertedState = viewModel.notificationStates.value.find { it.type == NotificationType.CHAT_MESSAGE }?.enabled
            assertThat(revertedState).isEqualTo(originalState)
            assertThat(viewModel.toastMessage.value).isEqualTo("업데이트 실패")
        }

    @Test
    fun `onPermissionRequestHandled - 트리거가 리셋된다`() =
        runTest {
            viewModel = createViewModel()
            viewModel.initNotiState(isGranted = false)

            viewModel.onSwitchToggled(NotificationType.WORKOUT_REMINDER, true)
            assertThat(viewModel.permissionRequestTrigger.value).isTrue()

            viewModel.onPermissionRequestHandled()

            assertThat(viewModel.permissionRequestTrigger.value).isFalse()
        }

    @Test
    fun `updateNotiStateByPermission - 초기화 전 호출 시 initNotiState가 호출된다`() =
        runTest {
            coEvery { mockGetNotiStateUseCase() } returns BaseResult.Success(sampleNotificationStates)

            viewModel = createViewModel()
            viewModel.updateNotiStateByPermission(isGranted = true)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockGetNotiStateUseCase() }
        }
}
