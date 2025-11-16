package com.project200.feature.exercise.form

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.common.constants.RuleConstants.ALLOWED_EXTENSIONS
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.ImageUtils.compressImage
import com.project200.presentation.utils.ImageValidator
import com.project200.presentation.utils.ImageValidator.FAIL_TO_READ
import com.project200.presentation.utils.ImageValidator.INVALID_TYPE
import com.project200.presentation.utils.ImageValidator.OVERSIZE
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.presentation.utils.UiUtils.getScreenWidthPx
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseFormBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

@AndroidEntryPoint
class ExerciseFormFragment : BindingFragment<FragmentExerciseFormBinding>(R.layout.fragment_exercise_form) {
    private val viewModel: ExerciseFormViewModel by viewModels()
    private val args: ExerciseFormFragmentArgs by navArgs()
    private lateinit var imageAdapter: ExerciseImageAdapter

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
                    } else if (reason == OVERSIZE) {
                        compressImage(requireContext(), uri)?.let { validatedUris.add(it) }
                    } else {
                        errorReason = reason
                    }
                }

                // 유효성 검사 에러가 있었다면 메시지 표시
                errorReason?.let { reason ->
                    val errorMessage =
                        when (reason) {
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
                    if (validatedUris.isNotEmpty()) {
                        viewModel.addImage(validatedUris)
                    }
                }
            }
        }

    override fun getViewBinding(view: View): FragmentExerciseFormBinding {
        return FragmentExerciseFormBinding.bind(view)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadInitialRecord(args.recordId)
        setupKeyboardAdjustments()
    }

    private fun setupRVAdapter(calculatedItemSize: Int) {
        imageAdapter =
            ExerciseImageAdapter(
                itemSize = calculatedItemSize,
                onAddItemClick = {
                    val currentImageCount = viewModel.imageItems.value?.count { it !is ExerciseImageListItem.AddButtonItem } ?: 0
                    if (currentImageCount >= MAX_IMAGE) {
                        Toast.makeText(requireContext(), getString(R.string.exercise_record_max_image), Toast.LENGTH_SHORT).show()
                    } else {
                        launchGallery()
                    }
                },
                onDeleteItemClick = { item ->
                    viewModel.removeImage(item)
                },
            )

        binding.exerciseImageRv.apply {
            addItemDecoration(GridItemDecoration(GRID_SPAN_COUNT, dpToPx(requireContext(), RV_MARGIN)))
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
                recordId = args.recordId,
                title = binding.recordTitleEt.text.toString().trim(),
                type = binding.recordTypeEt.text.toString().trim(),
                location = binding.recordLocationEt.text.toString().trim(),
                detail = binding.recordDescEt.text.toString().trim(),
            )
        }
    }

    private fun setupKeyboardAdjustments() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            Timber.tag("ExerciseFormFragment").d("setupKeyboardAdjustments called")
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // 키보드가 올라와 있으면 키보드 높이만큼, 아니면 네비게이션 바 높이만큼 패딩 적용
            val paddingBottom =
                if (imeHeight > 0) {
                    imeHeight
                } else {
                    // record_complete_btn의 높이 (btn_height)와 layout_marginBottom (32dp)를 더한 값
                    // 이 값은 dpToPx를 사용하여 픽셀로 변환해야 합니다.
                    val buttonHeight = dpToPx(requireContext(), binding.recordCompleteBtn.height.toFloat())
                    val buttonMarginBottom = dpToPx(requireContext(), binding.recordCompleteBtn.marginBottom.toFloat())
                    buttonHeight + buttonMarginBottom + navigationBarHeight
                }

            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, paddingBottom)
            insets
        }
    }

    private fun launchGallery() {
        pickMultipleMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun showTimePickerDialog(isStart: Boolean) {
        // 기존 선택된 시간이 있다면 해당 시간을 사용하고, 없다면 현재 시간을 기준으로 5분 단위로 내림한 시간으로 설정
        val dateTimeToShow =
            (if (isStart) viewModel.startTime.value else viewModel.endTime.value) ?: run {
                val now = LocalDateTime.now()
                val flooredMinute = (now.minute / 5) * 5
                now.withMinute(flooredMinute).truncatedTo(ChronoUnit.MINUTES)
            }

        val initialCalendar =
            Calendar.getInstance().apply {
                timeInMillis = dateTimeToShow.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
            }

        // 다이얼로그 생성 및 표시
        val dialog =
            ExerciseTimeDialog.newInstance(initialCalendar).apply {
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
            binding.startTimeBtn.text = dateTime?.let {
                viewModel.dateTimeFormatter.format(
                    it,
                )
            } ?: getString(R.string.exercise_record_start_time)
            binding.startTimeIv.isVisible = dateTime == null
        }

        viewModel.endTime.observe(viewLifecycleOwner) { dateTime ->
            binding.endTimeBtn.text = dateTime?.let {
                viewModel.dateTimeFormatter.format(
                    it,
                )
            } ?: getString(R.string.exercise_record_end_time)
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
                    handleSuccessfulCreate(result.earnedPoints)
                }
                is SubmissionResult.PartialSuccess -> {
                    // 부분 성공 (이미지 업로드 실패)
                    findNavController().navigate(
                        ExerciseFormFragmentDirections
                            .actionExerciseFormFragmentToExerciseDetailFragment(result.recordId),
                    )
                }
                is SubmissionResult.Failure -> { // 기록 생성 실패
                }
            }
        }

        viewModel.editResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ExerciseEditResult.Success -> { // 기록 수정, 이미지 삭제/업로드 성공
                    findNavController().popBackStack()
                }
                is ExerciseEditResult.ContentFailure -> { // 내용 수정 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is ExerciseEditResult.ImageFailure -> { // 이미지 삭제/업로드 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is ExerciseEditResult.Failure -> { // 내용 수정, 이미지 삭제/업로드 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.scoreGuidanceState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScoreGuidanceState.Hidden -> {
                    binding.scoreWarningTv.isVisible = false
                }
                is ScoreGuidanceState.Warning -> {
                    binding.scoreWarningTv.isVisible = true
                    binding.scoreWarningTv.text = state.message
                }
                is ScoreGuidanceState.PointsAvailable -> {
                    binding.scoreWarningTv.isVisible = false
                }
            }
        }
    }

    private fun setupInitialData(record: ExerciseRecord) {
        binding.recordTitleEt.setText(record.title)
        binding.recordTypeEt.setText(record.personalType)
        binding.recordLocationEt.setText(record.location)
        binding.recordDescEt.setText(record.detail)
    }

    private fun handleSuccessfulCreate(earnedPoints: Int) {
        when {
            (earnedPoints > 0) -> {
                Timber.tag("ExerciseFormFragment").d("PointsAvailable")
                ScoreCongratulationDialog(earnedPoints).apply {
                    confirmClickListener = {
                        findNavController().popBackStack()
                    }
                }.show(parentFragmentManager, "ScoreCongratulationDialog")
            }
            else -> {
                Timber.tag("ExerciseFormFragment").d("불가능")
                findNavController().popBackStack()
            }
        }
    }

    companion object {
        private const val GRID_SPAN_COUNT = 3
        private const val GRID_SPAN_MARGIN = 80f
        private const val RV_MARGIN = 20f
    }
}
