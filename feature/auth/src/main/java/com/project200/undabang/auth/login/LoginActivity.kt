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
import com.project200.undabang.feature.auth.R
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
                } ?: Timber.tag(TAG).e(getString(R.string.login_response_empty))
            } else {
                val ex = result.data?.let { AuthorizationException.fromIntent(it) }
                if (ex?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
                    // 사용자가 뒤로가기를 눌러서 직접 취소한 경우
                    Timber.tag(TAG).i(getString(R.string.login_user_canceled))
                } else {
                    // 그 외 실제 오류 (네트워크, 서버, 인증 거부 등)
                    Timber.tag(TAG).e(ex, getString(R.string.login_failed_with_error, ex?.errorDescription))
                    Toast.makeText(this, getString(R.string.login_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val authCallback = object : AuthResultCallback {
        override fun onAuthFlowStarted(authIntent: Intent) {
            authorizationLauncher.launch(authIntent)
        }

        override fun onSuccess(tokenResponse: TokenResponse) {
            Timber.tag(TAG).i(getString(R.string.login_success_with_token, tokenResponse.accessToken))
            viewModel.checkIsRegistered()
        }

        override fun onError(exception: AuthorizationException?) {
            Timber.tag(TAG).e(exception, getString(R.string.login_error_with_description, exception?.errorDescription))
            when {
                // 이미 같은 이메일로 다른 소셜에 가입되어 있는 경우, 계정 통합 안내
                exception?.errorDescription?.contains("ACCOUNT_LINKED_SUCCESS") == true -> {
                    Timber.tag(TAG).e(exception, getString(R.string.login_error_with_description, exception?.errorDescription))
                    Toast.makeText(this@LoginActivity, getString(R.string.account_merged), Toast.LENGTH_LONG).show()
                }
                else -> {
                    // 그 외 일반적인 로그인 실패 오류 (네트워크, 서버 오류, 유효하지 않은 요청 등)
                    Toast.makeText(this@LoginActivity, getString(R.string.login_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onConfigurationError(exception: Exception) {
            Timber.tag(TAG).e(exception, getString(R.string.server_address_incorrect))
        }
    }

    override fun getViewBinding(): ActivityLoginBinding {
        return ActivityLoginBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)

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
                authManager.initiateAuthorization("Google", authCallback)
            }
        }
        binding.kakaoLoginBtn.setOnClickListener {
            lifecycleScope.launch {
                authManager.initiateAuthorization("kakao", authCallback)
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