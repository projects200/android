package com.project200.feature.exercise.form

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.common.constants.RuleConstants.ALLOWED_EXTENSIONS
import com.project200.common.constants.RuleConstants.MAX_IMAGE
import com.project200.domain.model.ExerciseEditResult
import com.project200.domain.model.SubmissionResult
import com.project200.feature.exercise.detail.ExerciseDetailFragment
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.feedback.UndabangBottomSheet
import com.project200.presentation.utils.ImageUtils.compressImage
import com.project200.presentation.utils.ImageValidator
import com.project200.presentation.utils.ImageValidator.FAIL_TO_READ
import com.project200.presentation.utils.ImageValidator.INVALID_TYPE
import com.project200.presentation.utils.ImageValidator.OVERSIZE
import com.project200.undabang.feature.exercise.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate

@AndroidEntryPoint
class ExerciseFormFragment : Fragment() {
    private val viewModel: ExerciseFormViewModel by viewModels()
    private val args: ExerciseFormFragmentArgs by navArgs()

    private val pickMultipleMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGE)) { uris ->
            if (uris.isNotEmpty()) {
                handlePickedImages(uris)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadInitialRecord(args.recordId)
        viewModel.loadExerciseTypes()
        viewModel.loadExerciseLocation()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val startTime by viewModel.startTime.collectAsStateWithLifecycle()
                val endTime by viewModel.endTime.collectAsStateWithLifecycle()
                val timeSelection by viewModel.timeSelectionState.collectAsStateWithLifecycle()
                val imageItems by viewModel.imageItems.collectAsStateWithLifecycle()
                val initialDataLoaded by viewModel.initialDataLoaded.collectAsStateWithLifecycle()
                val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
                val scoreGuidance by viewModel.scoreGuidanceState.collectAsStateWithLifecycle()

                // 입력 필드 상태는 화면 단에서 hoisting (회전 보존)
                var title by rememberSaveable { mutableStateOf("") }
                var selectedType by rememberSaveable { mutableStateOf("") }
                var directTypeInput by rememberSaveable { mutableStateOf("") }
                var showDirectTypeInput by rememberSaveable { mutableStateOf(false) }
                var selectedLocation by rememberSaveable { mutableStateOf("") }
                var detail by rememberSaveable { mutableStateOf("") }

                // 수정 모드 초기 데이터 로드 시 입력 필드에 반영 (한 번만)
                LaunchInitialData(initialDataLoaded) { record ->
                    title = record.title
                    selectedType = record.personalType
                    selectedLocation = record.location
                    detail = record.detail
                }

                // PlaceSearch 결과 처리 (Compose 영역 밖이지만 같이 처리)
                ObservePlaceSearchResult { name -> selectedLocation = name }

                ExerciseFormScreen(
                    isEditMode = args.recordId != -1L,
                    title = title,
                    selectedType = selectedType,
                    directTypeInput = directTypeInput,
                    showDirectTypeInput = showDirectTypeInput,
                    startTime = startTime,
                    endTime = endTime,
                    timeSelectionState = timeSelection,
                    selectedLocation = selectedLocation,
                    detail = detail,
                    scoreGuidanceState = scoreGuidance,
                    imageItems = imageItems,
                    isLoading = isLoading,
                    onTitleChange = { title = it },
                    onTypeSelectClick = {
                        showTypeSelection(selectedType) { selected, isDirect ->
                            selectedType = selected
                            showDirectTypeInput = isDirect
                            if (isDirect) directTypeInput = ""
                        }
                    },
                    onDirectTypeInputChange = { directTypeInput = it },
                    onLocationSelectClick = {
                        showLocationSelection(selectedLocation) { selected ->
                            selectedLocation = selected
                        }
                    },
                    onTimeButtonClick = viewModel::onTimeSelectionClick,
                    onDateSelected = ::handleDateSelected,
                    onTimeConfirmed = viewModel::updateTime,
                    onDetailChange = { detail = it },
                    onAddImageClick = ::launchGallery,
                    onDeleteImageClick = viewModel::removeImage,
                    onCompleteClick = {
                        val typeToSubmit =
                            if (showDirectTypeInput) directTypeInput.trim() else selectedType.trim()
                        if (showDirectTypeInput && directTypeInput.isBlank()) {
                            Toast.makeText(
                                requireContext(),
                                R.string.exercise_record_type_direct_input_warning,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@ExerciseFormScreen
                        }
                        viewModel.submitRecord(
                            recordId = args.recordId,
                            title = title.trim(),
                            type = typeToSubmit,
                            location = selectedLocation.trim(),
                            detail = detail.trim(),
                        )
                    },
                    onBackClick = { findNavController().navigateUp() },
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.toastMessage.collect { messageId ->
                        Toast.makeText(requireContext(), getString(messageId), Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.createResult.collect { result ->
                        when (result) {
                            is SubmissionResult.Success -> handleSuccessfulCreate(result.earnedPoints)
                            is SubmissionResult.PartialSuccess -> {
                                // 부분 성공 (이미지 업로드 실패)
                                findNavController().navigate(
                                    ExerciseFormFragmentDirections
                                        .actionExerciseFormFragmentToExerciseDetailFragment(result.recordId),
                                )
                            }
                            is SubmissionResult.Failure -> { /* 기록 생성 실패 */ }
                        }
                    }
                }
                launch {
                    viewModel.editResult.collect { result ->
                        handleEditResult(result)
                    }
                }
            }
        }
    }

    private fun handleEditResult(result: ExerciseEditResult) {
        when (result) {
            is ExerciseEditResult.Success -> {
                // 기록 수정, 이미지 삭제/업로드 성공
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    ExerciseDetailFragment.KEY_RECORD_UPDATED,
                    true,
                )
                findNavController().popBackStack()
            }
            is ExerciseEditResult.ContentFailure -> {
                // 내용 수정 실패
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    ExerciseDetailFragment.KEY_RECORD_UPDATED,
                    true,
                )
                findNavController().popBackStack()
            }
            is ExerciseEditResult.ImageFailure -> {
                // 이미지 삭제/업로드 실패
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    ExerciseDetailFragment.KEY_RECORD_UPDATED,
                    true,
                )
                findNavController().popBackStack()
            }
            is ExerciseEditResult.Failure -> {
                // 내용 수정, 이미지 삭제/업로드 실패
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDateSelected(date: LocalDate) {
        viewModel.updateDate(date.year, date.monthValue, date.dayOfMonth)
    }

    private fun handlePickedImages(uris: List<Uri>) {
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
        } else if (validatedUris.isNotEmpty()) {
            viewModel.addImage(validatedUris)
        }
    }

    private fun launchGallery() {
        val currentImageCount = viewModel.imageItems.value.count { it !is ExerciseImageListItem.AddButtonItem }
        if (currentImageCount >= MAX_IMAGE) {
            Toast.makeText(requireContext(), getString(R.string.exercise_record_max_image), Toast.LENGTH_SHORT).show()
            return
        }
        pickMultipleMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun showTypeSelection(
        current: String,
        onSelected: (selected: String, isDirect: Boolean) -> Unit,
    ) {
        val items = viewModel.exerciseTypeList.value
        if (items.isEmpty()) {
            viewModel.loadExerciseTypes() // 데이터가 없으면 다시 요청
            return
        }
        UndabangBottomSheet.showSelection(
            fragmentManager = parentFragmentManager,
            items = items,
            selectedItem = current,
        ) { selectedType ->
            if (selectedType == ExerciseFormViewModel.DIRECT_INPUT) {
                // 직접 입력 선택
                onSelected(getString(R.string.exercise_record_type_direct), true)
            } else {
                onSelected(selectedType, false)
            }
        }
    }

    private fun showLocationSelection(
        current: String,
        onSelected: (String) -> Unit,
    ) {
        val items = viewModel.exerciseLocation.value
        if (items.isEmpty()) {
            viewModel.loadExerciseLocation()
            return
        }
        UndabangBottomSheet.showSelection(
            fragmentManager = parentFragmentManager,
            items = items,
            selectedItem = current,
        ) { selectedLocation ->
            if (selectedLocation == ExerciseFormViewModel.DIRECT_INPUT) {
                // 직접 입력 선택
                findNavController().navigate(
                    ExerciseFormFragmentDirections.actionExerciseFormFragmentToPlaceSearchFragment(),
                )
            } else {
                onSelected(selectedLocation)
            }
        }
    }

    private fun handleSuccessfulCreate(earnedPoints: Int) {
        when {
            earnedPoints > 0 -> {
                ScoreCongratulationDialog(earnedPoints).apply {
                    confirmClickListener = { findNavController().popBackStack() }
                }.show(parentFragmentManager, ScoreCongratulationDialog::class.java.name)
            }
            else -> findNavController().popBackStack()
        }
    }

    @androidx.compose.runtime.Composable
    private fun LaunchInitialData(
        initialRecord: com.project200.domain.model.ExerciseRecord?,
        onLoaded: (com.project200.domain.model.ExerciseRecord) -> Unit,
    ) {
        androidx.compose.runtime.LaunchedEffect(initialRecord) {
            initialRecord?.let(onLoaded)
        }
    }

    @androidx.compose.runtime.Composable
    private fun ObservePlaceSearchResult(onResult: (String) -> Unit) {
        androidx.compose.runtime.DisposableEffect(Unit) {
            val owner = viewLifecycleOwner
            val handle = findNavController().currentBackStackEntry?.savedStateHandle
            val liveData = handle?.getLiveData<String>(PlaceSearchFragment.KEY_SELECTED_PLACE)
            val observer =
                androidx.lifecycle.Observer<String> { name ->
                    onResult(name)
                    // 선택 후에는 결과 삭제
                    handle?.remove<String>(PlaceSearchFragment.KEY_SELECTED_PLACE)
                }
            liveData?.observe(owner, observer)
            onDispose { liveData?.removeObserver(observer) }
        }
    }
}
