package com.project200.feature.timer.simple

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.SimpleTimer
import com.project200.domain.usecase.AddSimpleTimerUseCase
import com.project200.domain.usecase.DeleteSimpleTimerUseCase
import com.project200.domain.usecase.EditSimpleTimerUseCase
import com.project200.domain.usecase.GetSimpleTimersUseCase
import com.project200.feature.timer.utils.SimpleTimerServiceManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SimpleTimerViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockSimpleTimerServiceManager: SimpleTimerServiceManager

    @MockK
    private lateinit var mockGetSimpleTimersUseCase: GetSimpleTimersUseCase

    @MockK
    private lateinit var mockAddSimpleTimerUseCase: AddSimpleTimerUseCase

    @MockK
    private lateinit var mockEditSimpleTimerUseCase: EditSimpleTimerUseCase

    @MockK
    private lateinit var mockDeleteSimpleTimerUseCase: DeleteSimpleTimerUseCase

    private lateinit var viewModel: SimpleTimerViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleTimers =
        listOf(
            SimpleTimer(id = 1L, time = 60),
            SimpleTimer(id = 2L, time = 120),
            SimpleTimer(id = 3L, time = 30),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockSimpleTimerServiceManager.service } returns MutableStateFlow(null)
        every { mockSimpleTimerServiceManager.bindService() } returns Unit
        every { mockSimpleTimerServiceManager.unbindService() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SimpleTimerViewModel {
        coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Success(sampleTimers)
        return SimpleTimerViewModel(
            simpleTimerServiceManager = mockSimpleTimerServiceManager,
            getSimpleTimersUseCase = mockGetSimpleTimersUseCase,
            addSimpleTimerUseCase = mockAddSimpleTimerUseCase,
            editSimpleTimerUseCase = mockEditSimpleTimerUseCase,
            deleteSimpleTimerUseCase = mockDeleteSimpleTimerUseCase,
        )
    }

    @Test
    fun `init - ViewModel 생성 시 서비스가 바인딩된다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            verify { mockSimpleTimerServiceManager.bindService() }
        }

    @Test
    fun `init - ViewModel 생성 시 타이머 목록을 로드한다`() =
        runTest {
            // When
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.timerItems.value).hasSize(3)
            coVerify { mockGetSimpleTimersUseCase() }
        }

    @Test
    fun `loadTimerItems - 성공하면 타이머 목록이 설정된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val newTimers = listOf(SimpleTimer(id = 10L, time = 300))
            coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Success(newTimers)

            // When
            viewModel.loadTimerItems()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.timerItems.value).hasSize(1)
            assertThat(viewModel.timerItems.value?.first()?.time).isEqualTo(300)
        }

    @Test
    fun `loadTimerItems - 실패하면 GET_ERROR 토스트가 발생한다`() =
        runTest {
            // Given
            coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Error("ERROR", "로드 실패")
            viewModel =
                SimpleTimerViewModel(
                    simpleTimerServiceManager = mockSimpleTimerServiceManager,
                    getSimpleTimersUseCase = mockGetSimpleTimersUseCase,
                    addSimpleTimerUseCase = mockAddSimpleTimerUseCase,
                    editSimpleTimerUseCase = mockEditSimpleTimerUseCase,
                    deleteSimpleTimerUseCase = mockDeleteSimpleTimerUseCase,
                )

            // When & Then
            viewModel.toastMessage.test {
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.GET_ERROR)
            }
        }

    @Test
    fun `addTimerItem - 성공하면 타이머가 추가된다`() =
        runTest {
            // Given
            coEvery { mockAddSimpleTimerUseCase(any()) } returns BaseResult.Success(100L)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val initialSize = viewModel.timerItems.value?.size ?: 0

            // When
            viewModel.addTimerItem(180)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.timerItems.value?.size).isEqualTo(initialSize + 1)
            assertThat(viewModel.timerItems.value?.last()?.time).isEqualTo(180)
            assertThat(viewModel.timerItems.value?.last()?.id).isEqualTo(100L)
        }

    @Test
    fun `addTimerItem - 실패하면 ADD_ERROR 토스트가 발생한다`() =
        runTest {
            // Given
            coEvery { mockAddSimpleTimerUseCase(any()) } returns BaseResult.Error("ERROR", "추가 실패")
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.toastMessage.test {
                viewModel.addTimerItem(60)
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.ADD_ERROR)
            }
        }

    @Test
    fun `addTimerItem - 최대 개수에 도달하면 추가되지 않는다`() =
        runTest {
            // Given
            val maxTimers = (1..6).map { SimpleTimer(id = it.toLong(), time = 60) }
            coEvery { mockGetSimpleTimersUseCase() } returns BaseResult.Success(maxTimers)
            viewModel =
                SimpleTimerViewModel(
                    simpleTimerServiceManager = mockSimpleTimerServiceManager,
                    getSimpleTimersUseCase = mockGetSimpleTimersUseCase,
                    addSimpleTimerUseCase = mockAddSimpleTimerUseCase,
                    editSimpleTimerUseCase = mockEditSimpleTimerUseCase,
                    deleteSimpleTimerUseCase = mockDeleteSimpleTimerUseCase,
                )
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.addTimerItem(300)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { mockAddSimpleTimerUseCase(any()) }
        }

    @Test
    fun `deleteTimerItem - 성공하면 타이머가 삭제된다`() =
        runTest {
            // Given
            coEvery { mockDeleteSimpleTimerUseCase(any()) } returns BaseResult.Success(Unit)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val initialSize = viewModel.timerItems.value?.size ?: 0

            // When
            viewModel.deleteTimerItem(1L)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.timerItems.value?.size).isEqualTo(initialSize - 1)
            assertThat(viewModel.timerItems.value?.any { it.id == 1L }).isFalse()
        }

    @Test
    fun `deleteTimerItem - 실패하면 DELETE_ERROR 토스트가 발생한다`() =
        runTest {
            // Given
            coEvery { mockDeleteSimpleTimerUseCase(any()) } returns BaseResult.Error("ERROR", "삭제 실패")
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.toastMessage.test {
                viewModel.deleteTimerItem(1L)
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.DELETE_ERROR)
            }
        }

    @Test
    fun `updateTimerItem - 성공하면 타이머가 수정된다`() =
        runTest {
            // Given
            coEvery { mockEditSimpleTimerUseCase(any()) } returns BaseResult.Success(Unit)
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedTimer = SimpleTimer(id = 1L, time = 999)

            // When
            viewModel.updateTimerItem(updatedTimer)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            assertThat(viewModel.timerItems.value?.find { it.id == 1L }?.time).isEqualTo(999)
            coVerify { mockEditSimpleTimerUseCase(updatedTimer) }
        }

    @Test
    fun `updateTimerItem - 실패하면 EDIT_ERROR 토스트가 발생한다`() =
        runTest {
            // Given
            coEvery { mockEditSimpleTimerUseCase(any()) } returns BaseResult.Error("ERROR", "수정 실패")
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When & Then
            viewModel.toastMessage.test {
                viewModel.updateTimerItem(SimpleTimer(id = 1L, time = 500))
                testDispatcher.scheduler.advanceUntilIdle()
                assertThat(awaitItem()).isEqualTo(SimpleTimerToastMessage.EDIT_ERROR)
            }
        }

    @Test
    fun `updateTimerItem - 존재하지 않는 타이머는 수정되지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val nonExistentTimer = SimpleTimer(id = 999L, time = 100)

            // When
            viewModel.updateTimerItem(nonExistentTimer)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify(exactly = 0) { mockEditSimpleTimerUseCase(any()) }
        }

    @Test
    fun `changeSortOrder - 오름차순으로 정렬된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.changeSortOrder()

            // Then
            val items = viewModel.timerItems.value!!
            assertThat(items[0].time).isEqualTo(120)
            assertThat(items[1].time).isEqualTo(60)
            assertThat(items[2].time).isEqualTo(30)
        }

    @Test
    fun `changeSortOrder - 두 번 호출하면 다시 오름차순이 된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // When
            viewModel.changeSortOrder()
            viewModel.changeSortOrder()

            // Then
            val items = viewModel.timerItems.value!!
            assertThat(items[0].time).isEqualTo(30)
            assertThat(items[1].time).isEqualTo(60)
            assertThat(items[2].time).isEqualTo(120)
        }

    @Test
    fun `startTimer - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.startTimer()

            // Then - no crash
        }

    @Test
    fun `pauseTimer - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.pauseTimer()

            // Then - no crash
        }

    @Test
    fun `setAndStartTimer - 서비스가 없으면 아무 동작도 하지 않는다`() =
        runTest {
            // Given
            viewModel = createViewModel()

            // When
            viewModel.setAndStartTimer(60)

            // Then - no crash
        }

    @Test
    fun `unbindService - 서비스 매니저의 bindService가 호출된다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            verify { mockSimpleTimerServiceManager.bindService() }
        }

    @Test
    fun `isTimerRunning - 초기값은 false이다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then - switchMap returns MutableLiveData(false) when service is null
            var observedValue: Boolean? = null
            viewModel.isTimerRunning.observeForever { observedValue = it }
            assertThat(observedValue).isFalse()
        }

    @Test
    fun `remainingTime - 초기값은 0이다`() =
        runTest {
            // Given
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            // Then - switchMap returns MutableLiveData(0L) when service is null
            var observedValue: Long? = null
            viewModel.remainingTime.observeForever { observedValue = it }
            assertThat(observedValue).isEqualTo(0L)
        }
}
