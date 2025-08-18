package com.project200.undabang.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.project200.domain.model.BaseResult
import com.project200.domain.model.UpdateCheckResult
import com.project200.feature.exercise.detail.ExerciseDetailFragmentDirections
import com.project200.feature.exercise.form.ExerciseFormFragmentDirections
import com.project200.feature.exercise.list.ExerciseListFragmentDirections
import com.project200.feature.exercise.main.ExerciseMainFragmentDirections
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.presentation.update.UpdateDialogFragment
import com.project200.presentation.utils.hideKeyboardOnTouchOutside
import com.project200.undabang.R
import com.project200.undabang.databinding.ActivityMainBinding
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var appNavigator: ActivityNavigator
    private lateinit var navController: NavController
    private var isLoading = true // 스플래시 화면 유지를 위한 플래그
    private lateinit var binding: ActivityMainBinding
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 스플래시 화면을 계속 보여줄 조건 설정
        splashScreen.setKeepOnScreenCondition { isLoading }

        requestNotificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    Timber.i("알림 권한 허용됨")
                } else {
                    Timber.w("알림 권한 거부됨")
                }
            }

        setupObservers()
        performRouting()
        observeAuthEvents()
    }

    private fun performRouting() {
        lifecycleScope.launch {
            val currentAuthState = authStateManager.getCurrent()
            if (currentAuthState.isAuthorized) {
                if (currentAuthState.needsTokenRefresh) {
                    Timber.i("Token needs refresh. Attempting refresh...")
                    when (val refreshResult = authManager.refreshAccessToken()) {
                        is TokenRefreshResult.Success -> {
                            Timber.i("Token refresh successful.")
                            viewModel.checkIsRegistered() // 회원 여부 확인
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
                    viewModel.checkIsRegistered() // 회원 여부 확인
                }
                viewModel.sendFcmToken()
            } else {
                Timber.i("User is not authorized.")
                navigateToLogin()
            }
        }
    }

    private fun proceedToContent() {
        isLoading = false // 스플래시 종료
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

        setupViews()
    }

    private fun navigateToLogin() {
        isLoading = false // 스플래시 종료
        appNavigator.navigateToLogin(this)
    }


    private fun setupViews() {
        viewModel.checkForUpdate()
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun setupObservers() {
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
        
        viewModel.isRegistered.observe(this) { isRegistered ->
            if (isRegistered) {
                proceedToContent()
                checkNotificationPermission()
            }
            else navigateToLogin()
        }

        viewModel.fcmTokenEvent.observe(this) { result ->
            when (result) {
                is BaseResult.Success -> {
                    Timber.d(getString(R.string.fcm_token_send_success))
                }
                is BaseResult.Error -> {
                    Timber.d(getString(R.string.fcm_token_not_found))
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            // 권한이 이미 허용되었는지 확인
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                Timber.i("알림 권한이 이미 허용되어 있음")
                return
            }
            // 권한 요청 실행
            requestNotificationPermissionLauncher.launch(permission)
        }
    }

    // AuthManager의 강제 로그아웃 이벤트를 구독하는 함수
    private fun observeAuthEvents() {
        lifecycleScope.launch {
            authManager.forceLogoutFlow.collectLatest {
                Timber.d("토큰 재발급 실패, 강제 로그아웃 이벤트 수신")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.error_token_refresh_failed, Toast.LENGTH_SHORT).show()
                    navigateToLogin()
                }
            }
        }
    }

    private fun showUpdateDialog(isForceUpdate: Boolean) {
        if (supportFragmentManager.findFragmentByTag(UpdateDialogFragment::class.java.simpleName) == null) {
            val dialog = UpdateDialogFragment(isForceUpdate)
            dialog.show(supportFragmentManager, UpdateDialogFragment::class.java.simpleName)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        hideKeyboardOnTouchOutside(ev)
        return super.dispatchTouchEvent(ev)
    }
}