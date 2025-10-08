package com.project200.feature.chatting

import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.FragmentChattingListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChattingListFragment: BindingFragment<FragmentChattingListBinding>(R.layout.fragment_chatting_list) {
    private val viewModel: ChattingListViewModel by viewModels()
    private lateinit var chattingListAdapter: ChattingListRVAdapter

    override fun getViewBinding(view: View): FragmentChattingListBinding {
        return FragmentChattingListBinding.bind(view)
    }

    override fun setupViews() {
        chattingListAdapter = ChattingListRVAdapter()
        binding.chattingRoomRv.adapter = chattingListAdapter
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chattingRooms.collect { chatRooms ->
                    // ListAdapter의 submitList를 통해 리스트를 업데이트
                    chattingListAdapter.submitList(chatRooms)
                }
            }
        }
    }
}