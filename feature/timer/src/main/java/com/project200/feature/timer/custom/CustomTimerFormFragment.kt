package com.project200.feature.timer.custom

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import com.project200.feature.timer.TimePickerDialog
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.timer.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomTimerFormFragment : Fragment() {
    private val viewModel: CustomTimerFormViewModel by viewModels()
    private val args: CustomTimerFormFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadData(args.customTimerId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                CustomTimerFormScreen(
                    title = state.title,
                    listItems = state.listItems,
                    isEditMode = viewModel.isEditMode,
                    onTitleChange = viewModel::updateTimerTitle,
                    onStepNameChange = viewModel::updateStepName,
                    onStepTimeClick = { id, time -> showTimePickerDialog(id, time) },
                    onStepDelete = viewModel::removeStep,
                    onMove = viewModel::moveStep,
                    onNewStepNameChange = viewModel::updateNewStepName,
                    onNewStepTimeClick = { time -> showTimePickerDialog(null, time) },
                    onAddStep = viewModel::addStep,
                    onCompleteClick = {
                        clearFocusAndHideKeyboard()
                        viewModel.submitCustomTimer()
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
                    viewModel.toast.collect { type ->
                        val messageResId =
                            when (type) {
                                ToastMessageType.EMPTY_TITLE -> R.string.custom_timer_error_empty_title
                                ToastMessageType.NO_STEPS -> R.string.custom_timer_error_no_steps
                                ToastMessageType.INVALID_STEP_TIME -> R.string.custom_timer_error_invalid_time
                                ToastMessageType.MAX_STEPS -> R.string.custom_timer_error_max_steps
                                ToastMessageType.NO_CHANGES -> R.string.custom_timer_error_no_changes
                                ToastMessageType.EMPTY_STEP_NAME -> R.string.custom_timer_error_empty_step_name
                                ToastMessageType.CREATE_ERROR -> R.string.custom_timer_error_create_failed
                                ToastMessageType.EDIT_ERROR -> R.string.custom_timer_error_edit_failed
                                ToastMessageType.GET_ERROR -> R.string.error_failed_to_load_list
                                ToastMessageType.UNKNOWN_ERROR -> R.string.unknown_error
                            }
                        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.submitResult.collect { id ->
                        if (findNavController().currentDestination?.id != R.id.customTimerFormFragment) {
                            return@collect
                        }
                        if (viewModel.isEditMode) {
                            findNavController().navigateUp()
                        } else {
                            findNavController().navigate(
                                CustomTimerFormFragmentDirections.actionCustomTimerFormFragmentToCustomTimerFragment(id),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showTimePickerDialog(
        id: Long? = null,
        time: Int,
    ) {
        TimePickerDialog(
            time,
            onTimeSelected = { newTimeInSeconds ->
                if (newTimeInSeconds < 5) {
                    Toast.makeText(requireContext(), R.string.custom_timer_error_invalid_time, Toast.LENGTH_SHORT).show()
                    return@TimePickerDialog
                }
                id?.let { viewModel.updateStepTime(it, newTimeInSeconds) }
                    ?: viewModel.updateNewStepTime(newTimeInSeconds)
            },
        ).show(parentFragmentManager, this::class.java.simpleName)
    }

    private fun clearFocusAndHideKeyboard() {
        val current = activity?.currentFocus ?: return
        current.clearFocus()
        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(current.windowToken, 0)
    }
}
