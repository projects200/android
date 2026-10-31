package com.project200.undabang.profile.setting

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.feedback.SessionConfirmationDialogFragment
import com.project200.presentation.navigator.ActivityNavigator
import com.project200.presentation.terms.TermsDialogFragment
import com.project200.presentation.terms.TermsDialogFragment.Companion.PRIVACY
import com.project200.presentation.terms.TermsDialogFragment.Companion.TERMS
import com.project200.undabang.feature.profile.R
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.LogoutResultCallback
import dagger.hilt.android.AndroidEntryPoint
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
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(requireContext())
        parentFragmentManager.setFragmentResultListener(SessionConfirmationDialogFragment.RESULT_KEY, this) { _, result ->
            if (viewModel.logoutBlocking.value) return@setFragmentResultListener
            if (!result.getBoolean(SessionConfirmationDialogFragment.CONFIRMED)) return@setFragmentResultListener
            when (result.getString(SessionConfirmationDialogFragment.ACTION)) {
                LOGOUT -> performLogout()
                WITHDRAW -> appNavigator.navigateToWeb(requireContext(), getString(R.string.withdraw_url))
            }
        }
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
                val logoutBlocking by viewModel.logoutBlocking.collectAsStateWithLifecycle()
                SettingScreen(
                    versionName = versionName,
                    onNavigateBack = { if (!viewModel.logoutBlocking.value) findNavController().popBackStack() },
                    onCustomerServiceClick = {
                        appNavigator.navigateToWeb(
                            requireContext(),
                            getString(R.string.customer_service_url),
                        )
                    },
                    onLogoutClick = {
                        showSessionConfirmation(
                            LOGOUT, getString(R.string.alert_logout), getString(R.string.logout_loss_warning), getString(R.string.logout),
                        )
                    },
                    onWithdrawClick = {
                        showSessionConfirmation(
                            WITHDRAW, getString(R.string.withdraw_confirmation_title),
                            getString(R.string.withdraw_loss_warning), getString(R.string.withdraw_open_form),
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
                if (logoutBlocking) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                    ) {
                        Surface(shape = MaterialTheme.shapes.large) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.logout_in_progress))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.logoutCompleted.observe(viewLifecycleOwner) { failed ->
            if (failed != null) {
                if (failed) Toast.makeText(requireContext(), getString(R.string.logout_error), Toast.LENGTH_LONG).show()
                appNavigator.navigateToLogin(requireContext())
                viewModel.consumeLogoutCompletion()
            }
        }
    }

    private fun showSessionConfirmation(action: String, title: String, message: String, confirmText: String) {
        if (viewModel.logoutBlocking.value) return
        if (parentFragmentManager.isStateSaved) return
        if (parentFragmentManager.findFragmentByTag(SESSION_DIALOG) != null) return
        SessionConfirmationDialogFragment.newInstance(action, title, message, confirmText)
            .showNow(parentFragmentManager, SESSION_DIALOG)
    }

    private fun performLogout() {
        viewModel.confirmLogout {
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
                    }
                },
            )
        }
    }

    private fun showTermsDialog(termsType: String) {
        TermsDialogFragment.newInstance(termsType).show(parentFragmentManager, TermsDialogFragment::class.java.simpleName)
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }

    companion object {
        private const val SESSION_DIALOG = "settings_session_confirmation"
        private const val LOGOUT = "logout"
        private const val WITHDRAW = "withdraw"
    }
}
