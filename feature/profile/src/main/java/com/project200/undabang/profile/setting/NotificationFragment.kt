package com.project200.undabang.profile.setting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentNotificationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationFragment : BindingFragment<FragmentNotificationBinding>(R.layout.fragment_notification) {
    private val viewModel: NotificationViewModel by viewModels()

    // 알림 권한 요청을 위한 ActivityResultLauncher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 권한이 허용되면 스위치를 활성화
                viewModel.setNotificationState(true)
                Toast.makeText(requireContext(), getString(R.string.noti_active), Toast.LENGTH_SHORT).show()
            } else {
                // 권한이 거부되면 스위치를 비활성화
                viewModel.setNotificationState(false)
                Toast.makeText(requireContext(), getString(R.string.noti_deactive), Toast.LENGTH_SHORT).show()
            }
        }

    override fun getViewBinding(view: View): FragmentNotificationBinding {
        return FragmentNotificationBinding.bind(view)
    }

    override fun onResume() {
        super.onResume()
        // 화면에 다시 돌아왔을 때 현재 알림 권한 상태를 체크하여 UI를 업데이트
        checkNotificationPermission()
    }

    override fun setupViews() {
        binding.backBtnIv.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.notificationSwitch.setOnClickListener {
            // 스위치가 켜지는 경우 (알림 활성화)
            if (binding.notificationSwitch.isChecked) {
                requestNotificationPermission()
            } else { // 스위치가 꺼지는 경우 (알림 비활성화)
                openAppSettings()
            }
        }
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isNotiActive.collect { isActive ->
                        binding.notificationSwitch.isChecked = isActive
                    }
                }
                launch {
                    viewModel.isSwitchEnabled.collect { isEnabled ->
                        binding.notificationSwitch.isEnabled = isEnabled
                    }
                }
            }
        }
    }

    // 현재 알림 권한 상태를 확인하고 ViewModel에 반영
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            viewModel.setNotificationState(permissionStatus == PackageManager.PERMISSION_GRANTED)
        } else {
            // Android 13 미만에서는 기본적으로 알림이 활성화된 것으로 간주
            viewModel.setNotificationState(true)
        }
    }

    // 알림 권한 요청
    private fun requestNotificationPermission() {
        // Android 13 (Tiramisu) 이상에서만 런타임 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 이미 권한이 있는 경우
                    viewModel.setNotificationState(true)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // 권한이 명시적으로 거부된 경우, 사용자에게 설명 후 설정으로 유도
                    Toast.makeText(requireContext(), getString(R.string.noti_permission_announce), Toast.LENGTH_LONG).show()
                    viewModel.setNotificationState(false)
                    openAppSettings()
                }
                else -> {
                    // 처음 권한을 요청하는 경우
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 13 미만 버전에서는 별도의 런타임 권한이 필요 없음
            viewModel.setNotificationState(true)
        }
    }

    // 앱의 알림 설정 화면으로 이동
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
        startActivity(intent)
    }
}