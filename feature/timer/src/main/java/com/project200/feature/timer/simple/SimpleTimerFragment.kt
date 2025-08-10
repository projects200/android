package com.project200.feature.timer.simple

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.domain.model.SimpleTimer
import com.project200.feature.timer.utils.TimerFormatter.toFormattedTime
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentSimpleTimerBinding
import timber.log.Timber

class SimpleTimerFragment : BindingFragment<FragmentSimpleTimerBinding>(R.layout.fragment_simple_timer) {
    private val viewModel: SimpleTimerViewModel by viewModels()
    private var progressAnimator: ValueAnimator? = null

    override fun getViewBinding(view: View): FragmentSimpleTimerBinding {
        return FragmentSimpleTimerBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.simple_timer))
            showBackButton(true) { findNavController().navigateUp() }
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
                            // 프로그레스바 초기화
                            progressAnimator?.cancel()
                            progressAnimator = null
                            binding.timerProgressbar.progress = 1.0f

                            viewModel.setTimer(simpleTimer.time)
                            viewModel.startTimer()
                        }
                    )
                }
            })
        }
    }

    override fun setupObservers() {
        // 남은 시간 LiveData를 관찰하여 UI 업데이트
        viewModel.remainingTime.observe(viewLifecycleOwner) { remainingTime ->
            binding.timerTv.text = remainingTime.toFormattedTime()

            // 타이머가 종료되었을 때, 프로그레스바를 0으로 설정하고 애니메이터를 취소
            if (remainingTime <= 0) {
                binding.timerProgressbar.progress = 0f
                progressAnimator?.cancel()
                progressAnimator = null
            }
        }

        // 타이머 진행 상태 LiveData를 관찰하여 버튼 아이콘 변경 및 애니메이터 제어
        viewModel.isTimerRunning.observe(viewLifecycleOwner) { isRunning ->
            if (isRunning) {
                binding.timerBtn.setImageResource(R.drawable.ic_stop)
                val totalTime = viewModel.totalTime
                val remainingTime = viewModel.remainingTime.value ?: 0

                // 애니메이터가 null이거나 아직 시작되지 않았을 때만 새로 생성
                if (progressAnimator == null || !progressAnimator!!.isStarted) {
                    progressAnimator?.cancel() // 혹시 모를 이전 애니메이터 정리

                    progressAnimator = ValueAnimator.ofFloat(remainingTime.toFloat() / totalTime.toFloat(), 0f).apply {
                        duration = remainingTime.toLong() * 1000
                        interpolator = LinearInterpolator()
                        addUpdateListener { animator ->
                            binding.timerProgressbar.progress = animator.animatedValue as Float
                        }
                        start()
                    }
                } else {
                    // 이미 시작된 애니메이터는 재개
                    progressAnimator?.resume()
                }

            } else {
                binding.timerBtn.setImageResource(R.drawable.ic_play)
                // 애니메이터 일시 정지
                progressAnimator?.pause()
            }
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

    companion object {
        private const val RV_ITEM_COL_COUNT = 2
        private const val RV_ITEM_ROW_COUNT = 3
    }
}