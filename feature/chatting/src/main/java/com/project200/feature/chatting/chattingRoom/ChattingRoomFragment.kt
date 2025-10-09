package com.project200.feature.chatting.chattingRoom

import android.view.View
import androidx.fragment.app.viewModels
import com.project200.feature.chatting.ChattingListViewModel
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.FragmentChattingRoomBinding

class ChattingRoomFragment: BindingFragment<FragmentChattingRoomBinding>(R.layout.fragment_chatting_room) {
    private val viewModel: ChattingListViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentChattingRoomBinding {
        return FragmentChattingRoomBinding.bind(view)
    }

    override fun setupViews() {

    }

    override fun setupObservers() {

    }
}
