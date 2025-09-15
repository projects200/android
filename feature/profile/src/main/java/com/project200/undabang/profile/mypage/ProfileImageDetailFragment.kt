package com.project200.undabang.profile.mypage

import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentProfileImageDetailBinding
import com.project200.undabang.profile.utils.ProfileImageErrorType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    override fun setupObservers() {
        viewModel.profileImages.observe(viewLifecycleOwner) { images ->
            profileImageAdapter.submitList(images)
            binding.imageMaxTv.text = images.size.toString()

            // ViewPager2의 현재 아이템이 0이 아니면서 리스트가 갱신될 경우를 대비 (예: 이미지 삭제 후)
            val currentItem = binding.profileImageVp.currentItem
            binding.currentPageTv.text = getString(R.string.number_format, currentItem + 1)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorType.collect { errorType ->
                    val message = when(errorType) {
                        ProfileImageErrorType.LOAD_FAILED -> R.string.error_faild_to_load_profile_image
                        ProfileImageErrorType.DELETE_FAILED -> R.string.error_faild_to_load_profile_image
                        ProfileImageErrorType.THUMBNAIL_CHANGE_FAILED -> R.string.error_faild_to_change_thumbnail
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}