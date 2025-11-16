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
import com.project200.domain.model.NotificationType
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentNotificationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class NotificationFragment : BindingFragment<FragmentNotificationBinding>(R.layout.fragment_notification) {
    private val viewModel: NotificationViewModel by viewModels()

    // 알림 권한 요청을 위한 ActivityResultLauncher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            viewModel.updateNotiStateByPermission(isGranted)
            if (!isGranted) {
                Toast.makeText(requireContext(), getString(R.string.noti_permission_announce), Toast.LENGTH_LONG).show()
                binding.exerciseNotiSwitch.isChecked = false
                binding.chattingNotiSwitch.isChecked = false
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
        binding.exerciseNotiSwitch.setOnClickListener {
            viewModel.onSwitchToggled(NotificationType.WORKOUT_REMINDER, binding.exerciseNotiSwitch.isChecked)
        }

        binding.chattingNotiSwitch.setOnClickListener {
            viewModel.onSwitchToggled(NotificationType.CHAT_MESSAGE, binding.chattingNotiSwitch.isChecked)
        }
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notificationStates.collect { states ->
                        // 운동 알림 스위치 상태 설정
                        val isExerciseOn = states.find { it.type == NotificationType.WORKOUT_REMINDER }?.enabled ?: false
                        if (binding.exerciseNotiSwitch.isChecked != isExerciseOn) {
                            binding.exerciseNotiSwitch.isChecked = isExerciseOn
                        }

                        // 채팅 알림 스위치 상태 설정
                        val isChatOn = states.find { it.type == NotificationType.CHAT_MESSAGE }?.enabled ?: false
                        if (binding.chattingNotiSwitch.isChecked != isChatOn) {
                            binding.chattingNotiSwitch.isChecked = isChatOn
                        }
                    }
                }

                launch {
                    viewModel.permissionRequestTrigger.collect { needsRequest ->
                        if (needsRequest) {
                            requestNotificationPermission()
                            viewModel.onPermissionRequestHandled()
                        }
                    }
                }
            }
        }
    }

    // 현재 알림 권한 상태를 확인
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
        // 확인된 권한 상태를 ViewModel에 전달하여 초기 로직을 수행하도록 합니다.
        viewModel.updateNotiStateByPermission(isGranted)
        if (!isGranted) {
            Toast.makeText(requireContext(), getString(R.string.noti_permission_announce), Toast.LENGTH_LONG).show()
            binding.exerciseNotiSwitch.isChecked = false
            binding.chattingNotiSwitch.isChecked = false
        }
    }

    // 알림 권한 요청
    private fun requestNotificationPermission() {
        // Android 13 (Tiramisu) 이상에서만 런타임 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 이미 권한이 있는 경우
                    Timber.tag("NotificationFragment").d("Notification permission already granted.")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    openAppSettings()
                }
                else -> {
                    // 처음 권한을 요청하는 경우
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 13 미만 버전에서는 별도의 런타임 권한이 필요 없음
        }
    }

    // 앱의 알림 설정 화면으로 이동
    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
        startActivity(intent)
    }
}
