package com.project200.undabang.e2e

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MaestroSetupActivity : ComponentActivity() {
    @Inject
    internal lateinit var coordinator: MaestroSetupCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Intent나 화면을 자격 증명 통로로 사용하지 않고 앱 전용 파일만 소비합니다
        lifecycleScope.launch {
            coordinator.consumeRequest()
            finish()
        }
    }
}
