package com.project200.undabang.profile.mypage

import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentProfileEditBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileEditFragment: BindingFragment<FragmentProfileEditBinding>(R.layout.fragment_profile_edit) {
    private val viewModel: ProfileEditViewModel by viewModels()

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
            //TODO: 닉네임 중복체크 해제처리
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
            //TODO: 프로필 수정 완료
        }

        binding.profileImgIv.setOnClickListener {
        }
    }

    override fun setupObservers() {
        viewModel.initProfile.observe(viewLifecycleOwner) { profile ->
            binding.nicknameEt.setText(profile.nickname)
            viewModel.selectGender(profile.gender)
            binding.introductionEt.setText(profile.bio)
            setupProfileImage(profile.profileImageUrl)
        }

        viewModel.gender.observe(viewLifecycleOwner) { gender ->
            binding.maleBtnImv.isSelected = gender == MALE
            binding.femaleBtnImv.isSelected = gender == FEMALE
            binding.hiddenBtnImv.isSelected = gender == HIDDEN
        }

    }

    private fun setupProfileImage(
        imageUrl: String?,
    ) {
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