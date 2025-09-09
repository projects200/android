package com.project200.undabang.auth.register

import android.view.MotionEvent
import com.project200.presentation.base.BindingActivity
import com.project200.presentation.utils.hideKeyboardOnTouchOutside
import com.project200.undabang.feature.auth.databinding.ActivityRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterActivity : BindingActivity<ActivityRegisterBinding>() {
    override fun getViewBinding(): ActivityRegisterBinding {
        return ActivityRegisterBinding.inflate(layoutInflater)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        hideKeyboardOnTouchOutside(ev)
        return super.dispatchTouchEvent(ev)
    }
}
