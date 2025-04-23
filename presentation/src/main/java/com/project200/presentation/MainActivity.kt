package com.project200.presentation

import androidx.activity.viewModels
import com.project200.common.base.BaseActivity
import com.project200.myapp.presentation.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding, SampleViewModel>() {

    override val viewModel: SampleViewModel by viewModels()

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // 초기 뷰 설정
    }
}
