package com.project200.undabang.profile.setting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.domain.model.NotificationType
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.profile.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class NotificationFragment : Fragment() {
    private val viewModel: NotificationViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            viewModel.updateNotiStateByPermission(isGranted)
            if (!isGranted) {
                Toast.makeText(requireContext(), getString(R.string.noti_permission_announce), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val states by viewModel.notificationStates.collectAsStateWithLifecycle()
                val isExerciseOn = states.find { it.type == NotificationType.WORKOUT_REMINDER }?.enabled ?: false
                val isChattingOn = states.find { it.type == NotificationType.CHAT_MESSAGE }?.enabled ?: false

                NotificationScreen(
                    isExerciseOn = isExerciseOn,
                    isChattingOn = isChattingOn,
                    onNavigateBack = { findNavController().popBackStack() },
                    onExerciseToggle = { viewModel.onSwitchToggled(NotificationType.WORKOUT_REMINDER, it) },
                    onChattingToggle = { viewModel.onSwitchToggled(NotificationType.CHAT_MESSAGE, it) },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.permissionRequestTrigger.collect { needsRequest ->
                        if (needsRequest) {
                            requestNotificationPermission()
                            viewModel.onPermissionRequestHandled()
                        }
                    }
                }

                launch {
                    viewModel.toastMessage.collect { message ->
                        message?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        val isGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        viewModel.updateNotiStateByPermission(isGranted)
        if (!isGranted) {
            Toast.makeText(requireContext(), getString(R.string.noti_permission_announce), Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Timber.tag("NotificationFragment").d("Notification permission already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    openAppSettings()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
        startActivity(intent)
    }
}
