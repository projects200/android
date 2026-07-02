package com.project200.feature.timer.simple

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
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.TimePickerDialog
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.timer.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SimpleTimerFragment : Fragment() {
    private val viewModel: SimpleTimerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val remainingTime by viewModel.remainingTime.collectAsStateWithLifecycle()
                val isRunning by viewModel.isTimerRunning.collectAsStateWithLifecycle()
                val timers by viewModel.timerItems.collectAsStateWithLifecycle()

                SimpleTimerScreen(
                    remainingTime = remainingTime,
                    totalTime = viewModel.totalTime,
                    isRunning = isRunning,
                    timers = timers,
                    onPlayPauseClick = {
                        if (isRunning) {
                            viewModel.pauseTimer()
                        } else if (viewModel.totalTime > 0) {
                            viewModel.startTimer()
                        }
                    },
                    onTimerItemClick = { simpleTimer ->
                        viewModel.setAndStartTimer(simpleTimer.time)
                    },
                    onTimerEditClick = { simpleTimer -> showTimePickerDialog(simpleTimer) },
                    onTimerDeleteClick = { simpleTimer -> viewModel.deleteTimerItem(simpleTimer.id) },
                    onAddClick = { showTimePickerDialog() },
                    onSortClick = viewModel::changeSortOrder,
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
                viewModel.toastMessage.collect { type ->
                    if (type == SimpleTimerToastMessage.GET_ERROR) findNavController().navigateUp()
                    val messageResId =
                        when (type) {
                            SimpleTimerToastMessage.GET_ERROR -> R.string.load_simple_timer_error
                            SimpleTimerToastMessage.EDIT_ERROR -> R.string.edit_simple_timer_error
                            SimpleTimerToastMessage.ADD_ERROR -> R.string.add_simple_timer_error
                            SimpleTimerToastMessage.DELETE_ERROR -> R.string.delete_simple_timer_error
                        }
                    Toast.makeText(requireContext(), getString(messageResId), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTimePickerDialog(simpleTimer: SimpleTimer? = null) {
        val isEditMode = simpleTimer != null
        val initialTime = simpleTimer?.time ?: SimpleTimerViewModel.DEFAULT_ADD_TIME_SEC

        TimePickerDialog(
            initialTime = initialTime,
            onTimeSelected = { newTime ->
                if (newTime <= 0) {
                    Toast.makeText(requireContext(), getString(R.string.invalid_simple_timer_error), Toast.LENGTH_SHORT).show()
                    return@TimePickerDialog
                }

                if (isEditMode) {
                    viewModel.updateTimerItem(simpleTimer!!.copy(time = newTime))
                } else {
                    viewModel.addTimerItem(newTime)
                }
            },
        ).show(parentFragmentManager, TAG)
    }

    companion object {
        private const val TAG = "SimpleTimerFragment"
    }
}
