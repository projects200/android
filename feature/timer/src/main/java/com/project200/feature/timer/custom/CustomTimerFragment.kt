package com.project200.feature.timer.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.media.RingtoneManager
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
import timber.log.Timber

@AndroidEntryPoint
class CustomTimerFragment: BindingFragment<FragmentCustomTimerBinding>(R.layout.fragment_custom_timer) {
    private val viewModel: CustomTimerViewModel by viewModels()
    private var progressAnimator: ValueAnimator? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun getViewBinding(view: View): FragmentCustomTimerBinding {
        return FragmentCustomTimerBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            showBackButton(true) { findNavController().navigateUp() }
            setSubButton(R.drawable.ic_menu, onClick = { showMenu() })
        }

        context?.let {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(it, notificationUri)
        }
        initClickListeners()
        initRecyclerView()
        setupObservers()
        updateEndButtonState(viewModel.isTimerFinished.value ?: true)
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
        }
    }

    private fun initRecyclerView() {
        binding.customTimerStepRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            viewModel.steps.observe(viewLifecycleOwner) { steps ->
                adapter = StepRVAdapter(steps)
            }
            addItemDecoration(StepItemDecoration(ITEM_MARGIN))
        }
    }

    override fun setupObservers() {
        viewModel.isTimerRunning.observe(viewLifecycleOwner) { isRunning ->
            updateRunningState(isRunning)
        }

        viewModel.remainingTime.observe(viewLifecycleOwner) { remainingTime ->
            binding.timerTv.text = remainingTime.toFormattedTimeAsLong()
            // 타이머가 실행 중이지 않을 때만(타이머 종료 시) 프로그레스바 업데이트
            if (viewModel.isTimerRunning.value == false) {
                val totalStepTime = viewModel.totalStepTime
                binding.timerProgressbar.progress = if (totalStepTime > 0) remainingTime.toFloat() / totalStepTime.toFloat() else 0f
            }
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
            updateEndButtonState(isFinished)
            if (isFinished && viewModel.isTimerRunning.value == false) {
                updateUIForTimerEnd()
            }
        }

        viewModel.alarm.observe(viewLifecycleOwner) { shouldPlay ->
            if (shouldPlay) {
                playAlarm()
                viewModel.onAlarmPlayed()
            }
        }
    }

    private fun updateRunningState(isRunning: Boolean) {
        if (isRunning) {
            binding.timerPlayBtn.backgroundTintList = ColorStateList.valueOf(
                getColor(requireContext(), com.project200.undabang.presentation.R.color.error_led)
            )
            binding.timerPlayBtn.text = getString(R.string.timer_stop)
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                mediaPlayer?.seekTo(0)
            }
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

    private fun updateEndButtonState(isFinished: Boolean) {
        if (isFinished) {
            binding.timerEndBtn.backgroundTintList = ColorStateList.valueOf(
                getColor(requireContext(), com.project200.undabang.presentation.R.color.gray300)
            )
            binding.timerEndBtn.isClickable = false
        } else {
            binding.timerEndBtn.backgroundTintList = ColorStateList.valueOf(
                getColor(requireContext(), com.project200.undabang.presentation.R.color.error_led)
            )
            binding.timerEndBtn.isClickable = true
        }
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
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        if (view != null) {
                            binding.timerProgressbar.progress = 0f
                        }
                    }
                })
                start()
            }
        }
    }

    private fun playAlarm() {
        mediaPlayer?.apply {
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
        mediaPlayer?.release()
        mediaPlayer = null
        progressAnimator?.cancel()
        progressAnimator = null
    }

    companion object {
        const val ITEM_MARGIN = 6
    }
}