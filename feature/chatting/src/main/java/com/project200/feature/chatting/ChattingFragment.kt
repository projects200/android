package com.project200.feature.chatting

import android.view.View
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.FragmentChattingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChattingFragment: BindingFragment<FragmentChattingBinding>(R.layout.fragment_chatting) {
    override fun getViewBinding(view: View): FragmentChattingBinding {
        return FragmentChattingBinding.bind(view)
    }

}