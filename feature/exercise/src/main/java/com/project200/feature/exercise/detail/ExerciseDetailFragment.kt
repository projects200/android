package com.project200.feature.exercise.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.project200.domain.model.BaseResult
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.feedback.UndabangAlertDialogFragment
import com.project200.presentation.compose.components.feedback.UndabangBottomSheet
import com.project200.presentation.utils.UiState
import com.project200.presentation.utils.mapFailureToString
import com.project200.undabang.feature.exercise.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExerciseDetailFragment : Fragment() {
    private val viewModel: ExerciseDetailViewModel by viewModels()
    private val args: ExerciseDetailFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.getExerciseRecord(args.recordId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val state by viewModel.exerciseRecord.collectAsStateWithLifecycle()
                ExerciseDetailScreen(
                    state = state,
                    onBackClick = { findNavController().navigateUp() },
                    onShareClick = ::handleShareClick,
                    onMenuClick = ::showExerciseDetailMenu,
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
                    viewModel.exerciseRecord.collect { state ->
                        // 에러 시 토스트
                        if (state is UiState.Error) {
                            Toast.makeText(
                                requireContext(),
                                requireContext().mapFailureToString(state.failure),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                launch {
                    viewModel.deleteResult.collect { result ->
                        when (result) {
                            is BaseResult.Success -> findNavController().popBackStack()
                            is BaseResult.Error -> {
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        observeRefreshSignal()
    }

    private fun observeRefreshSignal() {
        // 이전 화면에서 새로고침 요청이 있을 경우에만 데이터를 새로고침합니다.
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Boolean>(KEY_RECORD_UPDATED)?.observe(viewLifecycleOwner) { shouldRefresh ->
            if (shouldRefresh) {
                viewModel.getExerciseRecord(args.recordId)
                savedStateHandle.remove<Boolean>(KEY_RECORD_UPDATED)
            }
        }
    }

    private fun handleShareClick() {
        val record = (viewModel.exerciseRecord.value as? UiState.Success)?.data
        if (record?.pictures.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.share_image_required, Toast.LENGTH_SHORT).show()
        } else {
            findNavController().navigate(
                ExerciseDetailFragmentDirections.actionExerciseDetailFragmentToExerciseShareEditFragment(args.recordId),
            )
        }
    }

    private fun showExerciseDetailMenu() {
        // 종속 다이얼로그(ExerciseMenuBottomSheetDialog) → 공통 UndabangBottomSheet.showMenu로 대체
        UndabangBottomSheet.showMenu(
            fragmentManager = parentFragmentManager,
            onEditClick = {
                findNavController().navigate(
                    ExerciseDetailFragmentDirections
                        .actionExerciseDetailFragmentToExerciseFormFragment(args.recordId),
                )
            },
            onDeleteClick = { showDeleteConfirmationDialog() },
        )
    }

    private fun showDeleteConfirmationDialog() {
        UndabangAlertDialogFragment.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.exercise_record_delete_alert),
            onCancel = {},
            onConfirm = { viewModel.deleteExerciseRecord(args.recordId) },
        )
    }

    companion object {
        const val KEY_RECORD_UPDATED = "record_updated"
    }
}
