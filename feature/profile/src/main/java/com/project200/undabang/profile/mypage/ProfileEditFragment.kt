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
import com.project200.presentation.base.BindingFragment
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
            // 사용자가 이미지를 선택하면 이 콜백이 실행됩니다.
            uri?.let {
                // 선택된 이미지 URI를 ViewModel에 전달
                viewModel.updateProfileImageUri(it)
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
            // TODO: 닉네임 중복체크 해제처리
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
            // TODO: 프로필 수정 완료
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
                            com.project200.undabang.presentation.R.color.error_led
                        )
                    )
                }

                NicknameValidationState.DUPLICATED -> {
                    binding.duplicateMessageTv.text = getString(R.string.error_nickname_duplicated)
                    binding.duplicateMessageTv.setTextColor(
                        getColor(
                            requireContext(),
                            com.project200.undabang.presentation.R.color.error_led
                        )
                    )
                }

                NicknameValidationState.AVAILABLE -> {
                    binding.duplicateMessageTv.text = getString(R.string.usable_nickname)
                    binding.duplicateMessageTv.setTextColor(
                        getColor(
                            requireContext(),
                            com.project200.undabang.presentation.R.color.main
                        )
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // UI가 STARTED 상태일 때만 수집하고, STOPPED 상태가 되면 중단
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorType.collect { type ->
                    val messageResId =
                        when (type) {
                            ProfileEditErrorType.LOAD_FAILED -> R.string.error_failed_to_load
                            ProfileEditErrorType.SAME_AS_ORIGINAL -> R.string.same_nickname
                            ProfileEditErrorType.CHECK_DUPLICATE_FAILED -> R.string.error_unknown
                            // 수정 완료 에러 추가
                        }
                    Toast.makeText(requireContext(), getString(messageResId), Toast.LENGTH_SHORT)
                        .show()
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
