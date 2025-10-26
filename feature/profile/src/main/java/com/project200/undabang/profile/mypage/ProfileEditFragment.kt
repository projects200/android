package com.project200.undabang.profile.mypage

import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.getColor
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.project200.common.constants.RuleConstants
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.ImageUtils.compressImage
import com.project200.presentation.utils.ImageValidator
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentProfileEditBinding
import com.project200.undabang.profile.utils.NicknameValidationState
import com.project200.undabang.profile.utils.ProfileEditErrorType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileEditFragment :
    BindingFragment<FragmentProfileEditBinding>(R.layout.fragment_profile_edit) {
    private val viewModel: ProfileEditViewModel by viewModels()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { selectedUri ->
                // 이미지 유효성 검사 시작
                val (isValid, reason) = ImageValidator.validateImageFile(selectedUri, requireContext())

                if (isValid) {
                    // 유효성 검사 통과 시 ViewModel에 URI 전달
                    viewModel.updateProfileImageUri(selectedUri)
                } else {
                    when (reason) {
                        // 용량이 클 경우 압축 시도
                        ImageValidator.OVERSIZE -> {
                            val compressedUri = compressImage(requireContext(), selectedUri)
                            if (compressedUri != null) {
                                viewModel.updateProfileImageUri(compressedUri)
                            } else {
                                // 압축 실패 시 에러 메시지 표시
                                viewModel.postImageError(ProfileEditErrorType.IMAGE_READ_FAILED)
                            }
                        }
                        // 잘못된 파일 타입일 경우
                        ImageValidator.INVALID_TYPE -> {
                            viewModel.postImageError(ProfileEditErrorType.IMAGE_INVALID_TYPE)
                        }
                        // 파일을 읽을 수 없을 경우
                        ImageValidator.FAIL_TO_READ -> {
                            viewModel.postImageError(ProfileEditErrorType.IMAGE_READ_FAILED)
                        }
                    }
                }
            }
        }

    override fun getViewBinding(view: View): FragmentProfileEditBinding {
        return FragmentProfileEditBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.profile_edit))
            showBackButton(true) { findNavController().navigateUp() }
        }
        initListeners()
    }

    private fun initListeners() {
        binding.nicknameEt.doAfterTextChanged {
            viewModel.updateNickname(it.toString())
        }

        binding.duplicateCheckBtn.setOnClickListener {
            viewModel.checkIsNicknameDuplicated()
        }

        binding.introductionEt.doAfterTextChanged {
            viewModel.updateIntroduction(it.toString())
        }

        binding.genderMaleLl.setOnClickListener {
            viewModel.selectGender(MALE)
        }

        binding.genderFemaleLl.setOnClickListener {
            viewModel.selectGender(FEMALE)
        }

        binding.genderHiddenLl.setOnClickListener {
            viewModel.selectGender(HIDDEN)
        }

        binding.profileEditCompleteBtn.setOnClickListener {
            viewModel.completeEditProfile()
        }

        binding.profileImgIv.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    override fun setupObservers() {
        viewModel.initProfile.observe(viewLifecycleOwner) { profile ->
            binding.nicknameEt.setText(profile.nickname)
            viewModel.selectGender(profile.gender)
            binding.introductionEt.setText(profile.bio)
            setupProfileImage(profile.profileImageUrl)
        }

        viewModel.isNicknameChecked.observe(viewLifecycleOwner) { isChecked ->
            binding.duplicateCheckBtn.isEnabled = !isChecked
        }

        viewModel.gender.observe(viewLifecycleOwner) { gender ->
            binding.maleBtnImv.isSelected = gender == MALE
            binding.femaleBtnImv.isSelected = gender == FEMALE
            binding.hiddenBtnImv.isSelected = gender == HIDDEN
        }

        viewModel.newProfileImageUri.observe(viewLifecycleOwner) { uri ->
            uri?.let {
                setupProfileImage(it.toString())
            }
        }

        viewModel.nicknameValidationState.observe(viewLifecycleOwner) { state ->
            binding.duplicateMessageTv.visibility =
                if (state == NicknameValidationState.INVISIBLE) View.GONE else View.VISIBLE

            when (state) {
                NicknameValidationState.INVISIBLE -> {}
                NicknameValidationState.INVALID -> {
                    binding.duplicateMessageTv.text = getString(R.string.error_nickname_invalid)
                    binding.duplicateMessageTv.setTextColor(
                        getColor(
                            requireContext(),
                            com.project200.undabang.presentation.R.color.error_red,
                        ),
                    )
                }

                NicknameValidationState.DUPLICATED -> {
                    binding.duplicateMessageTv.text = getString(R.string.error_nickname_duplicated)
                    binding.duplicateMessageTv.setTextColor(
                        getColor(
                            requireContext(),
                            com.project200.undabang.presentation.R.color.error_red,
                        ),
                    )
                }

                NicknameValidationState.AVAILABLE -> {
                    binding.duplicateMessageTv.text = getString(R.string.usable_nickname)
                    binding.duplicateMessageTv.setTextColor(
                        getColor(
                            requireContext(),
                            com.project200.undabang.presentation.R.color.main,
                        ),
                    )
                }
                null -> return@observe
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // UI가 STARTED 상태일 때만 수집하고, STOPPED 상태가 되면 중단
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.errorType.collect { type ->
                        val str =
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
                        Toast.makeText(requireContext(), str, Toast.LENGTH_SHORT)
                            .show()
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

    private fun setupProfileImage(imageUrl: String?) {
        Glide.with(binding.profileImgIv)
            .load(imageUrl)
            .placeholder(R.drawable.ic_profile_default)
            .error(R.drawable.ic_profile_default)
            .into(binding.profileImgIv)
    }

    companion object {
        const val MALE = "MALE"
        const val FEMALE = "FEMALE"
        const val HIDDEN = "UNKNOWN"
    }
}
