package com.project200.undabang.feature.feed.form

import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
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
    private val args: FeedFormFragmentArgs by navArgs()
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

    override fun setupViews() {
        viewModel.initData(
            feedId = args.feedId,
            feedContent = args.feedContent,
            feedTypeId = args.feedTypeId,
            feedTypeName = args.feedTypeName
        )
        initToolbar()
        initView()
        initObserver()
    }

    private fun initToolbar() {
        viewModel.isEditMode.observe(viewLifecycleOwner) { isEditMode ->
            val title = if (isEditMode) {
                getString(R.string.feed_form_edit_title)
            } else {
                getString(R.string.feed_form_title)
            }
            binding.baseToolbar.setTitle(title)
        }
        binding.baseToolbar.showBackButton(true) { findNavController().navigateUp() }
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

        viewModel.updateSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(context, R.string.feed_form_update_success, Toast.LENGTH_SHORT).show()
                findNavController().previousBackStackEntry?.savedStateHandle?.set(FEED_UPDATED_KEY, true)
                findNavController().popBackStack()
            }
        }

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is FeedFormEvent.ShowToast -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.showDabangSelection.observe(viewLifecycleOwner) { types ->
            if (!types.isNullOrEmpty()) {
                displayDabangSelection(types)
            }
        }

        viewModel.initialContentForEdit.observe(viewLifecycleOwner) { content ->
            if (!content.isNullOrEmpty()) {
                binding.contentEt.setText(content)
            }
        }

        viewModel.selectedType.observe(viewLifecycleOwner) { type ->
            if (type != null) {
                binding.dabangSelectionTv.text = type.name
                binding.dabangSelectionTv.setTextColor(resources.getColor(com.project200.undabang.presentation.R.color.black, null))
            }
        }
    }

    companion object {
        const val FEED_UPDATED_KEY = "feed_updated"
    }
}
