package com.project200.presentation.auth

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.project200.common.base.BindingActivity
import com.project200.domain.usecase.UpdateCheckResult
import com.project200.presentation.MainViewModel
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.ActivityLoginBinding
import com.project200.undabang.presentation.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class LoginActivity: BindingActivity<ActivityLoginBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels()

    override fun getViewBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        viewModel.checkForUpdate()
    }

    override fun setupObservers() {

    }
}