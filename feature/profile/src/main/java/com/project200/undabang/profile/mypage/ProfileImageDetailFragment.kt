package com.project200.undabang.profile.mypage

import android.view.View
import androidx.fragment.app.viewModels
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentProfileImageDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue

@AndroidEntryPoint
class ProfileImageDetailFragment: BindingFragment<FragmentProfileImageDetailBinding> (R.layout.fragment_profile_image_detail) {
    private val viewModel: ProfileImageDetailViewModel by viewModels()
    private lateinit var profileImageAdapter: ProfileImageAdapter

    override fun getViewBinding(view: View): FragmentProfileImageDetailBinding {
        return FragmentProfileImageDetailBinding.bind(view)
    }

    override fun setupViews() {
        profileImageAdapter = ProfileImageAdapter()
        binding.profileImageVp.adapter = profileImageAdapter

        initListeners()
    }

    private fun initListeners() {
        binding.profileImageVp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val currentPage = position + 1
                binding.currentPageTv.text = currentPage.toString()
            }
        })
    }
    }
}