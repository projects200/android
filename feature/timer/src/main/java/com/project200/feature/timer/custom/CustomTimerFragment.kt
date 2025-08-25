package com.project200.feature.timer.custom

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.feature.timer.TimerListFragment
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.base.BaseAlertDialog
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.view.MenuBottomSheetDialog
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentCustomTimerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomTimerFragment: BindingFragment<FragmentCustomTimerBinding>(R.layout.fragment_custom_timer) {
    private val viewModel: CustomTimerViewModel by viewModels()
    private val args: CustomTimerFragmentArgs by navArgs()
    private var progressAnimator: ValueAnimator? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var stepRVAdapter: StepRVAdapter

    override fun getViewBinding(view: View): FragmentCustomTimerBinding {
        return FragmentCustomTimerBinding.bind(view)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadTimerData(args.customTimerId)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            showBackButton(true) {
                findNavController().previousBackStackEntry?.savedStateHandle?.set(TimerListFragment.REFRESH_KEY, true)
                findNavController().popBackStack()
            }
            setSubButton(R.drawable.ic_menu, onClick = { showMenu() })
        }

        context?.let {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(it, notificationUri)
        }
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
            viewModel.resetTimer()
            updateUIForTimerEnd()
        }
        binding.timerRepeatBtn.setOnClickListener {
            viewModel.toggleRepeat()
        }
    }

    private fun initRecyclerView() {
        stepRVAdapter = StepRVAdapter()

        binding.customTimerStepRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = stepRVAdapter
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
                }
            }
        }

        // 반복 버튼 UI 상태 업데이트를 위한 Observer
        viewModel.isRepeatEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.timerRepeatBtn.setImageResource(
                if (isEnabled) (R.drawable.ic_repeat) else R.drawable.ic_repeat_off
            )
        }

        viewModel.alarm.observe(viewLifecycleOwner) { shouldPlay ->
            if (shouldPlay) {
                playAlarm()
                viewModel.onAlarmPlayed()
            }
        }

        // 툴바 타이틀 설정
        viewModel.title.observe(viewLifecycleOwner) { title ->
            binding.baseToolbar.setTitle(title)
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