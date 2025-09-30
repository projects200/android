package com.project200.feature.matching.map

import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingGuideBinding

class MatchingGuideFragment: BindingFragment<FragmentMatchingGuideBinding> (R.layout.fragment_matching_guide) {
    override fun getViewBinding(view: android.view.View): FragmentMatchingGuideBinding {
        return FragmentMatchingGuideBinding.bind(view)
    }

    override fun setupViews() {
        binding.registerBtn.setOnClickListener {
            findNavController().navigate(
                MatchingGuideFragmentDirections.actionMatchingGuideFragmentToExercisePlaceSearchFragment()
            )
        }
    }
}