package com.project200.undabang.main

import androidx.activity.viewModels
import com.project200.presentation.base.BindingActivity
import com.project200.domain.usecase.UpdateCheckResult
import com.project200.undabang.databinding.ActivityMainBinding
import com.project200.presentation.update.UpdateDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : BindingActivity<ActivityMainBinding>() {
    val viewModel: MainViewModel by viewModels()

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        viewModel.checkForUpdate()
    }

    override fun setupObservers() {
        viewModel.updateCheckResult.observe(this) { result ->
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    showUpdateDialog(result.isForceUpdate)
                }
                is UpdateCheckResult.NoUpdateNeeded -> {
                    Timber.d("업데이트 불필요")
                }
            }
        }
    }

    // 업데이트 다이얼로그 표시 함수
    private fun showUpdateDialog(isForceUpdate: Boolean) {
        val dialog = UpdateDialogFragment(isForceUpdate)
        dialog.show(supportFragmentManager, UpdateDialogFragment::class.java.simpleName)
    }
}
