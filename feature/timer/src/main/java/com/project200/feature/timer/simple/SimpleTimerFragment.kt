package com.project200.feature.timer.simple

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.TimePickerDialog
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.utils.MenuStyler
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentSimpleTimerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SimpleTimerFragment : BindingFragment<FragmentSimpleTimerBinding>(R.layout.fragment_simple_timer) {
    private val viewModel: SimpleTimerViewModel by viewModels()
    private lateinit var timerAdapter: SimpleTimerRVAdapter

    // 뷰 높이가 계산되었는지 확인하는 플래그
    private var isHeightCalculated = false

    override fun getViewBinding(view: View): FragmentSimpleTimerBinding {
        return FragmentSimpleTimerBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.simple_timer))
            showBackButton(true) { findNavController().navigateUp() }
        }
        initClickListeners()
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        timerAdapter =
            SimpleTimerRVAdapter(
                onItemClick = { simpleTimer ->
                    binding.timerProgressbar.setProgress(1f, animated = false)
                    viewModel.setAndStartTimer(simpleTimer.time)
                },
                onMenuClick = { simpleTimer, view -> showPopupMenu(view, simpleTimer) },
                onAddClick = { showTimePickerDialog() },
            )
        binding.simpleTimerRv.apply {
            layoutManager = GridLayoutManager(requireContext(), RV_ITEM_COL_COUNT)
            adapter = timerAdapter
            postponeEnterTransition() // Transition 사용 시 화면 깜빡임 방지

            // RecyclerView의 레이아웃이 완료된 후 높이를 계산
            viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (isHeightCalculated || height == 0) return

                        // 리스너를 즉시 제거하여 중복 호출 방지
                        viewTreeObserver.removeOnGlobalLayoutListener(this)

                        val paddingInPixels = resources.getDimensionPixelSize(R.dimen.simple_timer_padding)
                        val recyclerViewHeight = height - paddingInPixels
                        timerAdapter.itemHeight = recyclerViewHeight / RV_ITEM_ROW_COUNT

                        // 높이 계산 후 어댑터 갱신
                        timerAdapter.notifyDataSetChanged()
                        isHeightCalculated = true
                        startPostponedEnterTransition() // Transition 다시 시작
                    }
                },
            )
        }
    }

    override fun setupObservers() {
        viewModel.remainingTime.observe(viewLifecycleOwner) { remainingTime ->
            binding.timerTv.text = remainingTime.toFormattedTimeAsLong()
            val total = viewModel.totalTime
            if (total > 0) {
                binding.timerProgressbar.setProgress(remainingTime.toFloat() / total.toFloat())
            } else {
                binding.timerProgressbar.setProgress(1f, animated = false)
            }
        }

        viewModel.isTimerRunning.observe(viewLifecycleOwner) { isRunning ->
            updateRunningState(isRunning)
        }

        // ViewModel의 타이머 아이템 목록을 관찰
        viewModel.timerItems.observe(viewLifecycleOwner) { timers ->
            timerAdapter.submitList(timers)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // UI가 STARTED 상태일 때만 수집하고, STOPPED 상태가 되면 중단
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toastMessage.collect { type ->
                    if (type == SimpleTimerToastMessage.GET_ERROR) findNavController()
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

    private fun updateRunningState(isRunning: Boolean) {
        if (isRunning) {
            binding.timerBtn.setImageResource(R.drawable.ic_stop)
        } else {
            binding.timerBtn.setImageResource(R.drawable.ic_play)
        }
    }

    private fun initClickListeners() {
        binding.timerBtn.setOnClickListener {
            if (viewModel.isTimerRunning.value == true) {
                viewModel.pauseTimer()
            } else {
                val totalTime = viewModel.totalTime
                if (totalTime > 0) {
                    viewModel.startTimer()
                }
            }
        }

        binding.sortBtn.setOnClickListener {
            viewModel.changeSortOrder()
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

    private fun showPopupMenu(
        view: View,
        simpleTimer: SimpleTimer,
    ) {
        val contextWrapper = ContextThemeWrapper(requireContext(), com.project200.undabang.presentation.R.style.PopupItemStyle)

        PopupMenu(contextWrapper, view).apply {
            menuInflater.inflate(R.menu.timer_item_menu, this.menu)

            menu.findItem(R.id.action_edit)?.let {
                MenuStyler.applyTextColor(requireContext(), it, android.R.color.black)
            }
            menu.findItem(R.id.action_delete)?.let {
                MenuStyler.applyTextColor(requireContext(), it, com.project200.undabang.presentation.R.color.error_red)
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> showTimePickerDialog(simpleTimer)
                    R.id.action_delete -> viewModel.deleteTimerItem(simpleTimer.id)
                }
                true
            }

            MenuStyler.showIcons(this)
        }.show()
    }

    companion object {
        private const val TAG = "SimpleTimerFragment"
        private const val RV_ITEM_COL_COUNT = 2
        private const val RV_ITEM_ROW_COUNT = 3
    }
}
