package com.project200.undabang.auth

import android.view.MotionEvent
import com.project200.presentation.base.BindingActivity
import com.project200.undabang.auth.databinding.ActivityOnBoardingBinding
import com.project200.presentation.utils.hideKeyboardOnTouchOutside
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterActivity: BindingActivity<ActivityOnBoardingBinding>() {
    override fun getViewBinding(): ActivityOnBoardingBinding {
        return ActivityOnBoardingBinding.inflate(layoutInflater)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        hideKeyboardOnTouchOutside(ev)
        return super.dispatchTouchEvent(ev)
    }
}