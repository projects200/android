package com.project200.undabang.profile.mypage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.domain.model.BaseResult
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.profile.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileImageDetailFragment : Fragment() {
    private val viewModel: ProfileImageDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val images by viewModel.profileImages.collectAsStateWithLifecycle()
                ProfileImageDetailScreen(
                    images = images,
                    onBackClick = { popBackWithRefresh() },
                    onChangeThumbnail = { id -> viewModel.changeThumbnail(id) },
                    onSaveImage = { image ->
                        if (image.id != ProfileImageDetailViewModel.EMPTY_ID) {
                            viewModel.saveImageToGallery(image.url)
                        } else {
                            Toast.makeText(requireContext(), R.string.cannot_save_default_image, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteImage = { id -> viewModel.deleteImage(id) },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    popBackWithRefresh()
                }
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.getProfileImageErrorToast.collect {
                        Toast.makeText(requireContext(), R.string.error_faild_to_load_profile_image, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.imageSaveResult.collect { result ->
                        val message =
                            when (result) {
                                true -> getString(R.string.image_save_success)
                                false -> getString(R.string.image_save_failed)
                            }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.imageDeleteResult.collect { result ->
                        val message =
                            when (result) {
                                is BaseResult.Success -> getString(R.string.image_delete_success)
                                is BaseResult.Error -> getString(R.string.error_faild_to_delete_profile_image)
                            }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.changeThumbnailResult.collect { result ->
                        val message =
                            when (result) {
                                is BaseResult.Success -> getString(R.string.change_thumbnail_success)
                                is BaseResult.Error -> getString(R.string.change_thumbnail_failed)
                            }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun popBackWithRefresh() {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(MypageFragment.REFRESH_KEY, true)
        findNavController().popBackStack()
    }
}
