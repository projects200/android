package com.project200.undabang.profile.mypage

import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.project200.domain.model.BaseResult
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentProfileImageDetailBinding
import com.project200.undabang.profile.utils.ProfileImageErrorType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
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
        binding.backBtn.setOnClickListener {
            findNavController().previousBackStackEntry?.savedStateHandle?.set(MypageFragment.REFRESH_KEY, true)
            findNavController().popBackStack()
        }

        binding.menuBtn.setOnClickListener {
            showPopupMenu()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(MypageFragment.REFRESH_KEY, true)
                    findNavController().popBackStack()
                }
            },
        )
    }

    override fun setupObservers() {
        viewModel.profileImages.observe(viewLifecycleOwner) { images ->
            profileImageAdapter.submitList(images)
            binding.imageMaxTv.text = images.size.toString()

            // ViewPager2의 현재 아이템이 0이 아니면서 리스트가 갱신될 경우를 대비 (예: 이미지 삭제 후)
            val currentItem = binding.profileImageVp.currentItem
            binding.currentPageTv.text = getString(R.string.number_format, currentItem + 1)

            binding.menuBtn.visibility = if(images[0].id == EMPTY_ID) View.GONE else View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.getProfileImageErrorToast.collect { errorType ->
                        Toast.makeText(requireContext(), R.string.error_faild_to_load_profile_image, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.imageSaveResult.collect { result ->
                        val message = when (result) {
                            true -> getString(R.string.image_save_success)
                            false -> getString(R.string.image_save_failed)
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.imageDeleteResult.collect { result ->
                        val message = when (result) {
                            is BaseResult.Success -> getString(R.string.image_delete_success)
                            is BaseResult.Error -> getString(R.string.error_faild_to_delete_profile_image)
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showPopupMenu() {
        val popup = PopupMenu(requireContext(), binding.menuBtn)
        popup.menuInflater.inflate(R.menu.menu_profile_image, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            val currentPosition = binding.profileImageVp.currentItem

            if (profileImageAdapter.currentList.isEmpty()) {
                return@setOnMenuItemClickListener false
            }
            // 현재 보고 있는 이미지 정보를 가져옵니다.
            val currentImage = profileImageAdapter.currentList[currentPosition]

            when (item.itemId) {
                R.id.item_change_thumbnail -> {
                    true
                }
                R.id.item_save_image -> {
                    // 빈 상태 이미지는 저장하지 않도록 URL을 확인합니다.
                    if (currentImage.id != EMPTY_ID) {
                        viewModel.saveImageToGallery(currentImage.url)
                    } else {
                        Toast.makeText(requireContext(), R.string.cannot_save_default_image, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.item_delete_record -> {
                    viewModel.deleteImage(currentImage.id)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    companion object {
        const val EMPTY_ID = -1L
    }
}