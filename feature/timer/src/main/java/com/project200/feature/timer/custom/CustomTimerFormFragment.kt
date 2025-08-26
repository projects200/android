package com.project200.feature.timer.custom

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.CustomTimerValidationResult
import com.project200.feature.timer.TimePickerDialog
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentCustomTimerFormBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CustomTimerFormFragment : BindingFragment<FragmentCustomTimerFormBinding>(R.layout.fragment_custom_timer_form) {

    private val viewModel: CustomTimerFormViewModel by viewModels()
    private lateinit var stepAdapter: AddedStepRVAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val args: CustomTimerFormFragmentArgs by navArgs()

    override fun getViewBinding(view: View): FragmentCustomTimerFormBinding {
        return FragmentCustomTimerFormBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadData(args.customTimerId)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            val titleRes = if (viewModel.isEditMode) R.string.custom_timer_edit else R.string.custom_timer_create
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
        val itemTouchHelperCallback = StepItemMoveCallback { from, to ->
            viewModel.moveStep(from, to)
        }
        itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

        stepAdapter = AddedStepRVAdapter(object : OnStepItemClickListener {
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
            override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
                itemTouchHelper.startDrag(viewHolder)
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
            itemTouchHelper.attachToRecyclerView(this)
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
                // 검증 결과에 따른 메시지 매핑
                ToastMessageType.EMPTY_TITLE -> R.string.custom_timer_error_empty_title
                ToastMessageType.NO_STEPS -> R.string.custom_timer_error_no_steps
                ToastMessageType.INVALID_STEP_TIME -> R.string.custom_timer_error_invalid_time
                ToastMessageType.MAX_STEPS -> R.string.custom_timer_error_max_steps
                // API 에러 메시지 매핑
                ToastMessageType.CREATE_ERROR -> R.string.custom_timer_error_create_failed
                ToastMessageType.EDIT_ERROR -> R.string.custom_timer_error_edit_failed
                ToastMessageType.GET_ERROR -> R.string.error_failed_to_load_list
                ToastMessageType.UNKNOWN_ERROR -> R.string.unknown_error
            }
            Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show()
        }

        viewModel.confirmResult.observe(viewLifecycleOwner) { id ->
            if (id != null) {
                findNavController().navigate(
                    CustomTimerFormFragmentDirections.actionCustomTimerFormFragmentToCustomTimerFragment(id)
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