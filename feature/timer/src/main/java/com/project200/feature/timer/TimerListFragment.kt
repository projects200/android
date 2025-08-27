package com.project200.feature.timer

import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.domain.model.CustomTimer
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentTimerListBinding

class TimerListFragment: BindingFragment<FragmentTimerListBinding>(R.layout.fragment_timer_list) {
    private lateinit var customTimerRVAdapter: CustomTimerRVAdapter

    override fun getViewBinding(view: View): FragmentTimerListBinding {
        return FragmentTimerListBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        initClickListeners()
        initRecyclerView()
    }

    private fun initClickListeners() {
        binding.simpleTimerBtn.setOnClickListener {
            findNavController().navigate(
                TimerListFragmentDirections.actionTimerListFragmentToSimpleTimerFragment()
            )
        }
        binding.addCustomTimerBtn.setOnClickListener {

        }
    }

    private fun initRecyclerView() {
        customTimerRVAdapter = CustomTimerRVAdapter { customTimer ->
            findNavController().navigate(
                TimerListFragmentDirections.actionTimerListFragmentToCustomTimerFragment(customTimer.id)
            )
        }

        val dummyData = listOf(
            CustomTimer(1, "타바타 타이머"),
            CustomTimer(2, "휴식 타이머"),
            CustomTimer(3, "운동 세트 타이머")
        )
        customTimerRVAdapter.submitList(dummyData)

        binding.customTimerRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customTimerRVAdapter
        }
    }
}