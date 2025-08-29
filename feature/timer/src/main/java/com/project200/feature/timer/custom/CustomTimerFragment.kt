package com.project200.feature.timer.custom

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.view.MenuBottomSheetDialog
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentCustomTimerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CustomTimerFragment: BindingFragment<FragmentCustomTimerBinding>(R.layout.fragment_custom_timer) {
    private val viewModel: CustomTimerViewModel by viewModels()
    private var progressAnimator: ValueAnimator? = null

    private var stepFinishPlayer: MediaPlayer? = null // 스텝 종료
    private var tickPlayer: MediaPlayer? = null // 카운트다운

    private lateinit var stepRVAdapter: StepRVAdapter

    override fun getViewBinding(view: View): FragmentCustomTimerBinding {
        return FragmentCustomTimerBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            showBackButton(true) { findNavController().navigateUp() }
            setSubButton(R.drawable.ic_menu, onClick = { showMenu() })
        }

        context?.let {
            stepFinishPlayer = MediaPlayer.create(it, R.raw.custom_finish_alarm)
            tickPlayer = MediaPlayer.create(it, R.raw.custom_tick_alarm)
        }
        initClickListeners()
        initRecyclerView()
        setupObservers()
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
            viewModel.resetTimer()
            updateUIForTimerEnd()
        }
        binding.timerRepeatBtn.setOnClickListener {
            viewModel.toggleRepeat()
        }
    }

    private fun initRecyclerView() {
        stepRVAdapter = StepRVAdapter { position ->
            viewModel.jumpToStep(position)
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
            updateRunningState(isRunning)
        }

        viewModel.remainingTime.observe(viewLifecycleOwner) { remainingTime ->
            binding.timerTv.text = remainingTime.toFormattedTimeAsLong()
        }

        viewModel.currentStepIndex.observe(viewLifecycleOwner) { index ->
            if (view != null) { // Fragment View가 살아있는지 확인
                updateRecyclerView(index)
                smoothScrollToPosition(index)
                // 타이머가 실행 중일 때만 애니메이션을 시작 (초기 로드나 리셋 시 불필요한 호출 방지)
                if (viewModel.isTimerRunning.value == true) {
                    startProgressBarAnimation()
                }
            }
        }

        viewModel.isTimerFinished.observe(viewLifecycleOwner) { isFinished ->
            binding.timerEndBtn.isClickable = !isFinished
            if (isFinished) {
                // 반복이 활성화 되어있으면 타이머를 재시작
                if (viewModel.isRepeatEnabled.value == true) {
                    viewModel.restartTimer()
                } else {
                    // 반복이 비활성화 되어있으면 종료 상태로 변경
                    updateUIForTimerEnd()
                    viewModel.resetTimer()

                    // 스텝이 바뀌면 프로그레스바를 100%로 조정
                    progressAnimator?.cancel()
                    binding.timerProgressbar.progress = 1f
                }
            }
        }

        // 반복 버튼 UI 상태 업데이트를 위한 Observer
        viewModel.isRepeatEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.timerRepeatBtn.setImageResource(
                if (isEnabled) (R.drawable.ic_repeat) else R.drawable.ic_repeat_off
            )
        }
        // 스텝 종료 알림음을 위한 Observer
        viewModel.stepFinishedAlarm.observe(viewLifecycleOwner) { shouldPlay ->
            if (shouldPlay) {
                playStepFinishAlarm()
                viewModel.onStepFinishedAlarmPlayed()
            }
        }

        // 카운트다운 알림음을 위한 Observer
        viewModel.playTickSound.observe(viewLifecycleOwner) { shouldPlay ->
            if (shouldPlay) {
                playTickSound()
                viewModel.onTickSoundPlayed()
            }
        }
    }

    private fun updateRunningState(isRunning: Boolean) {
        if (isRunning) {
            binding.timerPlayBtn.backgroundTintList = ColorStateList.valueOf(
                getColor(requireContext(), com.project200.undabang.presentation.R.color.error_led)
            )
            binding.timerPlayBtn.text = getString(R.string.timer_stop)

            // 재생 중인 알림음이 있다면 일시정지
            stepFinishPlayer?.takeIf { it.isPlaying }?.pause()
            tickPlayer?.takeIf { it.isPlaying }?.pause()

            startProgressBarAnimation()
        } else {
            binding.timerPlayBtn.backgroundTintList = ColorStateList.valueOf(
                getColor(requireContext(), com.project200.undabang.presentation.R.color.main)
            )
            binding.timerPlayBtn.text = getString(R.string.timer_start)
            progressAnimator?.pause()
        }
    }

    private fun updateUIForTimerEnd() {
        binding.timerPlayBtn.backgroundTintList = ColorStateList.valueOf(
            getColor(requireContext(), com.project200.undabang.presentation.R.color.main)
        )
        binding.timerPlayBtn.text = getString(R.string.timer_start)
        binding.timerProgressbar.progress = 1f // 종료 후엔 다시 100%로 설정
        progressAnimator?.cancel()
    }

    private fun updateRecyclerView(currentStepIndex: Int) {
        val adapter = binding.customTimerStepRv.adapter as? StepRVAdapter
        adapter?.highlightItem(currentStepIndex)
    }

    private fun smoothScrollToPosition(position: Int) {
        val layoutManager = binding.customTimerStepRv.layoutManager as? LinearLayoutManager
        layoutManager?.smoothScrollToPosition(binding.customTimerStepRv, null, position)
    }

    private fun startProgressBarAnimation() {
        progressAnimator?.cancel()

        val totalStepTime = viewModel.totalStepTime
        val remainingTime = viewModel.remainingTime.value ?: 0L

        // 방어 코드: remainingTime이 totalStepTime보다 약간 클 수 있는 경우를 대비
        val startProgress = (remainingTime.toFloat() / totalStepTime.toFloat()).coerceAtMost(1.0f)

        if (totalStepTime > 0 && remainingTime > 0) {
            progressAnimator = ValueAnimator.ofFloat(startProgress, 0f).apply {
                duration = remainingTime
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    if (view != null) {
                        binding.timerProgressbar.progress = animator.animatedValue as Float
                    }
                }
                start()
            }
        }
    }

    // 스텝 종료 알림음 재생 함수
    private fun playStepFinishAlarm() {
        stepFinishPlayer?.apply {
            if (isPlaying) {
                pause()
                seekTo(0)
            }
            start()
        }
    }

    // 카운트다운 알림음 재생 함수
    private fun playTickSound() {
        tickPlayer?.apply {
            if (isPlaying) {
                pause()
                seekTo(0)
            }
            start()
        }
    }

    private fun showMenu() {
        MenuBottomSheetDialog(
            onEditClicked = {
                // TODO: 타이머 수정 기능이 추가되면 구현 예정
            },
            onDeleteClicked = { showDeleteConfirmationDialog() },
            isEditVisible = false // TODO: 타이머 수정 기능이 추가되면 제거 예정
        ).show(parentFragmentManager, MenuBottomSheetDialog::class.java.simpleName)
    }

    private fun showDeleteConfirmationDialog() {
        BaseAlertDialog(
            title = getString(R.string.custom_timer_delete_alert),
            desc = null,
            onConfirmClicked = {
                // TODO: 커스텀 타이머 삭제
            }
        ).show(parentFragmentManager, BaseAlertDialog::class.java.simpleName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stepFinishPlayer?.release()
        stepFinishPlayer = null
        tickPlayer?.release()
        tickPlayer = null
        progressAnimator?.cancel()
        progressAnimator = null
    }

    companion object {
        const val ITEM_MARGIN = 6
    }
}