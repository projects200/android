package com.project200.undabang.auth.register

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment // BaseFragment 경로 확인 및 수정 필요
import com.project200.undabang.feature.auth.R
import com.project200.undabang.feature.auth.databinding.FragmentPermissionBinding
import timber.log.Timber

class PermissionFragment : BindingFragment<FragmentPermissionBinding>(R.layout.fragment_permission) {


    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    private val requiredPermissions = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        requestMultiplePermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach { (permission, isGranted) ->
                    when (permission) {
                        Manifest.permission.ACCESS_FINE_LOCATION -> {
                            if (isGranted) {
                                Timber.i("위치 권한 허용됨")
                            } else {
                                Timber.w("위치 권한 거부됨")
                            }
                        }
                        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE -> {
                            if (isGranted) {
                                Timber.i("갤러리/저장소 읽기 권한 허용됨 ($permission)")
                            } else {
                                Timber.w("갤러리/저장소 읽기 권한 거부됨 ($permission)")
                            }
                        }
                    }
                }
            }

        // 알림 권한 요청 결과 처리 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        Timber.i("알림 권한 허용됨")
                    } else {
                        Timber.w("알림 권한 거부됨")
                    }
                }
        }
    }

    override fun getViewBinding(view: View): FragmentPermissionBinding {
        return FragmentPermissionBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 화면이 생성된 후 필요한 권한 요청
        requestNeededPermissions()
    }

    override fun setupViews() {
        binding.permissionNextBtn.setOnClickListener {
            if (isAdded && findNavController().currentDestination?.id == R.id.permissionFragment) {
                findNavController().navigate(R.id.action_permissionFragment_to_termsFragment)
            }
        }
    }

    private fun requestNeededPermissions() {
        requiredPermissions.clear()

        // 위치 권한
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // 갤러리/저장소 권한
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            Manifest.permission.READ_MEDIA_IMAGES
        } else { // Android 12 이하
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (!isPermissionGranted(storagePermission)) {
            requiredPermissions.add(storagePermission)
        }

        // 위치 및 저장소 권한 요청
        Timber.d("다음 권한들 요청: ${requiredPermissions.joinToString()}")
        requestMultiplePermissionsLauncher.launch(requiredPermissions.toTypedArray())

        // 알림 권한 (Android 13 이상에서만 요청)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Timber.d("알림 권한 요청")
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}