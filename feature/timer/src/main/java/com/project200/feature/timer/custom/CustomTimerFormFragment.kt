package com.project200.feature.timer.custom

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginTop
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.resources.MaterialResources.getDimensionPixelSize
import com.project200.domain.model.ValidationResult
import com.project200.feature.timer.TimePickerDialog
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentCustomTimerFormBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CustomTimerFormFragment : BindingFragment<FragmentCustomTimerFormBinding>(R.layout.fragment_custom_timer_form) {

    private val viewModel: CustomTimerFormViewModel by viewModels()
    private lateinit var stepAdapter: AddedStepRVAdapter

    override fun getViewBinding(view: View): FragmentCustomTimerFormBinding {
        return FragmentCustomTimerFormBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.custom_timer_create))
            showBackButton(true) { findNavController().navigateUp() }
        }
        initRecyclerView()
        initListeners()
        setupObservers()
    }

    private fun initListeners() {
        binding.timerTitleEt.addTextChangedListener { title ->
            viewModel.updateTimerTitle(title.toString())
        }

        binding.completeBtn.setOnClickListener {
            clearFocusAndHideKeyboard()
            viewModel.completeCustomTimerCreation()
        }
    }

    private fun initRecyclerView() {
        stepAdapter = AddedStepRVAdapter(object : AddedStepRVAdapter.OnStepItemClickListener {
            // 스텝 아이템 이벤트
            override fun onDeleteClick(id: Long) {
                viewModel.removeStep(id)
            }
            override fun onTimeClick(id: Long, time: Int) {
                showTimePickerDialog(id, time)
            }
            override fun onStepNameChanged(id: Long, name: String) {
                viewModel.updateStepName(id, name)
            }

            // 입력 필드 이벤트
            override fun onNewStepNameChanged(name: String) {
                viewModel.updateNewStepName(name)
            }
            override fun onNewStepTimeClick(currentTime: Int) {
                showTimePickerDialog(null, currentTime)
            }
            override fun onAddStepClick() {
                viewModel.addStep()
            }
        })

        binding.stepRv.apply {
            adapter = stepAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state == null) return@observe

            if (binding.timerTitleEt.text.toString() != state.title) {
                binding.timerTitleEt.setText(state.title)
            }
            stepAdapter.submitList(state.listItems)
        }

        viewModel.toast.observe(viewLifecycleOwner) { toast ->
            val messageResId = when (toast) {
                is ValidationResult.EmptyTitle -> R.string.custom_timer_error_empty_title
                is ValidationResult.NoSteps -> R.string.custom_timer_error_no_steps
                is ValidationResult.InvalidStepTime -> R.string.custom_timer_error_invalid_time
                is ValidationResult.Success -> return@observe
            }
            Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show()
        }

        viewModel.createResult.observe(viewLifecycleOwner) {
            if (it != null) {
                findNavController().navigate(
                    CustomTimerFormFragmentDirections.actionCustomTimerFormFragmentToCustomTimerFragment(it)
                )
            }
        }
    }

    private fun showTimePickerDialog(id: Long? = null, time: Int) {
        TimePickerDialog(
            time,
            onTimeSelected = { newTimeInSeconds ->
                id?.let { viewModel.updateStepTime(id, newTimeInSeconds)} ?: run {
                    // id가 null인 경우는 새 스텝을 추가할 때이므로 새 스텝 시간 업데이트
                    viewModel.updateNewStepTime(newTimeInSeconds)
                }
            }
        ).show(parentFragmentManager, this::class.java.simpleName)
    }

    // 포커스 해제 및 키보드 숨기기
    private fun clearFocusAndHideKeyboard() {
        val currentFocusView = activity?.currentFocus
        if (currentFocusView != null) {
            currentFocusView.clearFocus()
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
        }
    }
}