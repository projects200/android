package com.project200.feature.exercise.form

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.common.constants.RuleConstants.ALLOWED_EXTENSIONS
import com.project200.common.constants.RuleConstants.MAX_FILE_SIZE
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.presentation.utils.ImageValidator
import com.project200.presentation.utils.ImageValidator.FAIL_TO_READ
import com.project200.presentation.utils.ImageValidator.INVALID_TYPE
import com.project200.presentation.utils.ImageValidator.OVERSIZE
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.presentation.utils.UiUtils.getScreenWidthPx
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseFormBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime

@AndroidEntryPoint
class ExerciseFormFragment : BindingFragment<FragmentExerciseFormBinding>(R.layout.fragment_exercise_form) {

    private val viewModel: ExerciseFormViewModel by viewModels()
    private lateinit var imageAdapter: ExerciseImageAdapter
    private var fragmentNavigator: FragmentNavigator? = null

    private val pickMultipleMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE)) { uris ->
            if (uris.isNotEmpty()) {
                val validatedUris = mutableListOf<Uri>()
                var errorReason: String? = null

                // 유효성 검사 및 유효한 URI 수집
                for (uri in uris) {
                    val (isValid, reason) = ImageValidator.validateImageFile(uri, requireContext())
                    if (isValid) {
                        validatedUris.add(uri)
                    } else if (errorReason == null) {
                        errorReason = reason
                    }
                }

                // 유효성 검사 에러가 있었다면 메시지 표시
                errorReason?.let { reason ->
                    val errorMessage = when (reason) {
                        OVERSIZE -> getString(R.string.image_error_oversized, "${MAX_FILE_SIZE}MB")
                        INVALID_TYPE -> getString(R.string.image_error_invalid_type, ALLOWED_EXTENSIONS.joinToString(", "))
                        FAIL_TO_READ -> getString(R.string.image_error_file_read)
                        else -> getString(R.string.unknown_error)
                    }
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }

                // 최대 이미지 개수를 넘은 경우
                if (validatedUris.size > viewModel.getCurrentPermittedImageCount()) {
                    Toast.makeText(requireContext(), getString(R.string.exercise_record_max_image), Toast.LENGTH_LONG).show()
                } else {
                    viewModel.addImage(validatedUris)
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ : Boolean ->
            launchGallery()
        }

    override fun getViewBinding(view: View): FragmentExerciseFormBinding {
        return FragmentExerciseFormBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadInitialRecord()
    }

    private fun setupRVAdapter(calculatedItemSize: Int) {
        imageAdapter = ExerciseImageAdapter(
            itemSize = calculatedItemSize,
            onAddItemClick = {
                val currentImageCount = viewModel.imageItems.value?.count { it !is ExerciseImageListItem.AddButtonItem } ?: 0
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
        binding.baseToolbar.showBackButton(true) { findNavController().navigateUp() }

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

        viewModel.initialDataLoaded.observe(viewLifecycleOwner) { record ->
            if (record != null) {
                setupInitialData(record)
                binding.baseToolbar.setTitle(getString(R.string.edit_exercise))
            } else {
                binding.baseToolbar.setTitle(getString(R.string.record_exercise))
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingGroup.isVisible = isLoading
            binding.recordCompleteBtn.isEnabled = !isLoading
        }

        viewModel.createResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is SubmissionResult.Success -> {
                    // 기록 생성, 이미지 업로드 성공
                    fragmentNavigator?.navigateFromExerciseFormToExerciseDetail(result.recordId)
                }
                is SubmissionResult.PartialSuccess -> {
                    // 부분 성공 (이미지 업로드 실패)
                    fragmentNavigator?.navigateFromExerciseFormToExerciseDetail(result.recordId)
                }
                is SubmissionResult.Failure -> { // 기록 생성 실패
                }
            }
        }

        viewModel.editResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ExerciseEditResult.Success -> { // 기록 수정, 이미지 삭제/업로드 성공
                    fragmentNavigator?.navigateFromExerciseFormToExerciseDetail(result.recordId)
                }
                is ExerciseEditResult.ContentFailure -> { // 내용 수정 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    fragmentNavigator?.navigateFromExerciseFormToExerciseDetail(result.recordId)
                }
                is ExerciseEditResult.ImageFailure -> { // 이미지 삭제/업로드 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    fragmentNavigator?.navigateFromExerciseFormToExerciseDetail(result.recordId)
                }
                is ExerciseEditResult.Failure -> { // 내용 수정, 이미지 삭제/업로드 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupInitialData(record: ExerciseRecord) {
        binding.recordTitleEt.setText(record.title)
        binding.recordTypeEt.setText(record.personalType)
        binding.recordLocationEt.setText(record.location)
        binding.recordDescEt.setText(record.detail)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentNavigator) {
            fragmentNavigator = context
        } else {
            throw ClassCastException("$context must implement FragmentNavigator")
        }
    }

    override fun onDetach() {
        fragmentNavigator = null
        super.onDetach()
    }

    companion object {
        private const val GRID_SPAN_COUNT = 3
        private const val GRID_SPAN_MARGIN = 80f
        private const val RV_MARGIN = 20f
    }
}