package com.project200.presentation.auth

import android.view.View
import androidx.navigation.fragment.findNavController
import com.project200.common.base.BindingFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.FragmentPermissionBinding

class PermissionFragment: BindingFragment<FragmentPermissionBinding>(R.layout.fragment_permission) {


    override fun getViewBinding(view: View): FragmentPermissionBinding {
        return FragmentPermissionBinding.bind(view)
    }

    override fun setupViews() {
        binding.permissionNextBtn.setOnClickListener {
            if (isAdded && findNavController().currentDestination?.id == R.id.permissionFragment) {
                findNavController().navigate(R.id.action_permissionFragment_to_termsFragment)
            }
        }
    }
}