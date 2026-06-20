package com.project200.undabang.feature.feed.form

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.feedback.UndabangBottomSheet
import com.project200.presentation.utils.collectToast
import com.project200.undabang.feature.feed.list.FeedListFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FeedFormFragment : Fragment() {
    private val viewModel: FeedFormViewModel by viewModels()
    private val args: FeedFormFragmentArgs by navArgs()

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.addImages(uris)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initData(feedId = args.feedId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
                val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
                val content by viewModel.content.collectAsStateWithLifecycle()
                val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
                val registeredImages by viewModel.registeredImages.collectAsStateWithLifecycle()
                val selectedImages by viewModel.selectedImages.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

                FeedFormScreen(
                    isEditMode = isEditMode,
                    userProfile = userProfile,
                    content = content,
                    selectedTypeName = selectedType?.name,
                    registeredImages = registeredImages,
                    newImages = selectedImages,
                    isLoading = isLoading,
                    onContentChange = viewModel::updateContent,
                    onDabangSelectClick = viewModel::requestShowDabangSelection,
                    onAddImageClick = {
                        pickImagesLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemoveRegisteredImage = viewModel::removeExistingImage,
                    onRemoveNewImage = viewModel::removeImage,
                    onCompleteClick = viewModel::submitFeed,
                    onBackClick = { findNavController().navigateUp() },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        collectToast(viewModel.toastEvent)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.showDabangSelection.collect { types ->
                        val names = types.map { it.name }
                        UndabangBottomSheet.showSelection(
                            fragmentManager = parentFragmentManager,
                            items = names,
                        ) { selectedName ->
                            val selected = types.find { it.name == selectedName }
                            viewModel.selectType(selected)
                        }
                    }
                }
                launch {
                    viewModel.createSuccess.collect {
                        android.widget.Toast.makeText(
                            requireContext(),
                            com.project200.undabang.feature.feed.R.string.feed_form_create_success,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        findNavController().previousBackStackEntry?.savedStateHandle
                            ?.set(FeedListFragment.REFRESH_KEY, true)
                        findNavController().popBackStack()
                    }
                }
                launch {
                    viewModel.updateSuccess.collect {
                        android.widget.Toast.makeText(
                            requireContext(),
                            com.project200.undabang.feature.feed.R.string.feed_form_update_success,
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        findNavController().previousBackStackEntry?.savedStateHandle
                            ?.set(FEED_UPDATED_KEY, true)
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    companion object {
        const val FEED_UPDATED_KEY = "feed_updated"
    }
}
