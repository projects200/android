package com.project200.undabang.profile.setting

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.presentation.terms.TermsDialogFragment
import com.project200.presentation.terms.TermsDialogFragment.Companion.PRIVACY
import com.project200.presentation.terms.TermsDialogFragment.Companion.TERMS
import com.project200.undabang.feature.profile.R
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.LogoutResultCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettingFragment : Fragment() {
    @Inject lateinit var appNavigator: ActivityNavigator

    @Inject lateinit var authManager: AuthManager
    private lateinit var authService: AuthorizationService
    private val viewModel: SettingViewModel by viewModels()

    private val logoutPageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.d("로그아웃: ${result.resultCode}")
            appNavigator.navigateToLogin(requireContext())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val versionName =
            requireActivity().packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName
                .orEmpty()

        return ComposeView(requireContext()).apply {
            applyAppTheme {
                SettingScreen(
                    versionName = versionName,
                    onNavigateBack = { findNavController().popBackStack() },
                    onCustomerServiceClick = {
                        appNavigator.navigateToWeb(
                            requireContext(),
                            getString(R.string.customer_service_url),
                        )
                    },
                    onLogoutClick = {
                        BaseAlertDialog(getString(R.string.alert_logout), null) {
                            performLogout()
                        }.show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
                    },
                    onWithdrawClick = {
                        appNavigator.navigateToWeb(
                            requireContext(),
                            getString(R.string.withdraw_url),
                        )
                    },
                    onBlockMembersClick = {
                        findNavController().navigate(R.id.action_settingFragment_to_blockMembersFragment)
                    },
                    onTermsClick = { showTermsDialog(TERMS) },
                    onPrivacyClick = { showTermsDialog(PRIVACY) },
                    onNotificationClick = {
                        findNavController().navigate(R.id.action_settingFragment_to_notificationFragment)
                    },
                )
            }
        }
    }

    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.logout()
            } catch (e: Exception) {
                Timber.e(e, "로그아웃 실패")
            }

            authManager.logout(
                authService,
                object : LogoutResultCallback {
                    override fun onLogoutPageIntentReady(logoutIntent: Intent) {
                        logoutPageLauncher.launch(logoutIntent)
                    }

                    override fun onLocalLogoutCompleted() {
                        Timber.i("Local logout (AuthState cleared) completed.")
                    }

                    override fun onLogoutProcessError(exception: Exception) {
                        Timber.e(exception, "로그아웃 에러: ${exception.message}")
                        Toast.makeText(requireContext(), getString(R.string.logout_error), Toast.LENGTH_LONG).show()
                        appNavigator.navigateToLogin(requireContext())
                    }
                },
            )
        }
    }

    private fun showTermsDialog(termsType: String) {
        TermsDialogFragment(termsType).show(parentFragmentManager, TermsDialogFragment::class.java.simpleName)
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }
}
