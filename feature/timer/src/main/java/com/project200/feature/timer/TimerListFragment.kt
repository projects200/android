package com.project200.feature.timer

import android.view.View
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
    }

    private fun initClickListeners() {
        binding.simpleTimerBtn.setOnClickListener {

        }
        binding.addCustomTimerBtn.setOnClickListener {

        }
    }
}