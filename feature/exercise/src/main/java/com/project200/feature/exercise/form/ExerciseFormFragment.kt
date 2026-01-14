package com.project200.feature.exercise.form

import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.common.constants.RuleConstants.ALLOWED_EXTENSIONS
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.common.utils.CommonDateTimeFormatters.HH_MM_KR
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_MM_DD_KR
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import com.project200.feature.exercise.detail.ExerciseDetailFragment
import com.project200.feature.exercise.utils.ScoreGuidanceState
import com.project200.feature.exercise.utils.TimeSelectionState
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.ImageUtils.compressImage
import com.project200.presentation.utils.ImageValidator
import com.project200.presentation.utils.ImageValidator.FAIL_TO_READ
import com.project200.presentation.utils.ImageValidator.INVALID_TYPE
import com.project200.presentation.utils.ImageValidator.OVERSIZE
import com.project200.presentation.utils.KeyboardAdjustHelper.applyEdgeToEdgeInsets
import com.project200.presentation.utils.TimeEditTextLimiter.addRangeLimit
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.presentation.utils.UiUtils.getScreenWidthPx
import com.project200.presentation.view.SelectionBottomSheetDialog
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseFormBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

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
        binding.root.applyEdgeToEdgeInsets()
        binding.baseToolbar.apply {
            showBackButton(true) { findNavController().navigateUp() }
            binding.baseToolbar.setTitle(
                if (args.recordId == -1L) {
                    getString(R.string.record_exercise)
                } else {
                    getString(R.string.edit_exercise)
                },
            )
        }
        viewModel.loadInitialRecord(args.recordId)
        setupRVAdapter((getScreenWidthPx(requireActivity()) - dpToPx(requireContext(), GRID_SPAN_MARGIN)) / GRID_SPAN_COUNT)
        initListeners()
    }

    private fun initListeners() {
        // 운동 종류 선택 버튼
        binding.recordTypeSelectBtn.setOnClickListener {
            val items = viewModel.exerciseTypeList.value
            if (items.isNullOrEmpty()) {
                viewModel.loadExerciseTypes() // 데이터가 없으면 다시 요청
                return@setOnClickListener
            }

            SelectionBottomSheetDialog(items, binding.recordTypeSelectBtn.text.toString()) { selectedType ->
                if (selectedType == ExerciseFormViewModel.DIRECT_INPUT) {
                    // 직접 입력 선택
                    binding.recordTypeSelectBtn.setText(R.string.exercise_record_type_direct)
                    binding.recordTypeEt.apply {
                        setText("")
                        visibility = View.VISIBLE
                        requestFocus()
                    }
                } else {
                    binding.recordTypeSelectBtn.setText(selectedType)
                    binding.recordTypeEt.visibility = View.GONE
                }
            }.show(parentFragmentManager, SelectionBottomSheetDialog::class.java.name)
        }

        // 시간/날짜 버튼 클릭 리스너 설정
        binding.startDateBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.START_DATE) }
        binding.startTimeBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.START_TIME) }
        binding.endDateBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.END_DATE) }
        binding.endTimeBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.END_TIME) }

        binding.timeHourEt.addRangeLimit(23)
        binding.timeMinuteEt.addRangeLimit(59)

        // 캘린더 날짜 선택
        binding.exerciseDateCalendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
            viewModel.updateDate(year, month, dayOfMonth)
        }

        // 시간 입력 확인
        binding.timeConfirmBtn.setOnClickListener {
            val hour = binding.timeHourEt.text.toString().toIntOrNull()
            val minute = binding.timeMinuteEt.text.toString().toIntOrNull()

            if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                Toast.makeText(requireContext(), "유효한 시간을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.updateTime(hour, minute)
        }

        // 기록 완료 버튼
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

    private fun launchGallery() {
        pickMultipleMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun setupObservers() {
        // 시작 시간
        viewModel.startTime.observe(viewLifecycleOwner) { dateTime ->
            binding.startDateBtn.text = dateTime?.format(YYYY_MM_DD_KR)
            binding.startTimeBtn.text = dateTime?.format(HH_MM_KR)
            // CalendarView의 날짜도 동기화
            dateTime?.let {
                val calendar = Calendar.getInstance()
                calendar.time = Date.from(it.atZone(ZoneId.systemDefault()).toInstant())
                binding.exerciseDateCalendar.date = calendar.timeInMillis
            }
        }

        // 종료 시간
        viewModel.endTime.observe(viewLifecycleOwner) { dateTime ->
            binding.endDateBtn.text = dateTime?.format(YYYY_MM_DD_KR)
            binding.endTimeBtn.text = dateTime?.format(HH_MM_KR)
        }

        // 시간 선택 상태 (어떤 버튼이 선택되었는지)
        viewModel.timeSelectionState.observe(viewLifecycleOwner) { state ->
            updateTimeSelectionUi(state)
        }

        viewModel.imageItems.observe(viewLifecycleOwner) { items ->
            imageAdapter.submitList(items.toList())
        }

        viewModel.initialDataLoaded.observe(viewLifecycleOwner) { record ->
            if (record != null) setupInitialData(record)
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
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        ExerciseDetailFragment.KEY_RECORD_UPDATED,
                        true,
                    )
                    findNavController().popBackStack()
                }
                is ExerciseEditResult.ContentFailure -> { // 내용 수정 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        ExerciseDetailFragment.KEY_RECORD_UPDATED,
                        true,
                    )
                    findNavController().popBackStack()
                }
                is ExerciseEditResult.ImageFailure -> { // 이미지 삭제/업로드 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        ExerciseDetailFragment.KEY_RECORD_UPDATED,
                        true,
                    )
                    findNavController().popBackStack()
                }
                is ExerciseEditResult.Failure -> { // 내용 수정, 이미지 삭제/업로드 실패
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { messageId ->
            messageId?.let {
                Toast.makeText(requireContext(), getString(messageId), Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.scoreGuidanceState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScoreGuidanceState.Hidden -> {
                    binding.scoreWarningTv.isVisible = false
                }
                is ScoreGuidanceState.Warning -> {
                    binding.scoreWarningTv.isVisible = true
                    binding.scoreWarningTv.text = getString(state.messageId)
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

    // 선택된 시간/날짜 UI를 업데이트하는 함수
    private fun updateTimeSelectionUi(state: TimeSelectionState) {
        // 모든 선택 UI 초기화
        binding.startDateBtn.background = null
        binding.startTimeBtn.background = null
        binding.endDateBtn.background = null
        binding.endTimeBtn.background = null

        binding.exerciseDateCalendar.isVisible = false
        binding.timeSelectorLl.isVisible = false

        // 현재 상태에 맞는 UI 활성화
        val indicator = getDrawable(requireContext(), R.drawable.bg_time_indicator)
        when (state) {
            TimeSelectionState.START_DATE -> {
                binding.startDateBtn.background = indicator
                binding.exerciseDateCalendar.isVisible = true
            }
            TimeSelectionState.START_TIME -> {
                binding.startTimeBtn.background = indicator
                binding.timeSelectorLl.isVisible = true
                // 현재 시간으로 EditText 초기화
                val time = viewModel.startTime.value
                binding.timeHourEt.setText(time?.hour?.toString()?.padStart(2, '0') ?: "")
                binding.timeMinuteEt.setText(time?.minute?.toString()?.padStart(2, '0') ?: "")
            }
            TimeSelectionState.END_DATE -> {
                binding.endDateBtn.background = indicator
                binding.exerciseDateCalendar.isVisible = true
            }
            TimeSelectionState.END_TIME -> {
                binding.endTimeBtn.background = indicator
                binding.timeSelectorLl.isVisible = true

                val time = viewModel.endTime.value
                binding.timeHourEt.setText(time?.hour?.toString()?.padStart(2, '0') ?: "")
                binding.timeMinuteEt.setText(time?.minute?.toString()?.padStart(2, '0') ?: "")
            }
            TimeSelectionState.NONE -> { }
        }
    }

    private fun handleSuccessfulCreate(earnedPoints: Int) {
        when {
            (earnedPoints > 0) -> {
                ScoreCongratulationDialog(earnedPoints).apply {
                    confirmClickListener = {
                        findNavController().popBackStack()
                    }
                }.show(parentFragmentManager, ScoreCongratulationDialog::class.java.name)
            }
            else -> {
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
