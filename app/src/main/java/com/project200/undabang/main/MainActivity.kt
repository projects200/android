package com.project200.undabang.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.project200.domain.model.UpdateCheckResult
import com.project200.feature.exercise.detail.ExerciseDetailFragmentDirections
import com.project200.feature.exercise.form.ExerciseFormFragmentDirections
import com.project200.feature.exercise.list.ExerciseListFragmentDirections
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.presentation.update.UpdateDialogFragment
import com.project200.undabang.R
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
class MainActivity : AppCompatActivity(), FragmentNavigator {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var authStateManager: AuthStateManager
    @Inject lateinit var appNavigator: ActivityNavigator
    private lateinit var navController: NavController
    private var isLoading = true // 스플래시 화면 유지를 위한 플래그
    private lateinit var binding: ActivityMainBinding
    private lateinit var authService: AuthorizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 스플래시 화면을 계속 보여줄 조건 설정
        splashScreen.setKeepOnScreenCondition { isLoading }
        authService = AuthorizationService(this)

        setupObservers()
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
            if (isRegistered) proceedToContent()
            else navigateToLogin()
        }
    }

    private fun showUpdateDialog(isForceUpdate: Boolean) {
        if (supportFragmentManager.findFragmentByTag(UpdateDialogFragment::class.java.simpleName) == null) {
            val dialog = UpdateDialogFragment(isForceUpdate)
            dialog.show(supportFragmentManager, UpdateDialogFragment::class.java.simpleName)
        }
    }

    override fun navigateFromExerciseListToExerciseDetail(recordId: Long) {
        navController.navigate(ExerciseListFragmentDirections.actionExerciseListFragmentToExerciseDetailFragment(recordId))
    }

    override fun navigateFromExerciseListToSetting() {
        navController.navigate(ExerciseListFragmentDirections.actionExerciseListFragmentToSettingFragment())
    }

    override fun navigateFromExerciseListToExerciseForm(recordId: Long) {
        navController.navigate(ExerciseListFragmentDirections.actionExerciseListFragmentToExerciseFormFragment(recordId))
    }

    override fun navigateFromExerciseDetailToExerciseForm(recordId: Long) {
        navController.navigate(ExerciseDetailFragmentDirections.actionExerciseDetailFragmentToExerciseFormFragment(recordId))
    }

    override fun navigateFromExerciseFormToExerciseDetail(recordId: Long) {
        navController.navigate(ExerciseFormFragmentDirections.actionExerciseFormFragmentToExerciseDetailFragment(recordId))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::authService.isInitialized) {
            authService.dispose()
        }
    }
}