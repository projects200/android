package com.project200.feature.timer.simple

import android.view.View
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.FragmentSimpleTimerBinding

class SimpleTimerFragment: BindingFragment<FragmentSimpleTimerBinding>(R.layout.fragment_simple_timer) {
    override fun getViewBinding(view: View): FragmentSimpleTimerBinding {
        return FragmentSimpleTimerBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()
    }
}