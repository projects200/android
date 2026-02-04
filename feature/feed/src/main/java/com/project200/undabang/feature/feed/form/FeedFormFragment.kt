package com.project200.undabang.feature.feed.form

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.project200.domain.model.PreferredExercise
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.view.SelectionBottomSheetDialog
import com.project200.undabang.feature.feed.R
import com.project200.undabang.feature.feed.databinding.FragmentFeedFormBinding
import com.project200.undabang.feature.feed.list.FeedListFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FeedFormFragment : BindingFragment<FragmentFeedFormBinding>(R.layout.fragment_feed_form) {

    private val viewModel: FeedFormViewModel by viewModels()
    private val imageAdapter = FeedFormImageAdapter { uri ->
        viewModel.removeImage(uri)
    }

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    override fun getViewBinding(view: View): FragmentFeedFormBinding {
        return FragmentFeedFormBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initView()
        initObserver()
    }

    private fun initToolbar() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.feed_form_title))
            showBackButton(true) { findNavController().navigateUp() }
        }
        binding.completeBtn.setOnClickListener {
            viewModel.submitFeed(binding.contentEt.text.toString())
        }
    }

    private fun initView() {
        binding.imageListRv.adapter = imageAdapter

        binding.addImageBtn.setOnClickListener {
            pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.dabangSelectionTv.setOnClickListener {
            showDabangSelection()
        }
        
        binding.arrowIv.setOnClickListener {
            showDabangSelection()
        }
    }

    private fun showDabangSelection() {
        viewModel.requestShowDabangSelection()
    }

    private fun displayDabangSelection(types: List<PreferredExercise>) {
        val names = types.map { it.name }
        
        SelectionBottomSheetDialog(names) { selectedName ->
            val selected = types.find { it.name == selectedName }
            viewModel.selectType(selected)
            binding.dabangSelectionTv.text = selectedName
            binding.dabangSelectionTv.setTextColor(resources.getColor(com.project200.undabang.presentation.R.color.black, null))
        }.show(parentFragmentManager, SelectionBottomSheetDialog::class.java.name)
        viewModel.onDabangSelectionShown()
    }

    private fun initObserver() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            binding.nicknameTv.text = profile.nickname
            Glide.with(this)
                .load(profile.profileImageUrl)
                .placeholder(com.project200.undabang.presentation.R.drawable.ic_profile_default)
                .circleCrop()
                .into(binding.profileIv)
        }

        viewModel.selectedImages.observe(viewLifecycleOwner) { uris ->
            imageAdapter.submitList(uris)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingPb.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.createSuccess.observe(viewLifecycleOwner) {
            Toast.makeText(context, R.string.feed_form_create_success, Toast.LENGTH_SHORT).show()
            findNavController().previousBackStackEntry?.savedStateHandle?.set(FeedListFragment.REFRESH_KEY, true)
            findNavController().popBackStack()
        }

        viewModel.error.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.showDabangSelection.observe(viewLifecycleOwner) { types ->
            if (!types.isNullOrEmpty()) {
                displayDabangSelection(types)
            }
        }
    }
}
