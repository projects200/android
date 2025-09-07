package com.project200.undabang.profile.mypage

import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentMypageBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MypageFragment: BindingFragment<FragmentMypageBinding> (R.layout.fragment_mypage) {
    private val viewModel: MypageViewModel by viewModels()

    override fun getViewBinding(view: android.view.View): FragmentMypageBinding {
        return FragmentMypageBinding.bind(view)
    }

    override fun setupViews() {
        initClickListener()
    }

    private fun initClickListener() {
        binding.settingBtn.setOnClickListener {
            findNavController().navigate(
                MypageFragmentDirections.actionMypageFragmentToSettingFragment()
            )
        }
    }
}