package com.project200.undabang.profile.mypage

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.common.constants.RuleConstants
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.utils.ImageUtils.compressImage
import com.project200.presentation.utils.ImageValidator
import com.project200.undabang.feature.profile.R
import com.project200.undabang.profile.utils.ProfileEditErrorType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileEditFragment : Fragment() {
    private val viewModel: ProfileEditViewModel by viewModels()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { selectedUri ->
                val (isValid, reason) = ImageValidator.validateImageFile(selectedUri, requireContext())

                if (isValid) {
                    viewModel.updateProfileImageUri(selectedUri)
                } else {
                    when (reason) {
                        ImageValidator.OVERSIZE -> {
                            val compressedUri = compressImage(requireContext(), selectedUri)
                            if (compressedUri != null) {
                                viewModel.updateProfileImageUri(compressedUri)
                            } else {
                                viewModel.postImageError(ProfileEditErrorType.IMAGE_READ_FAILED)
                            }
                        }
                        ImageValidator.INVALID_TYPE -> {
                            viewModel.postImageError(ProfileEditErrorType.IMAGE_INVALID_TYPE)
                        }
                        ImageValidator.FAIL_TO_READ -> {
                            viewModel.postImageError(ProfileEditErrorType.IMAGE_READ_FAILED)
                        }
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val nickname by viewModel.nickname.collectAsStateWithLifecycle()
                val gender by viewModel.gender.collectAsStateWithLifecycle()
                val introduction by viewModel.introduction.collectAsStateWithLifecycle()
                val initProfile by viewModel.initProfile.collectAsStateWithLifecycle()
                val newProfileImageUri by viewModel.newProfileImageUri.collectAsStateWithLifecycle()
                val nicknameValidationState by viewModel.nicknameValidationState.collectAsStateWithLifecycle()
                val isNicknameChecked by viewModel.isNicknameChecked.collectAsStateWithLifecycle()

                ProfileEditScreen(
                    nickname = nickname,
                    gender = gender,
                    introduction = introduction,
                    initProfileImageUrl = initProfile?.profileImageUrl,
                    newProfileImageUri = newProfileImageUri,
                    nicknameValidationState = nicknameValidationState,
                    isNicknameChecked = isNicknameChecked,
                    onNicknameChange = viewModel::updateNickname,
                    onIntroductionChange = viewModel::updateIntroduction,
                    onGenderSelect = viewModel::selectGender,
                    onProfileImageClick = { pickImageLauncher.launch("image/*") },
                    onDuplicateCheckClick = viewModel::checkIsNicknameDuplicated,
                    onCompleteClick = viewModel::completeEditProfile,
                    onBackClick = { findNavController().navigateUp() },
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
                    viewModel.errorType.collect { type ->
                        val msg =
                            when (type) {
                                ProfileEditErrorType.LOAD_FAILED -> getString(R.string.error_failed_to_load)
                                ProfileEditErrorType.SAME_AS_ORIGINAL -> getString(R.string.same_nickname)
                                ProfileEditErrorType.CHECK_DUPLICATE_FAILED -> getString(R.string.error_unknown)
                                ProfileEditErrorType.NO_CHANGE -> getString(R.string.error_no_changed)
                                ProfileEditErrorType.NO_DUPLICATE_CHECKED -> getString(R.string.error_no_duplicate_checked)
                                ProfileEditErrorType.IMAGE_INVALID_TYPE ->
                                    getString(
                                        R.string.image_error_invalid_type,
                                        RuleConstants.ALLOWED_EXTENSIONS.joinToString(", "),
                                    )
                                ProfileEditErrorType.IMAGE_READ_FAILED -> getString(R.string.image_error_file_read)
                            }
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.editResult.collect { result ->
                        if (result) {
                            Toast.makeText(requireContext(), R.string.edit_profile_success, Toast.LENGTH_SHORT).show()
                            findNavController().previousBackStackEntry?.savedStateHandle?.set(MypageFragment.REFRESH_KEY, true)
                            findNavController().popBackStack()
                        } else {
                            Toast.makeText(requireContext(), R.string.error_edit_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val MALE = "MALE"
        const val FEMALE = "FEMALE"
        const val HIDDEN = "UNKNOWN"
    }
}
