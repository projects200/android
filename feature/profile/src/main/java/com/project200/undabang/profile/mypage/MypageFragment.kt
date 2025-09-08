package com.project200.undabang.profile.mypage

import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentMypageBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MypageFragment: BindingFragment<FragmentMypageBinding> (R.layout.fragment_mypage) {
    private val viewModel: MypageViewModel by viewModels()

    override fun getViewBinding(view: android.view.View): FragmentMypageBinding {
        return FragmentMypageBinding.bind(view)
    }

    override fun setupViews() {
        initClickListener()
        viewModel.getProfile()
    }

    private fun initClickListener() {
        binding.settingBtn.setOnClickListener {
            findNavController().navigate(
                MypageFragmentDirections.actionMypageFragmentToSettingFragment()
            )
        }
    }

    override fun setupObservers() {
        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            binding.apply {
                setupProfileImage(profile.profileThumbnailUrl, profile.profileImageUrl)

                nicknameTv.text = profile.nickname
                setGenderBirth(profile.gender, profile.birthDate)

                introductionTv.text =
                    if(profile.bio.isEmpty()) getString(R.string.empty_introduction) else profile.bio

                currentYearExerciseDaysTv.text = profile.yearlyExerciseDays.toString()
                recentExerciseCountsTv.text = profile.exerciseCountInLast30Days.toString()
                scoreTv.text = profile.exerciseScore.toString()
            }
        }
    }

    private fun setupProfileImage(thumbnailUrl: String?, imageUrl: String?) {
        val imageRes = thumbnailUrl ?: imageUrl

        Glide.with(binding.mypageProfileIv)
            .load(imageRes)
            .placeholder(R.drawable.ic_profile_default)
            .error(R.drawable.ic_profile_default)
            .into(binding.mypageProfileIv)
    }

    private fun setGenderBirth(gender: String, birthDate: String) {
        val genderStr = when (gender) {
            MALE -> getString(R.string.male)
            FEMALE -> getString(R.string.female)
            else -> getString(R.string.unknown_gender)
        }
        binding.genderBirthTv.text = getString(R.string.gender_birth_format, genderStr, birthDate)
    }

    companion object {
        const val MALE = "MALE"
        const val FEMALE = "FEMALE"
    }
}