package com.project200.undabang.profile.setting

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.domain.model.BlockedMember
import com.project200.domain.usecase.GetBlockedMembersUseCase
import com.project200.domain.usecase.UnblockMemberUseCase
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
class BlockMembersViewModelTest {
    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockGetBlockedMembersUseCase: GetBlockedMembersUseCase

    @MockK
    private lateinit var mockUnblockMemberUseCase: UnblockMemberUseCase

    private lateinit var viewModel: BlockMembersViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sampleBlockedMembers = listOf(
        BlockedMember(memberBlockId = 1L, memberId = "member1", nickname = "차단유저1", profileImageUrl = "https://example.com/1.jpg", thumbnailImageUrl = "https://example.com/thumb1.jpg"),
        BlockedMember(memberBlockId = 2L, memberId = "member2", nickname = "차단유저2", profileImageUrl = "https://example.com/2.jpg", thumbnailImageUrl = "https://example.com/thumb2.jpg")
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
        viewModel = BlockMembersViewModel(
            getBlockedMembersUseCase = mockGetBlockedMembersUseCase,
            unblockMemberUseCase = mockUnblockMemberUseCase
        )
    }

    @Test
    fun `init - ViewModel 초기화 시 fetchBlockedMembers가 호출된다`() = runTest {
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Success(sampleBlockedMembers)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockGetBlockedMembersUseCase() }
    }

    @Test
    fun `fetchBlockedMembers - 성공 시 blockedMembers가 업데이트된다`() = runTest {
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Success(sampleBlockedMembers)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.blockedMembers.value).isEqualTo(sampleBlockedMembers)
    }

    @Test
    fun `fetchBlockedMembers - 실패 시 errorEvent가 emit된다`() = runTest {
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Error("ERROR", "로드 실패")

        createViewModel()

        viewModel.errorEvent.test {
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo("로드 실패")
        }
    }

    @Test
    fun `unblockMember - 성공 시 목록이 새로고침된다`() = runTest {
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Success(sampleBlockedMembers)
        coEvery { mockUnblockMemberUseCase(any()) } returns BaseResult.Success(Unit)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedList = listOf(sampleBlockedMembers[1])
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Success(updatedList)

        viewModel.unblockMember(sampleBlockedMembers[0])
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { mockGetBlockedMembersUseCase() }
        assertThat(viewModel.blockedMembers.value).isEqualTo(updatedList)
    }

    @Test
    fun `unblockMember - 실패 시 errorEvent가 emit된다`() = runTest {
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Success(sampleBlockedMembers)
        coEvery { mockUnblockMemberUseCase(any()) } returns BaseResult.Error("ERROR", "차단 해제 실패")

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.errorEvent.test {
            viewModel.unblockMember(sampleBlockedMembers[0])
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo("차단 해제 실패")
        }
    }

    @Test
    fun `fetchBlockedMembers - 수동 호출 시 목록이 다시 로드된다`() = runTest {
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Success(sampleBlockedMembers)

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val newList = listOf(
            BlockedMember(memberBlockId = 3L, memberId = "member3", nickname = "새유저", profileImageUrl = "https://example.com/3.jpg", thumbnailImageUrl = "https://example.com/thumb3.jpg")
        )
        coEvery { mockGetBlockedMembersUseCase() } returns BaseResult.Success(newList)

        viewModel.fetchBlockedMembers()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.blockedMembers.value).isEqualTo(newList)
        coVerify(exactly = 2) { mockGetBlockedMembersUseCase() }
    }
}
