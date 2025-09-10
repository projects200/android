package com.project200.undabang.profile.mypage

import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentProfileEditBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileEditFragment: BindingFragment<FragmentProfileEditBinding>(R.layout.fragment_profile_edit) {
    private val viewModel: ProfileEditViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentProfileEditBinding {
        return FragmentProfileEditBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.profile_edit))
            showBackButton(true) { findNavController().navigateUp() }
        }
    }


}