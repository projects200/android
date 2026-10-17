package com.project200.feature.chatting

import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.domain.model.ChatSocketStatus
import com.project200.domain.usecase.ObserveChatConnectionUseCase
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.chatting.BuildConfig
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.FragmentChattingListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ChattingListFragment : BindingFragment<FragmentChattingListBinding>(R.layout.fragment_chatting_list) {
    private val viewModel: ChattingListViewModel by viewModels()
    private lateinit var chattingListAdapter: ChattingListRVAdapter

    @Inject
    lateinit var observeChatConnectionUseCase: ObserveChatConnectionUseCase

    override fun getViewBinding(view: View): FragmentChattingListBinding {
        return FragmentChattingListBinding.bind(view)
    }

    override fun setupViews() {
        chattingListAdapter =
            ChattingListRVAdapter { roomId, nickname, opponentMemberId ->
                findNavController().navigate(
                    ChattingListFragmentDirections.actionChattingFragmentToChattingRoomFragment(
                        roomId,
                        nickname,
                        opponentMemberId,
                    ),
                )
            }
        binding.chattingRoomRv.adapter = chattingListAdapter
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (BuildConfig.DEBUG) {
                    launch {
                        observeChatConnectionUseCase().collect { connection ->
                            binding.chattingRoomRv.contentDescription =
                                if (connection.status == ChatSocketStatus.Disconnected) {
                                    "chat-disconnected:${connection.roomId}"
                                } else {
                                    "chat-socket:${connection.roomId}:${connection.status}"
                                }
                        }
                    }
                }
                launch {
                    // Fragment가 STARTED 상태가 되면 폴링 시작
                    // STOPPED 상태가 되면 자동으로 코루틴 취소
                    while (isActive) {
                        viewModel.fetchChattingRooms()
                        delay(POLLING_PERIOD)
                    }
                }
                if (BuildConfig.DEBUG) {
                    launch {
                        viewModel.hasLoadedChattingRooms.collect {
                            updateEmptySelector()
                        }
                    }
                }
                viewModel.chattingRooms.collect { chatRooms ->
                    Timber.tag(TAG).d("Polling: $chatRooms")
                    if (BuildConfig.DEBUG) {
                        val owner = viewLifecycleOwner
                        chattingListAdapter.submitList(chatRooms) {
                            if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                                updateEmptySelector()
                            }
                        }
                    } else {
                        chattingListAdapter.submitList(chatRooms)
                    }
                    if (chatRooms.isEmpty()) {
                        binding.emptyTv.visibility = View.VISIBLE
                    } else {
                        binding.emptyTv.visibility = View.GONE
                    }
                    if (BuildConfig.DEBUG) {
                        updateEmptySelector()
                    }
                }
            }
        }
    }

    private fun updateEmptySelector() {
        // 최초 조회 전의 기본 빈 화면과 서버가 확인한 빈 목록을 접근성 레이블로 구분합니다.
        binding.emptyTv.contentDescription =
            if (
                viewModel.hasLoadedChattingRooms.value &&
                viewModel.chattingRooms.value.isEmpty() &&
                chattingListAdapter.currentList.isEmpty()
            ) {
                getString(R.string.chatting_empty_loaded)
            } else {
                null
            }
    }

    override fun onResume() {
        super.onResume()
        viewModel.fetchChattingRooms()
    }

    companion object {
        const val POLLING_PERIOD = 15000L
        const val TAG = "ChattingListFragment"
    }
}
