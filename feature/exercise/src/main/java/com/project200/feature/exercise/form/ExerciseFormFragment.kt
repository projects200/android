package com.project200.feature.exercise.form

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.common.constants.RuleConstants.ALLOWED_EXTENSIONS
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.common.utils.CommonDateTimeFormatters.HH_MM_KR
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_MM_DD_KR
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.ExerciseRecord
import com.project200.domain.model.SubmissionResult
import com.project200.feature.exercise.utils.ScoreGuidanceState
import com.project200.feature.exercise.utils.TimeSelectionState
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
import java.util.Date

@AndroidEntryPoint
class ExerciseFormFragment : BindingFragment<FragmentExerciseFormBinding>(R.layout.fragment_exercise_form) {
    private val viewModel: ExerciseFormViewModel by viewModels()
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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _: Boolean ->
            launchGallery()
        }

    override fun getViewBinding(view: View): FragmentExerciseFormBinding {
        return FragmentExerciseFormBinding.bind(view)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadInitialRecord()
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
                        checkPermissionAndLaunchGallery()
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

        // 시간/날짜 버튼 클릭 리스너 설정
        binding.startDateBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.START_DATE) }
        binding.startTimeBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.START_TIME) }
        binding.endDateBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.END_DATE) }
        binding.endTimeBtn.setOnClickListener { viewModel.onTimeSelectionClick(TimeSelectionState.END_TIME) }

        // 캘린더 날짜 선택 리스너 설정
        binding.exerciseDateCalendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
            viewModel.updateDate(year, month, dayOfMonth)
        }

        // 시간 입력 '확인' 버튼 리스너
        binding.timeConfirmBtn.setOnClickListener {
            val hour = binding.timeHourEt.text.toString().toIntOrNull()
            val minute = binding.timeMinuteEt.text.toString().toIntOrNull()

            if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                Toast.makeText(requireContext(), "유효한 시간을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.updateTime(hour, minute)
        }

        binding.recordCompleteBtn.setOnClickListener {
            viewModel.submitRecord(
                title = binding.recordTitleEt.text.toString().trim(),
                type = binding.recordTypeEt.text.toString().trim(),
                location = binding.recordLocationEt.text.toString().trim(),
                detail = binding.recordDescEt.text.toString().trim(),
            )
        }
    }
    private fun checkPermissionAndLaunchGallery() {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        when {
            checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED -> {
                launchGallery()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
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

    // [추가] 선택된 시간/날짜 UI를 업데이트하는 함수
    private fun updateTimeSelectionUi(state: TimeSelectionState) {
        // 1. 모든 선택 UI 초기화
        binding.startDateBtn.background = null
        binding.startTimeBtn.background = null
        binding.endDateBtn.background = null
        binding.endTimeBtn.background = null

        binding.exerciseDateCalendar.isVisible = false
        binding.timeSelectorLl.isVisible = false

        // 2. 현재 상태에 맞는 UI 활성화 (요구사항 2, 2-1, 2-2)
        val indicator = getDrawable(requireContext(), R.drawable.bg_time_indicator)
        when (state) {
            TimeSelectionState.START_DATE -> {
                binding.startDateBtn.background = indicator
                binding.exerciseDateCalendar.isVisible = true
            }
            TimeSelectionState.START_TIME -> {
                binding.startTimeBtn.background = indicator
                binding.timeSelectorLl.isVisible = true
                // 현재 시간으로 EditText 초기화 (요구사항 1)
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
                // 현재 시간으로 EditText 초기화 (요구사항 1)
                val time = viewModel.endTime.value
                binding.timeHourEt.setText(time?.hour?.toString()?.padStart(2, '0') ?: "")
                binding.timeMinuteEt.setText(time?.minute?.toString()?.padStart(2, '0') ?: "")
            }
            TimeSelectionState.NONE -> {  }
        }
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
