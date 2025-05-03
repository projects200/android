package com.project200.presentation

import androidx.activity.viewModels
import com.project200.common.base.BindingActivity
import com.project200.undabang.presentation.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BindingActivity<ActivityMainBinding, SampleViewModel>() {

    override val viewModel: SampleViewModel by viewModels()

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // 초기 뷰 설정
    }
}
