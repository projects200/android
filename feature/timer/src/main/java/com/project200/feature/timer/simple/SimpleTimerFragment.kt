package com.project200.feature.timer.simple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.project200.feature.timer.TimePickerDialog
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat.getColor
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.MenuStyler
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentSimpleTimerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

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

        context?.let {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(it, notificationUri)
        }

        initClickListeners()
        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        timerAdapter = SimpleTimerRVAdapter(
            onItemClick = { simpleTimer ->
                stopAndResetTimer()
                viewModel.setTimer(simpleTimer.time)
                viewModel.startTimer()
            },
            onMenuClick = { simpleTimer, view -> showPopupMenu(view, simpleTimer) },
            onAddClick = { showTimePickerDialog() }
        )
        binding.simpleTimerRv.apply {
            layoutManager = GridLayoutManager(requireContext(), RV_ITEM_COL_COUNT)
            adapter = timerAdapter
            postponeEnterTransition() // Transition 사용 시 화면 깜빡임 방지

            // RecyclerView의 레이아웃이 완료된 후 높이를 계산
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
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
        viewModel.timerItems.observe(viewLifecycleOwner) { timers ->
            timerAdapter.submitList(timers)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // UI가 STARTED 상태일 때만 수집하고, STOPPED 상태가 되면 중단
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toastMessage.collect { type ->
                    if(type == SimpleTimerToastMessage.GET_ERROR) findNavController()
                    val messageResId = when (type) {
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
                if(newTime <= 0) {
                    Toast.makeText(requireContext(), getString(R.string.invalid_simple_timer_error), Toast.LENGTH_SHORT).show()
                    return@TimePickerDialog
                }

                if (isEditMode) viewModel.updateTimerItem(simpleTimer!!.copy(time = newTime))
                else viewModel.addTimerItem(newTime)
            }
        ).show(parentFragmentManager, TAG)
    }

    private fun showPopupMenu(view: View, simpleTimer: SimpleTimer) {
        val contextWrapper = ContextThemeWrapper(requireContext(), R.style.PopupItemStyle)

        PopupMenu(contextWrapper, view).apply {
            menuInflater.inflate(R.menu.timer_item_menu, this.menu)

            menu.findItem(R.id.action_edit)?.let {
                MenuStyler.applyTextColor(requireContext(), it, android.R.color.black)
            }
            menu.findItem(R.id.action_delete)?.let {
                MenuStyler.applyTextColor(requireContext(), it, com.project200.undabang.presentation.R.color.error_led)
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