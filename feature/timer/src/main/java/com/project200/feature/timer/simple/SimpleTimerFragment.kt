package com.project200.feature.timer.simple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.project200.feature.timer.TimePickerDialog
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentSimpleTimerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SimpleTimerFragment : BindingFragment<FragmentSimpleTimerBinding>(R.layout.fragment_simple_timer) {
    private val viewModel: SimpleTimerViewModel by viewModels()
    private var progressAnimator: ValueAnimator? = null
    private var mediaPlayer: MediaPlayer? = null
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

        context?.let { mediaPlayer = MediaPlayer.create(it, R.raw.simple_alarm) }

        initClickListeners()
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        binding.simpleTimerRv.apply {
            layoutManager = GridLayoutManager(requireContext(), RV_ITEM_COL_COUNT)

            // 어댑터만 미리 생성, 데이터는 옵저버에서 설정
            timerAdapter = SimpleTimerRVAdapter(
                onItemClick = { simpleTimer ->
                    stopAndResetTimer()
                    viewModel.setTimer(simpleTimer.time)
                    viewModel.startTimer()
                },
                onMenuClick = { simpleTimer, view ->
                    showPopupMenu(view, simpleTimer)
                }
            )
            adapter = timerAdapter

            // onGlobalLayout 리스너를 사용해 높이를 한 번만 계산
            viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (isHeightCalculated) return

                    binding.simpleTimerRv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val paddingInPixels = resources.getDimensionPixelSize(com.project200.undabang.presentation.R.dimen.base_horizontal_margin)
                    val recyclerViewHeight = binding.simpleTimerRv.height - paddingInPixels
                    timerAdapter.itemHeight = recyclerViewHeight / RV_ITEM_ROW_COUNT

                    // 높이 계산 후 notifyDataSetChanged() 호출
                    timerAdapter.notifyDataSetChanged()
                    isHeightCalculated = true
                }
            })
        }
    }

    private fun stopAndResetTimer() {
        progressAnimator?.cancel()
        progressAnimator = null
        binding.timerProgressbar.progress = 1.0f
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            mediaPlayer?.seekTo(0)
        }
    }

    override fun setupObservers() {
        viewModel.remainingTime.observe(viewLifecycleOwner) { remainingTime ->
            binding.timerTv.text = remainingTime.toFormattedTimeAsLong()
            updateProgressBar(remainingTime)
        }

        viewModel.isTimerRunning.observe(viewLifecycleOwner) { isRunning ->
            updateRunningState(isRunning)
        }

        // ViewModel의 타이머 아이템 목록을 관찰
        viewModel.timerItems.observe(viewLifecycleOwner) { newItems ->
            timerAdapter.items = newItems
            if (isHeightCalculated) {
                timerAdapter.notifyDataSetChanged()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // UI가 STARTED 상태일 때만 수집하고, STOPPED 상태가 되면 중단
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        is TimerEvent.NavigateToErrorScreen -> {
                            findNavController().navigateUp()
                        }
                        is TimerEvent.ShowToast -> {
                            Toast.makeText(requireContext(), getString(R.string.edit_simple_timer_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateProgressBar(remainingTime: Long) {
        val totalTime = viewModel.totalTime
        if (totalTime > 0) {
            progressAnimator?.cancel()
            val currentProgress = binding.timerProgressbar.progress
            val targetProgress = remainingTime.toFloat() / totalTime.toFloat()

            progressAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
                duration = 50L
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    // 뷰가 아직 생성되어 있는지 확인
                    if(view !=null) binding.timerProgressbar.progress = animator.animatedValue as Float
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        // 타이머가 0ms 이하일 때
                        if (remainingTime <= 0) {
                            binding.timerProgressbar.progress = 0f
                            mediaPlayer?.start()
                        }
                    }
                })
                start()
            }
        }
    }

    private fun updateRunningState(isRunning: Boolean) {
        if (isRunning) {
            binding.timerBtn.setImageResource(R.drawable.ic_stop)
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                mediaPlayer?.seekTo(0)
            }
        } else {
            binding.timerBtn.setImageResource(R.drawable.ic_play)
            progressAnimator?.pause()
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
    }

    private fun showPopupMenu(view: View, simpleTimer: SimpleTimer) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.timer_item_menu, popupMenu.menu) // menu_timer_item.xml을 사용

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> { // 수정 메뉴
                    showTimePickerDialog(simpleTimer)
                    true
                }
                // TODO: 삭제 기능 추가 (추가/삭제 함께 구현 예정)
                else -> false
            }
        }
        popupMenu.show()
    }


    private fun showTimePickerDialog(simpleTimer: SimpleTimer) {
        TimePickerDialog(
            simpleTimer.time,
            onTimeSelected = { newTime ->
                viewModel.updateTimerItem(simpleTimer.copy(time = newTime))
            }
        ).show(parentFragmentManager, TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
        progressAnimator?.cancel()
        progressAnimator = null
    }

    companion object {
        private const val TAG = "SimpleTimerFragment"
        private const val RV_ITEM_COL_COUNT = 2
        private const val RV_ITEM_ROW_COUNT = 3
    }
}