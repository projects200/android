package com.project200.undabang.profile.setting

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.AppNavigator
import com.project200.presentation.base.BaseAlertDialog
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentSettingBinding
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.LogoutResultCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettingFragment : BindingFragment<FragmentSettingBinding>(R.layout.fragment_setting) {
    override fun getViewBinding(view: View): FragmentSettingBinding {
        return FragmentSettingBinding.bind(view)
    }

    @Inject lateinit var appNavigator: AppNavigator
    @Inject lateinit var authManager: AuthManager
    private lateinit var authService: AuthorizationService

    // Cognito 로그아웃 페이지를 열기 위한 ActivityResultLauncher
    private val logoutPageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.d("로그아웃: ${result.resultCode}")
            appNavigator.navigateToLogin(requireContext())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(requireContext())
    }
    override fun setupViews() = with(binding) {
        versionInfoTv.text = requireActivity().packageManager.getPackageInfo(requireContext().packageName, 0).versionName

        backBtnIv.setOnClickListener { findNavController().popBackStack() }
        logoutLl.setOnClickListener {
            BaseAlertDialog(getString(R.string.alert_logout), null) {
                performLogout()
            }.show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
        }
        withdrawLl.setOnClickListener {  }
        termsLl.setOnClickListener {  }
        privacyLl.setOnClickListener {  }
        versionInfoLl.setOnClickListener {  }
    }

    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch { // Fragment의 viewLifecycleOwner 사용
            authManager.logout(authService, object : LogoutResultCallback {
                override fun onLogoutPageIntentReady(logoutIntent: Intent) {
                    logoutPageLauncher.launch(logoutIntent)
                }

                override fun onLocalLogoutCompleted() {
                    Timber.i("Local logout (AuthState cleared) completed.")
                }

                override fun onLogoutProcessError(exception: Exception) {
                    Timber.e(exception, "로그아웃 에러: ${exception.message}")
                    Toast.makeText(requireContext(), getString(R.string.logout_error), Toast.LENGTH_LONG).show()
                    // 오류 발생 시에도 로컬 상태는 AuthManager에서 정리 시도됨. 로그인 화면으로 이동.
                    appNavigator.navigateToLogin(requireContext())
                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }
}