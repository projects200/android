package com.project200.undabang.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.project200.domain.usecase.UpdateCheckResult
import com.project200.presentation.navigator.AppNavigator
import com.project200.presentation.update.UpdateDialogFragment
import com.project200.undabang.R
import com.project200.undabang.auth.login.LoginActivity
import com.project200.undabang.databinding.ActivityMainBinding
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var appNavigator: AppNavigator
    private var isLoading = true // 스플래시 화면 유지를 위한 플래그
    private lateinit var binding: ActivityMainBinding
    private lateinit var authService: AuthorizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 스플래시 화면을 계속 보여줄 조건 설정
        splashScreen.setKeepOnScreenCondition { isLoading }

        authService = AuthorizationService(this)

        performRouting()
    }

    private fun performRouting() {
        lifecycleScope.launch {
            val currentAuthState = authStateManager.getCurrent()
            if (currentAuthState.isAuthorized) {
                if (currentAuthState.needsTokenRefresh) {
                    Timber.i("Token needs refresh. Attempting refresh...")
                    when (val refreshResult = authManager.refreshAccessToken(authService)) {
                        is TokenRefreshResult.Success -> {
                            Timber.i("Token refresh successful.")
                            proceedToThisActivityContent()
                        }
                        is TokenRefreshResult.Error,
                        is TokenRefreshResult.NoRefreshToken,
                        is TokenRefreshResult.ConfigError -> {
                            Timber.w("Token refresh failed or not possible. Navigating to Login.")
                            if (refreshResult is TokenRefreshResult.Error) Timber.e(refreshResult.exception)
                            navigateToLogin()
                        }
                    }
                } else {
                    Timber.i("User is authorized and token is fresh.")
                    proceedToThisActivityContent()
                }
            } else {
                Timber.i("User is not authorized.")
                navigateToLogin()
            }
        }
    }

    private fun proceedToThisActivityContent() {
        isLoading = false // 스플래시 종료
        // MainActivity의 실제 UI를 여기서 설정
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupObservers() // 옵저버 설정
        setupViews()     // 뷰 설정
        Timber.d("MainActivity content is now visible.")
    }

    private fun navigateToLogin() {
        isLoading = false // 스플래시 종료
        appNavigator.navigateToLogin(this)
    }


    private fun setupViews() { // 기존 함수 유지
        viewModel.checkForUpdate()
    }

    private fun setupObservers() { // 기존 함수 유지
        viewModel.updateCheckResult.observe(this) { result ->
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    showUpdateDialog(result.isForceUpdate)
                }
                is UpdateCheckResult.NoUpdateNeeded -> {
                    Timber.d("업데이트 불필요")
                }
                else -> {
                    // 필요한 경우 다른 상태 처리
                    Timber.d("UpdateCheckResult: Unhandled state or null")
                }
            }
        }
    }

    private fun showUpdateDialog(isForceUpdate: Boolean) { // 기존 함수 유지
        // 다이얼로그가 이미 떠있는지 확인하여 중복 호출 방지 (선택 사항)
        if (supportFragmentManager.findFragmentByTag(UpdateDialogFragment::class.java.simpleName) == null) {
            val dialog = UpdateDialogFragment(isForceUpdate)
            dialog.show(supportFragmentManager, UpdateDialogFragment::class.java.simpleName)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::authService.isInitialized) { // authService가 초기화 되었는지 확인
            authService.dispose() // AuthorizationService 리소스 해제
            Timber.d("AuthorizationService disposed in MainActivity.")
        }
    }
}