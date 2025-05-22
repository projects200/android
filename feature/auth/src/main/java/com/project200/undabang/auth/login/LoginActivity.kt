package com.project200.undabang.auth.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.project200.presentation.base.BindingActivity
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.auth.register.RegisterActivity
import com.project200.undabang.feature.auth.databinding.ActivityLoginBinding
import com.project200.undabang.oauth.AuthResultCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : BindingActivity<ActivityLoginBinding>() {
    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var appNavigator: ActivityNavigator

    private lateinit var authService: AuthorizationService

    val viewModel: LoginViewModel by viewModels()

    private val authorizationLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent: Intent ->
                    authManager.handleAuthorizationResponse(authService, intent, authCallback)
                } ?: Timber.tag(TAG).e("Authorization response data is null")
            } else {
                result.data?.let {
                    val ex = AuthorizationException.fromIntent(it)
                    Timber.tag(TAG).e(ex, "Authorization Exception from launcher: ${ex?.errorDescription}")
                    Toast.makeText(this, "로그인 실패", Toast.LENGTH_SHORT).show()
                } ?: Timber.tag(TAG).w("Authorization flow canceled or failed without data.")
            }
        }

    private val authCallback = object : AuthResultCallback {
        override fun onAuthFlowStarted(authIntent: Intent) {
            authorizationLauncher.launch(authIntent)
        }

        override fun onSuccess(tokenResponse: TokenResponse) {
            Timber.tag(TAG).i("로그인 성공: ${tokenResponse.accessToken}")
            viewModel.checkIsRegistered()
        }

        override fun onError(exception: AuthorizationException?) {
            Timber.tag(TAG).e(exception, "로그인 실패: ${exception?.errorDescription}")
        }

        override fun onConfigurationError(exception: Exception) {
            Timber.tag(TAG).e(exception, "서버 주소가 틀렸거나, 통신이 불가능합니다.")
        }
    }

    override fun getViewBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)

        // TODO: 자동 로그인

        intent?.let {
            checkIntent(it)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntent(intent)
    }

    private fun checkIntent(intent: Intent) {
        if (intent.hasExtra(USED_INTENT_EXTRA_KEY)) {
            return
        }

        // onNewIntent로 들어오는 콜백 처리
        if (AuthorizationResponse.fromIntent(intent) != null ||
            AuthorizationException.fromIntent(intent) != null) {
            Timber.tag(TAG).d("Handling authorization response from onNewIntent/checkIntent")
            authManager.handleAuthorizationResponse(authService, intent, authCallback)
            intent.putExtra(USED_INTENT_EXTRA_KEY, true)
        } else {
            Timber.tag(TAG).w("Intent in checkIntent is not an auth callback: ${intent.data}")
        }
    }

    override fun setupViews() {
        binding.googleLoginBtn.setOnClickListener {
            lifecycleScope.launch {
                authManager.initiateAuthorization(authService, "Google", authCallback)
            }
        }
        binding.kakaoLoginBtn.setOnClickListener {
            lifecycleScope.launch {
                authManager.initiateAuthorization(authService, "kakao", authCallback)
            }
        }
    }

    override fun setupObservers() {
        viewModel.isRegistered.observe(this) { isRegistered ->
            if (isRegistered) appNavigator.navigateToMain(this)
            else startActivity(Intent(this@LoginActivity, RegisterActivity::class.java ))
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val USED_INTENT_EXTRA_KEY = "USED_INTENT_LOGIN_ACTIVITY"
    }
}