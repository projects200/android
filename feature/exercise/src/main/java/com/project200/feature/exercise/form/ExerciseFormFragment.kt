package com.project200.feature.exercise.form

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.presentation.utils.UiUtils.getScreenWidthPx
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseFormBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.util.Calendar

@AndroidEntryPoint
class ExerciseFormFragment : BindingFragment<FragmentExerciseFormBinding>(R.layout.fragment_exercise_form) {

    private val viewModel: ExerciseFormViewModel by viewModels()
    private lateinit var imageAdapter: ExerciseImageAdapter

    private val pickMultipleMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE)) { uris ->
            if (uris.isNotEmpty()) {
                // 최대 이미지 개수를 넘은 경우
                if (uris.size > viewModel.getCurrentPermittedImageCount()) {
                    Toast.makeText(requireContext(), getString(R.string.exercise_record_max_image), Toast.LENGTH_LONG).show()
                } else {
                    viewModel.addImage(uris)
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchGallery()
            } else {
                Toast.makeText(requireContext(), getString(R.string.exercise_record_image_access), Toast.LENGTH_SHORT).show()
            }
        }

    override fun getViewBinding(view: View): FragmentExerciseFormBinding {
        return FragmentExerciseFormBinding.bind(view)
    }

    private fun setupRVAdapter(calculatedItemSize: Int) {
        imageAdapter = ExerciseImageAdapter(
            itemSize = calculatedItemSize,
            onAddItemClick = {
                val currentImageCount = viewModel.imageItems.value?.count { it is ExerciseImageListItem.ImageItem } ?: 0
                if (currentImageCount >= MAX_IMAGE) {
                    Toast.makeText(requireContext(), getString(R.string.exercise_record_max_image), Toast.LENGTH_SHORT).show()
                } else {
                    checkPermissionAndLaunchGallery()
                }
            },
            onDeleteItemClick = { item ->
                viewModel.removeImage(item)
            }
        )

        binding.exerciseImageRv.apply {
            addItemDecoration(GridSpacingItemDecoration(GRID_SPAN_COUNT, dpToPx(requireContext(), RV_MARGIN)))
            layoutManager = GridLayoutManager(requireContext(), GRID_SPAN_COUNT)
            adapter = imageAdapter
        }
    }


    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.exercise_record))
            showBackButton(true) { findNavController().navigateUp() }
        }

        setupRVAdapter((getScreenWidthPx(requireActivity()) - dpToPx(requireContext(), GRID_SPAN_MARGIN)) / GRID_SPAN_COUNT)

        binding.startTimeBtn.setOnClickListener { showTimePickerDialog(true) }
        binding.endTimeBtn.setOnClickListener { showTimePickerDialog(false) }

        binding.recordCompleteBtn.setOnClickListener {
            viewModel.submitRecord(
                title = binding.recordTitleEt.text.toString().trim(),
                type = binding.recordTypeEt.text.toString().trim(),
                location = binding.recordLocationEt.text.toString().trim(),
                detail = binding.recordDescEt.text.toString().trim()
            )
        }
    }

    private fun checkPermissionAndLaunchGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else { Manifest.permission.READ_EXTERNAL_STORAGE }

        when {
            checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
                launchGallery()
            }
            shouldShowRequestPermissionRationale(permission) -> { requestPermissionLauncher.launch(permission) }
            else -> { requestPermissionLauncher.launch(permission) }
        }
    }

    private fun launchGallery() {
        pickMultipleMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun showTimePickerDialog(isStart: Boolean) {
        val dialog = ExerciseTimeDialog().apply {
            onDateTimeSelected = { year, month, day, hour, minute ->
                val selectedDateTime = LocalDateTime.of(year, month + 1, day, hour, minute)
                if (isStart) {
                    viewModel.setStartTime(selectedDateTime)
                } else {
                    if (!viewModel.setEndTime(selectedDateTime)) {
                        Toast.makeText(requireContext(), getString(R.string.exercise_record_time_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show(parentFragmentManager, ExerciseTimeDialog::class.java.simpleName)
    }

    override fun setupObservers() {
        viewModel.startTime.observe(viewLifecycleOwner) { dateTime ->
            binding.startTimeBtn.text = dateTime?.let { viewModel.dateTimeFormatter.format(it) } ?: getString(R.string.exercise_record_start_time)
            binding.startTimeIv.isVisible = dateTime == null
        }

        viewModel.endTime.observe(viewLifecycleOwner) { dateTime ->
            binding.endTimeBtn.text = dateTime?.let { viewModel.dateTimeFormatter.format(it) } ?: getString(R.string.exercise_record_end_time)
            binding.endTimeIv.isVisible = dateTime == null
        }

        viewModel.imageItems.observe(viewLifecycleOwner) { items ->
            imageAdapter.submitList(items.toList())
        }
    }

    companion object {
        private const val GRID_SPAN_COUNT = 3
        private const val GRID_SPAN_MARGIN = 80f
        private const val RV_MARGIN = 20f
    }
}