package com.project200.undabang.profile.setting

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
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var getState: GetNotificationStateUseCase

    @MockK
    private lateinit var updateState: UpdateNotificationStateUseCase

    private val dispatcher = StandardTestDispatcher()
    private val original =
        listOf(
            NotificationState(NotificationType.WORKOUT_REMINDER, true),
            NotificationState(NotificationType.CHAT_MESSAGE, false),
            NotificationState(NotificationType.UNKNOWN, true),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getState() } returns BaseResult.Success(original)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `조회가 성공한 뒤에만 ready 상태가 된다`() =
        runTest {
            val model = NotificationViewModel(getState, updateState)
            model.initNotiState(true)
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Loading)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Ready)
            assertThat(model.notificationStates.value).isEqualTo(original)
        }

    @Test
    fun `권한 거부 중에도 실제 서버 조회가 실패하면 OFF 성공 화면이 아니다`() =
        runTest {
            coEvery { getState() } returns BaseResult.Error("ERROR", "로드 실패")
            val model = NotificationViewModel(getState, updateState)
            model.initNotiState(false)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Error)
            assertThat(model.notificationStates.value).isEmpty()
            assertThat(model.toastMessage.value).isEqualTo("로드 실패")
        }

    @Test
    fun `필수 알림 행이 없으면 기본 OFF로 대체하지 않는다`() =
        runTest {
            coEvery { getState() } returns BaseResult.Success(original.filterNot { it.type == NotificationType.CHAT_MESSAGE })
            val model = NotificationViewModel(getState, updateState)
            model.initNotiState(true)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Error)
        }

    @Test
    fun `PATCH 완료와 재조회 전에는 낙관적 checked 상태나 다음 쓰기를 허용하지 않는다`() =
        runTest {
            val patch = CompletableDeferred<BaseResult<Unit>>()
            coEvery { updateState(any()) } coAnswers { patch.await() }
            val model = NotificationViewModel(getState, updateState)
            model.initNotiState(true)
            dispatcher.scheduler.advanceUntilIdle()
            model.onSwitchToggled(NotificationType.CHAT_MESSAGE, true)
            model.onSwitchToggled(NotificationType.WORKOUT_REMINDER, false)
            dispatcher.scheduler.runCurrent()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Saving)
            assertThat(model.notificationStates.value).isEqualTo(original)
            coVerify(exactly = 1) { updateState(listOf(NotificationState(NotificationType.CHAT_MESSAGE, true))) }
            coEvery { getState() } returns
                BaseResult.Success(
                    original.map {
                        if (it.type == NotificationType.CHAT_MESSAGE) it.copy(enabled = true) else it
                    },
                )
            patch.complete(BaseResult.Success(Unit))
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Ready)
            assertThat(model.notificationStates.value.first { it.type == NotificationType.CHAT_MESSAGE }.enabled).isTrue()
        }

    @Test
    fun `PATCH 실패 또는 재조회 불일치는 settled 성공이 아니다`() =
        runTest {
            coEvery { updateState(any()) } returns BaseResult.Error("ERROR", "업데이트 실패")
            val model = NotificationViewModel(getState, updateState)
            model.initNotiState(true)
            dispatcher.scheduler.advanceUntilIdle()
            model.onSwitchToggled(NotificationType.CHAT_MESSAGE, true)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Error)
            assertThat(model.toastMessage.value).isEqualTo("업데이트 실패")
            assertThat(model.notificationStates.value).isEqualTo(original)
            model.updateNotiStateByPermission(true)
            dispatcher.scheduler.advanceUntilIdle()
            coEvery { updateState(any()) } returns BaseResult.Success(Unit)
            model.onSwitchToggled(NotificationType.CHAT_MESSAGE, true)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Error)
        }

    @Test
    fun `권한 거부는 화면만 OFF로 만들고 서버 설정을 수정하지 않는다`() =
        runTest {
            val model = NotificationViewModel(getState, updateState)
            model.initNotiState(false)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.notificationStates.value.all { !it.enabled }).isTrue()
            assertThat(model.syncState.value).isEqualTo(NotificationSyncState.Ready)
            coVerify(exactly = 0) { updateState(any()) }
            model.updateNotiStateByPermission(true)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(model.notificationStates.value).isEqualTo(original)
        }

    @Test
    fun `권한 요청은 처리 후 초기화되고 허용 후 해당 type만 저장한다`() =
        runTest {
            coEvery { updateState(any()) } returns BaseResult.Success(Unit)
            val enabled = original.map { if (it.type == NotificationType.CHAT_MESSAGE) it.copy(enabled = true) else it }
            val model = NotificationViewModel(getState, updateState)
            model.updateNotiStateByPermission(false)
            dispatcher.scheduler.advanceUntilIdle()
            model.onSwitchToggled(NotificationType.CHAT_MESSAGE, true)
            assertThat(model.permissionRequestTrigger.value).isTrue()
            model.onPermissionRequestHandled()
            assertThat(model.permissionRequestTrigger.value).isFalse()
            coEvery { getState() } returnsMany listOf(BaseResult.Success(original), BaseResult.Success(enabled))
            model.updateNotiStateByPermission(true)
            dispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 1) { updateState(listOf(NotificationState(NotificationType.CHAT_MESSAGE, true))) }
            assertThat(model.notificationStates.value).isEqualTo(enabled)
        }
}
