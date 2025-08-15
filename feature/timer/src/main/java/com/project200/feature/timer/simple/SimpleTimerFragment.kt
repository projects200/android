package com.project200.feature.timer.simple

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.project200.domain.model.SimpleTimer
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentSimpleTimerBinding

class SimpleTimerFragment :
    BindingFragment<FragmentSimpleTimerBinding>(R.layout.fragment_simple_timer) {

    // getViewBinding 메서드를 오버라이드하여 ViewBinding을 사용합니다.
    override fun getViewBinding(view: View): FragmentSimpleTimerBinding {
        return FragmentSimpleTimerBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.simple_timer))
            showBackButton(true) { findNavController().navigateUp() }
        }
        initRecyclerView()
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
                    // RecyclerView의 전체 높이를 가져와서 계산
                    val recyclerViewHeight = binding.simpleTimerRv.height - paddingInPixels

                    // 그리드 아이템의 높이
                    binding.simpleTimerRv.adapter = SimpleTimerRVAdapter(
                        items = timerItems,
                        itemHeight = recyclerViewHeight / RV_ITEM_ROW_COUNT,
                        onItemClick = { simpleTimer ->
                        }
                    )
                }
            })
        }

    }

    companion object {
        private const val RV_ITEM_COL_COUNT = 2 // RecyclerView의 열 개수
        private const val RV_ITEM_ROW_COUNT = 3 // RecyclerView의 행 개수
    }
}
