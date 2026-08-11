package com.project200.undabang.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.project200.domain.model.UpdateCheckResult
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.presentation.navigator.BottomNavigationController
import com.project200.presentation.update.UpdateDialogFragment
import com.project200.presentation.utils.KeyboardControlInterface
import com.project200.presentation.utils.KeyboardUtils.hideKeyboardOnTouchOutside
import com.project200.undabang.R
import com.project200.undabang.databinding.ActivityMainBinding
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.AuthStateManager
import com.project200.undabang.oauth.TokenRefreshResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), BottomNavigationController {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var authManager: AuthManager

    @Inject lateinit var authStateManager: AuthStateManager

    @Inject lateinit var appNavigator: ActivityNavigator
    private lateinit var navController: NavController
    private var isLoading = true // 스플래시 화면 유지를 위한 플래그
    private lateinit var binding: ActivityMainBinding
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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

        checkNotificationPermission()

        setupObservers()
        viewModel.checkForUpdate()
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
                            viewModel.loginInBackground()
                            proceedToContent()
                        }
                        is TokenRefreshResult.Error -> {
                            val ex = refreshResult.exception
                            val isInvalidGrant =
                                ex?.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR &&
                                    ex.error == "invalid_grant"
                            if (isInvalidGrant) {
                                // forceLogoutFlow도 함께 발행되지만 명시적으로 처리
                                Timber.w("invalid_grant - 로그인 화면으로 이동")
                                navigateToLogin()
                            } else {
                                // 네트워크 오류 등 일시적 실패 - 오프라인으로 진입
                                Timber.w("Token refresh network error, proceeding offline.")
                                viewModel.loginInBackground()
                                proceedToContent()
                            }
                        }
                        is TokenRefreshResult.NoRefreshToken,
                        is TokenRefreshResult.ConfigError,
                        -> {
                            Timber.w("Token refresh not possible (${refreshResult::class.simpleName}). Navigating to Login.")
                            navigateToLogin()
                        }
                    }
                } else {
                    Timber.i("User is authorized and token is fresh.")
                    viewModel.loginInBackground()
                    proceedToContent()
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
        viewModel.onContentShown()
    }

    private fun navigateToLogin() {
        isLoading = false // 스플래시 종료
        appNavigator.navigateToLogin(this)
    }

    private fun setupViews() {
        binding.bottomNavigation.setupWithNavController(navController)

        val bottomNavHiddenFragments =
            setOf(
                com.project200.undabang.feature.exercise.R.id.exerciseDetailFragment,
                com.project200.undabang.feature.exercise.R.id.exerciseFormFragment,
                com.project200.undabang.feature.exercise.R.id.exerciseListFragment,
                com.project200.undabang.feature.timer.R.id.timerListFragment,
                com.project200.undabang.feature.timer.R.id.simpleTimerFragment,
                com.project200.undabang.feature.timer.R.id.customTimerFragment,
                com.project200.undabang.feature.timer.R.id.customTimerFormFragment,
                com.project200.undabang.feature.profile.R.id.settingFragment,
                com.project200.undabang.feature.profile.R.id.profileEditFragment,
                com.project200.undabang.feature.profile.R.id.profileImageDetailFragment,
                com.project200.undabang.feature.profile.R.id.urlFormFragment,
                com.project200.undabang.feature.profile.R.id.notificationFragment,
                com.project200.undabang.feature.profile.R.id.blockMembersFragment,
                com.project200.undabang.feature.profile.R.id.preferredExerciseFragment,
                com.project200.undabang.feature.matching.R.id.matchingProfileFragment,
                com.project200.undabang.feature.matching.R.id.exercisePlaceFragment,
                com.project200.undabang.feature.matching.R.id.exercisePlaceSearchFragment,
                com.project200.undabang.feature.matching.R.id.exercisePlaceRegisterFragment,
                com.project200.undabang.feature.exercise.R.id.exerciseShareEditFragment,
                com.project200.undabang.feature.matching.R.id.matchingGuideFragment,
                com.project200.undabang.feature.chatting.R.id.chattingRoomFragment,
                com.project200.undabang.feature.feed.R.id.feedFormFragment,
                com.project200.undabang.feature.feed.R.id.feedDetailFragment,
                // ... 필요한 다른 프래그먼트 ID들 추가 ... //
            )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in bottomNavHiddenFragments) {
                hideBottomNavigation()
            } else {
                showBottomNavigation()
            }
        }
    }

    override fun hideBottomNavigation() {
        binding.bottomNavigation.isVisible = false
    }

    override fun showBottomNavigation() {
        binding.bottomNavigation.isVisible = true
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.updateCheckResult.collectLatest { result ->
                result ?: return@collectLatest
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        showUpdateDialog(result.isForceUpdate)
                        if (result.isForceUpdate) {
                            isLoading = false
                        } else {
                            performRouting()
                        }
                    }
                    is UpdateCheckResult.NoUpdateNeeded -> {
                        Timber.d("업데이트 불필요")
                        performRouting()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.showBottomNavigation.collectLatest { show ->
                if (::binding.isInitialized) {
                    binding.bottomNavigation.isVisible = show
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        // Android 13 (Tiramisu, API 33) 이상에서만 동작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS

            // 권한이 이미 허용되었는지 확인
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                Timber.i("알림 권한이 이미 허용되어 있음")
                return
            }

            // 사용자가 이전에 권한 요청을 거부했는지 확인
            if (shouldShowRequestPermissionRationale(permission)) {
                Timber.i("사용자가 이전에 알림 권한을 거부했음. 앱 시작 시 자동 요청하지 않음.")
                return
            }

            // 권한이 없고, 이전에 거부한 적도 없는 경우 (== 최초 요청인 경우)
            Timber.i("최초로 알림 권한을 요청합니다.")
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
        lifecycleScope.launch {
            viewModel.forceUpdateAfterReconnect.collectLatest {
                Timber.d("재연결 후 강제 업데이트 필요 이벤트 수신")
                showUpdateDialog(isForceUpdate = true)
            }
        }
    }

    private fun showUpdateDialog(isForceUpdate: Boolean) {
        if (supportFragmentManager.findFragmentByTag(UpdateDialogFragment::class.java.simpleName) == null) {
            val dialog = UpdateDialogFragment.newInstance(isForceUpdate)
            dialog.show(supportFragmentManager, UpdateDialogFragment::class.java.simpleName)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.get(0)

        var shouldHide = true
        // 현재 프래그먼트가 KeyboardControlInterface를 구현했다면,
        if (currentFragment is KeyboardControlInterface && ev != null) {
            // 키보드를 숨길지 여부를 프래그먼트에게 위임
            shouldHide = currentFragment.shouldHideKeyboardOnTouch(ev)
        }

        // 프래그먼트가 숨겨야 한다고 결정한 경우에만 hideKeyboard 로직을 실행
        if (shouldHide) {
            hideKeyboardOnTouchOutside(ev)
        }

        return super.dispatchTouchEvent(ev)
    }
}
