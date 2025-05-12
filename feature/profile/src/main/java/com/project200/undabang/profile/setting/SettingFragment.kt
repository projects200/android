package com.project200.undabang.profile.setting

import android.view.View
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentSettingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingFragment : BindingFragment<FragmentSettingBinding>(R.layout.fragment_setting) {
    override fun getViewBinding(view: View): FragmentSettingBinding {
        return FragmentSettingBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        versionInfoTv.text = requireActivity().packageManager.getPackageInfo(requireContext().packageName, 0).versionName

        backBtnIv.setOnClickListener { findNavController().popBackStack() }
        logoutLl.setOnClickListener {  }
        withdrawLl.setOnClickListener {  }
        termsLl.setOnClickListener {  }
        privacyLl.setOnClickListener {  }
        versionInfoLl.setOnClickListener {  }
    }
}