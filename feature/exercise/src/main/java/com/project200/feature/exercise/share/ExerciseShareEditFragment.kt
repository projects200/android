package com.project200.feature.exercise.share

import android.graphics.Bitmap
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.project200.feature.exercise.utils.ExerciseRecordStickerGenerator
import com.project200.feature.exercise.utils.ExerciseShareHelper
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseShareEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExerciseShareEditFragment : BindingFragment<FragmentExerciseShareEditBinding>(R.layout.fragment_exercise_share_edit) {

    private val viewModel: ExerciseShareEditViewModel by viewModels()
    private val args: ExerciseShareEditFragmentArgs by navArgs()

    private var currentStickerBitmap: Bitmap? = null

    override fun getViewBinding(view: View): FragmentExerciseShareEditBinding {
        return FragmentExerciseShareEditBinding.bind(view)
    }

    override fun setupViews() {
        viewModel.loadExerciseRecord(args.recordId)

        binding.themeDarkBtn.setOnClickListener {
            viewModel.selectTheme(StickerTheme.DARK)
        }
        binding.themeLightBtn.setOnClickListener {
            viewModel.selectTheme(StickerTheme.LIGHT)
        }
        binding.themeMinimalBtn.setOnClickListener {
            viewModel.selectTheme(StickerTheme.MINIMAL)
        }

        binding.cancelBtn.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.shareBtn.setOnClickListener {
            shareImage()
        }
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.backgroundImageUrl.collect { url ->
                        url?.let { loadBackgroundImage(it) }
                    }
                }

                launch {
                    viewModel.selectedTheme.collect { theme ->
                        updateThemeButtonSelection(theme)
                        updateStickerPreview(theme)
                    }
                }

                launch {
                    viewModel.exerciseRecord.collect { record ->
                        if (record != null) {
                            updateStickerPreview(viewModel.selectedTheme.value)
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun loadBackgroundImage(url: String) {
        Glide.with(this)
            .load(url)
            .centerCrop()
            .into(binding.backgroundImage)
    }

    private fun updateThemeButtonSelection(theme: StickerTheme) {
        val selectedAlpha = 1.0f
        val unselectedAlpha = 0.4f

        binding.themeDarkBtn.alpha = if (theme == StickerTheme.DARK) selectedAlpha else unselectedAlpha
        binding.themeLightBtn.alpha = if (theme == StickerTheme.LIGHT) selectedAlpha else unselectedAlpha
        binding.themeMinimalBtn.alpha = if (theme == StickerTheme.MINIMAL) selectedAlpha else unselectedAlpha
    }

    private fun updateStickerPreview(theme: StickerTheme) {
        val record = viewModel.exerciseRecord.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            currentStickerBitmap?.recycle()
            currentStickerBitmap = ExerciseRecordStickerGenerator.generateStickerBitmap(
                requireContext(),
                record,
                theme
            )
            binding.stickerPreview.setImageBitmap(currentStickerBitmap)
        }
    }

    private fun shareImage() {
        val record = viewModel.exerciseRecord.value ?: return
        val theme = viewModel.selectedTheme.value
        val transformInfo = binding.stickerPreview.getTransformInfo()

        binding.loadingOverlay.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ExerciseShareHelper.shareExerciseRecord(requireContext(), record, theme, transformInfo)
            } finally {
                binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        currentStickerBitmap?.recycle()
        currentStickerBitmap = null
        super.onDestroyView()
    }
}
