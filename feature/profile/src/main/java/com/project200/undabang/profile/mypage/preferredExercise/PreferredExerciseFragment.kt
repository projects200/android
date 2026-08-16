package com.project200.undabang.profile.mypage.preferredExercise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.project200.common.utils.PreferredExerciseDayFormatter
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.profile.R
import com.project200.undabang.profile.mypage.MypageFragment
import com.project200.undabang.profile.utils.CompletionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PreferredExerciseFragment : Fragment() {
    private val viewModel: PreferredExerciseViewModel by viewModels()
    private val args: PreferredExerciseFragmentArgs by navArgs()

    @Inject
    lateinit var formatter: PreferredExerciseDayFormatter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        viewModel.initNickname(args.nickname)
        return ComposeView(requireContext()).apply {
            applyAppTheme {
                var step by rememberSaveable { mutableStateOf(PreferredExerciseStep.TYPE) }

                val uiModels by viewModel.exerciseUiModels.collectAsStateWithLifecycle()
                val selectedUiModels by viewModel.selectedExerciseUiModels.collectAsStateWithLifecycle()
                val completionState by viewModel.completionState.collectAsStateWithLifecycle()

                BackHandler(enabled = step == PreferredExerciseStep.DETAIL) {
                    step = PreferredExerciseStep.TYPE
                }

                PreferredExerciseScreen(
                    step = step,
                    nickname = viewModel.nickname,
                    uiModels = uiModels,
                    selectedUiModels = selectedUiModels,
                    isLoading = completionState is CompletionState.Loading,
                    formatter = formatter,
                    onBackClick = {
                        if (step == PreferredExerciseStep.DETAIL) {
                            step = PreferredExerciseStep.TYPE
                        } else {
                            findNavController().popBackStack()
                        }
                    },
                    onNextOrCompleteClick = {
                        when (step) {
                            PreferredExerciseStep.TYPE -> step = PreferredExerciseStep.DETAIL
                            PreferredExerciseStep.DETAIL -> viewModel.completePreferredExerciseChanges()
                        }
                    },
                    onTypeClick = viewModel::updateSelectedExercise,
                    onTypeLimitReached = {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.preferred_exercise_type_max_error),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onDayClick = viewModel::updateDaySelection,
                    onSkillClick = viewModel::updateSkillLevel,
                )
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.completionState.collect { state ->
                    when (state) {
                        is CompletionState.Success -> {
                            Toast.makeText(requireContext(), R.string.preferred_exercise_success, Toast.LENGTH_SHORT).show()
                            findNavController().previousBackStackEntry?.savedStateHandle?.set(MypageFragment.REFRESH_KEY, true)
                            findNavController().popBackStack()
                            viewModel.consumeCompletionState()
                        }
                        is CompletionState.NoChanges -> {
                            Toast.makeText(requireContext(), R.string.preferred_exercise_no_changed, Toast.LENGTH_SHORT).show()
                            viewModel.consumeCompletionState()
                        }
                        is CompletionState.NoneSelected -> {
                            Toast.makeText(requireContext(), R.string.preferred_exercise_none_selected, Toast.LENGTH_SHORT).show()
                            viewModel.consumeCompletionState()
                        }
                        is CompletionState.IncompleteSelection -> {
                            Toast.makeText(requireContext(), R.string.preferred_exercise_incomplete_seleted, Toast.LENGTH_SHORT).show()
                            viewModel.consumeCompletionState()
                        }
                        is CompletionState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                            viewModel.consumeCompletionState()
                        }
                        is CompletionState.Loading, is CompletionState.Idle -> Unit
                    }
                }
            }
        }
    }
}
