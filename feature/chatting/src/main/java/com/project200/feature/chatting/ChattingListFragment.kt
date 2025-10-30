package com.project200.feature.chatting

import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.FragmentChattingListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ChattingListFragment : BindingFragment<FragmentChattingListBinding>(R.layout.fragment_chatting_list) {
    private val viewModel: ChattingListViewModel by viewModels()
    private lateinit var chattingListAdapter: ChattingListRVAdapter

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
                        opponentMemberId
                    )
                )
            }
        binding.chattingRoomRv.adapter = chattingListAdapter
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Fragment가 STARTED 상태가 되면 폴링 시작
                    // STOPPED 상태가 되면 자동으로 코루틴 취소
                    while (isActive) {
                        viewModel.fetchChattingRooms()
                        delay(POLLING_PERIOD)
                    }
                }
                viewModel.chattingRooms.collect { chatRooms ->
                    Timber.tag(TAG).d("Polling: $chatRooms")
                    chattingListAdapter.submitList(chatRooms)
                    if (chatRooms.isEmpty()) {
                        binding.emptyTv.visibility = View.VISIBLE
                    } else {
                        binding.emptyTv.visibility = View.GONE
                    }
                }
            }
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
