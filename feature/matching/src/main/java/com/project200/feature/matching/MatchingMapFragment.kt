package com.project200.feature.matching

import android.view.View
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingMapBinding

class MatchingMapFragment: BindingFragment<FragmentMatchingMapBinding>(R.layout.fragment_matching_map) {
    override fun getViewBinding(view: View): FragmentMatchingMapBinding {
        return FragmentMatchingMapBinding.bind(view)
    }

    override fun setupViews() {

    }
}