package com.project200.feature.timer.simple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTimeAsLong
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentSimpleTimerBinding

class SimpleTimerFragment : BindingFragment<FragmentSimpleTimerBinding>(R.layout.fragment_simple_timer) {
    private val viewModel: SimpleTimerViewModel by viewModels()
    private var progressAnimator: ValueAnimator? = null
    private var mediaPlayer: MediaPlayer? = null

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
        initRecyclerView()
        setupObservers()
    }

    private fun initRecyclerView() {
        val timerItems = listOf(
            SimpleTimer("1", 30),
            SimpleTimer("2", 45),
            SimpleTimer("3", 60),
            SimpleTimer("4", 90),
            SimpleTimer("5", 120),
            SimpleTimer("6", 150)
        )

        binding.simpleTimerRv.apply {
            layoutManager = GridLayoutManager(requireContext(), RV_ITEM_COL_COUNT)
            viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.simpleTimerRv.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    val paddingInPixels = resources.getDimensionPixelSize(com.project200.undabang.presentation.R.dimen.base_horizontal_margin)
                    val recyclerViewHeight = binding.simpleTimerRv.height - paddingInPixels

                    binding.simpleTimerRv.adapter = SimpleTimerRVAdapter(
                        items = timerItems,
                        itemHeight = recyclerViewHeight / RV_ITEM_ROW_COUNT,
                        onItemClick = { simpleTimer ->
                            stopAndResetTimer()
                            viewModel.setTimer(simpleTimer.time)
                            viewModel.startTimer()
                        }
                    )
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

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
        progressAnimator?.cancel()
        progressAnimator = null
    }

    companion object {
        private const val RV_ITEM_COL_COUNT = 2
        private const val RV_ITEM_ROW_COUNT = 3
    }
}
