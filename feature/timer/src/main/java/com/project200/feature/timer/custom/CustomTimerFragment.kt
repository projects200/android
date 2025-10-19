package com.project200.feature.timer.custom

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.domain.model.BaseResult
import com.project200.feature.timer.TimerListFragment
import com.project200.feature.timer.custom.adapter.StepItemDecoration
import com.project200.feature.timer.custom.adapter.StepRVAdapter
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.view.MenuBottomSheetDialog
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentCustomTimerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomTimerFragment : BindingFragment<FragmentCustomTimerBinding>(R.layout.fragment_custom_timer) {
    private val viewModel: CustomTimerViewModel by viewModels()
    private val args: CustomTimerFragmentArgs by navArgs()

    private lateinit var stepRVAdapter: StepRVAdapter

    override fun getViewBinding(view: View): FragmentCustomTimerBinding {
        return FragmentCustomTimerBinding.bind(view)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTimerData()
    }

    override fun setupViews() {
        viewModel.setTimerId(args.customTimerId)

        binding.baseToolbar.apply {
            showBackButton(true) {
                findNavController().previousBackStackEntry?.savedStateHandle?.set(TimerListFragment.REFRESH_KEY, true)
                findNavController().popBackStack()
            }
            setSubButton(R.drawable.ic_menu, onClick = { showMenu() })
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(TimerListFragment.REFRESH_KEY, true)
                    findNavController().popBackStack()
                }
            },
        )

        initClickListeners()
        initRecyclerView()
        binding.timerEndBtn.isClickable = viewModel.isTimerFinished.value == false
    }

    private fun initClickListeners() {
        binding.timerPlayBtn.setOnClickListener {
            if (viewModel.isTimerRunning.value == true) {
                viewModel.pauseTimer()
            } else {
                viewModel.startTimer()
            }
        }
        binding.timerEndBtn.setOnClickListener {
            viewModel.resetTimer(true)
            updateUIForTimerEnd()
        }
        binding.timerRepeatBtn.setOnClickListener {
            viewModel.toggleRepeat()
        }
    }

    private fun initRecyclerView() {
        stepRVAdapter =
            StepRVAdapter { position ->
                viewModel.jumpToStep(position)
                binding.timerProgressbar.setProgress(1f, animated = false)
            }
        binding.customTimerStepRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            viewModel.steps.observe(viewLifecycleOwner) { steps ->
                adapter = stepRVAdapter
            }
            addItemDecoration(StepItemDecoration(ITEM_MARGIN))
        }
    }

    override fun setupObservers() {
        viewModel.steps.observe(viewLifecycleOwner) { steps ->
            stepRVAdapter.submitList(steps)
        }

        viewModel.isTimerRunning.observe(viewLifecycleOwner) { isRunning ->
            updateButtonState(isRunning)
        }


        viewModel.remainingTime.observe(viewLifecycleOwner) { remainingTime ->
            binding.timerTv.text = remainingTime.toFormattedTimeAsLong()

            val total = viewModel.totalStepTime
            if (total > 0) {
                val currentProgress = remainingTime.toFloat() / total.toFloat()
                binding.timerProgressbar.setProgress(currentProgress)
            } else {
                binding.timerProgressbar.setProgress(1f, animated = false)
            }
        }

        viewModel.currentStepIndex.observe(viewLifecycleOwner) { index ->
            if (view != null) {
                updateRecyclerView(index)
                smoothScrollToPosition(index)
                binding.timerProgressbar.setProgress(1f, animated = false)
            }
        }

        viewModel.isTimerFinished.observe(viewLifecycleOwner) { isFinished ->
            binding.timerEndBtn.isClickable = !isFinished
            if (isFinished) {
                updateUIForTimerEnd()
                viewModel.resetTimer(false)
            }
        }

        // 반복 버튼 UI 상태 업데이트를 위한 Observer
        viewModel.isRepeatEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.timerRepeatBtn.setImageResource(
                if (isEnabled) (R.drawable.ic_repeat) else R.drawable.ic_repeat_off,
            )
        }

        // 툴바 타이틀 설정
        viewModel.title.observe(viewLifecycleOwner) { title ->
            binding.baseToolbar.setTitle(title)
        }

        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BaseResult.Success -> {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(TimerListFragment.REFRESH_KEY, true)
                    findNavController().popBackStack()
                    Toast.makeText(requireContext(), getString(R.string.custom_timer_delete_success), Toast.LENGTH_SHORT).show()
                }
                is BaseResult.Error -> {
                    Toast.makeText(requireContext(), getString(R.string.custom_timer_error_delete_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 에러 이벤트 처리
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvent.collect { error ->
                    Toast.makeText(requireContext(), getString(R.string.error_failed_to_load_list), Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }
        }
    }

    // 버튼 상태 업데이트
    private fun updateButtonState(isRunning: Boolean) {
        if (isRunning) {
            binding.timerPlayBtn.backgroundTintList =
                ColorStateList.valueOf(getColor(requireContext(), com.project200.undabang.presentation.R.color.error_red))
            binding.timerPlayBtn.text = getString(R.string.timer_stop)
        } else {
            binding.timerPlayBtn.backgroundTintList =
                ColorStateList.valueOf(getColor(requireContext(), com.project200.undabang.presentation.R.color.main))
            binding.timerPlayBtn.text = getString(R.string.timer_start)
        }
    }

    private fun updateUIForTimerEnd() {
        updateButtonState(false)
        binding.timerProgressbar.setProgress(1f, animated = false)
    }

    private fun updateRecyclerView(currentStepIndex: Int) {
        val adapter = binding.customTimerStepRv.adapter as? StepRVAdapter
        adapter?.highlightItem(currentStepIndex)
    }

    private fun smoothScrollToPosition(position: Int) {
        val layoutManager = binding.customTimerStepRv.layoutManager as? LinearLayoutManager
        layoutManager?.smoothScrollToPosition(binding.customTimerStepRv, null, position)
    }

    private fun showMenu() {
        MenuBottomSheetDialog(
            onEditClicked = {
                findNavController().navigate(
                    CustomTimerFragmentDirections.actionCustomTimerToCustomTimerFormFragment(
                        args.customTimerId,
                    ),
                )
            },
            onDeleteClicked = { showDeleteConfirmationDialog() },
        ).show(parentFragmentManager, MenuBottomSheetDialog::class.java.simpleName)
    }

    private fun showDeleteConfirmationDialog() {
        BaseAlertDialog(
            title = getString(R.string.custom_timer_delete_alert),
            desc = null,
            onConfirmClicked = {
                viewModel.deleteTimer()
            },
        ).show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
    }

    companion object {
        const val ITEM_MARGIN = 6
    }
}
