package com.project200.presentation.auth

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.project200.common.base.BindingActivity
import com.project200.presentation.utils.hideKeyboardOnTouchOutside
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.ActivityLoginBinding
import com.project200.undabang.presentation.databinding.ActivityOnBoardingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnBoardingActivity: BindingActivity<ActivityOnBoardingBinding, AuthViewModel>() {

    override val viewModel: AuthViewModel by viewModels()

    override fun getViewBinding(): ActivityOnBoardingBinding {
        return ActivityOnBoardingBinding.inflate(layoutInflater)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        hideKeyboardOnTouchOutside(ev)
        return super.dispatchTouchEvent(ev)
    }
}