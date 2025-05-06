package com.project200.presentation.auth

import androidx.activity.viewModels
import com.project200.common.base.BindingActivity
import com.project200.undabang.presentation.databinding.ActivityLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity: BindingActivity<ActivityLoginBinding, AuthViewModel>() {

    override val viewModel: AuthViewModel by viewModels()

    override fun getViewBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
    }

    override fun setupObservers() {

    }
}