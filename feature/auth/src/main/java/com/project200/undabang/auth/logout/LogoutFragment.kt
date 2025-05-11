package com.project200.undabang.auth.logout

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.auth.login.LoginActivity
import com.project200.undabang.feature.auth.R
import com.project200.undabang.feature.auth.databinding.FragmentLogoutBinding
import com.project200.undabang.feature.auth.databinding.FragmentRegisterBinding
import com.project200.undabang.oauth.AuthManager
import com.project200.undabang.oauth.LogoutResultCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch // launch 임포트
import net.openid.appauth.AuthorizationService
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class LogoutFragment : BindingFragment<FragmentLogoutBinding>(R.layout.fragment_logout) { // 실제 레이아웃 파일 지정

     @Inject
     lateinit var authManager: AuthManager
     private lateinit var localAuthService: AuthorizationService

    override fun getViewBinding(view: View): FragmentLogoutBinding {
        return FragmentLogoutBinding.bind(view)
    }

     // IdP 로그아웃 페이지(브라우저/커스텀 탭)를 실행하기 위한 런처
     private val logoutPageLauncher =
         registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
             Timber.d("Logout page launcher finished, result code: ${result.resultCode}")
             navigateToLoginScreenAfterLogout()
         }

     private val logoutCallback = object : LogoutResultCallback {
         override fun onLogoutPageIntentReady(logoutIntent: Intent) {
             Timber.i("Logout intent from IdP is ready. Launching...")
             logoutPageLauncher.launch(logoutIntent)
         }

         override fun onLocalLogoutCompleted() {
             Timber.i("Local logout completed. Tokens and auth state cleared.")
             Toast.makeText(requireContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
             navigateToLoginScreenAfterLogout()
         }

         override fun onLogoutProcessError(exception: Exception) {
             Timber.e(exception, "Logout process error.")
             Toast.makeText(requireContext(), "로그아웃 중 오류 발생: ${exception.message}", Toast.LENGTH_LONG).show()
             navigateToLoginScreenAfterLogout()
         }
     }

     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         localAuthService = AuthorizationService(requireActivity())
     }

    override fun setupViews() {
        binding.logoutBtn.setOnClickListener {
            Timber.d("Logout button clicked.")
            lifecycleScope.launch {
                authManager.logout(localAuthService, logoutCallback)
            }
        }
    }

     private fun navigateToLoginScreenAfterLogout() {
         val intent = Intent(requireActivity(), LoginActivity::class.java)
         intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
         startActivity(intent)
         requireActivity().finish()
     }

     override fun onDestroy() {
         super.onDestroy()
         localAuthService.dispose()
     }
 }
