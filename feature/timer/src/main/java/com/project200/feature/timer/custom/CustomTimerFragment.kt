package com.project200.feature.timer.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.project200.feature.timer.TimerListFragment
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.feedback.UndabangAlertDialogFragment
import com.project200.presentation.compose.components.feedback.UndabangBottomSheet
import com.project200.undabang.feature.timer.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomTimerFragment : Fragment() {
    private val viewModel: CustomTimerViewModel by viewModels()
    private val args: CustomTimerFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setTimerId(args.customTimerId)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTimerData()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val title by viewModel.title.collectAsStateWithLifecycle()
                val steps by viewModel.steps.collectAsStateWithLifecycle()
                val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
                val remainingTime by viewModel.remainingTime.collectAsStateWithLifecycle()
                val isRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
                val isRepeatEnabled by viewModel.isRepeatEnabled.collectAsStateWithLifecycle()
                val isTimerFinished by viewModel.isTimerFinished.collectAsStateWithLifecycle()

                CustomTimerScreen(
                    title = title,
                    steps = steps,
                    currentStepIndex = currentStepIndex,
                    remainingTime = remainingTime,
                    totalStepTime = viewModel.totalStepTime,
                    isRunning = isRunning,
                    isRepeatEnabled = isRepeatEnabled,
                    isTimerFinished = isTimerFinished,
                    onPlayPauseClick = {
                        if (isRunning) viewModel.pauseTimer() else viewModel.startTimer()
                    },
                    onEndClick = { viewModel.resetTimer(true) },
                    onRepeatToggle = viewModel::toggleRepeat,
                    onStepClick = viewModel::jumpToStep,
                    onMenuClick = ::showMenu,
                    onBackClick = ::popWithRefresh,
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    popWithRefresh()
                }
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isTimerFinished.collect { isFinished ->
                        // 타이머 종료 시 reset 호출 (UI는 상태 기반이라 별도 갱신 불필요)
                        if (isFinished) {
                            viewModel.resetTimer(false)
                        }
                    }
                }
                launch {
                    viewModel.deleteResult.collect { result ->
                        when (result) {
                            is BaseResult.Success -> {
                                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                                    TimerListFragment.REFRESH_KEY,
                                    true,
                                )
                                findNavController().popBackStack()
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.custom_timer_delete_success),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            is BaseResult.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.custom_timer_error_delete_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.errorEvent.collect {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.error_failed_to_load_list),
                            Toast.LENGTH_SHORT,
                        ).show()
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun popWithRefresh() {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(TimerListFragment.REFRESH_KEY, true)
        findNavController().popBackStack()
    }

    private fun showMenu() {
        UndabangBottomSheet.showMenu(
            fragmentManager = parentFragmentManager,
            onEditClick = {
                findNavController().navigate(
                    CustomTimerFragmentDirections.actionCustomTimerToCustomTimerFormFragment(
                        args.customTimerId,
                    ),
                )
            },
            onDeleteClick = { showDeleteConfirmationDialog() },
        )
    }

    private fun showDeleteConfirmationDialog() {
        UndabangAlertDialogFragment.show(
            fragmentManager = parentFragmentManager,
            title = getString(R.string.custom_timer_delete_alert),
            onCancel = {},
            onConfirm = { viewModel.deleteTimer() },
        )
    }
}
