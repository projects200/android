package com.project200.undabang.profile.mypage

import android.view.View
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentPreferredExerciseDetailBinding

class PreferredExerciseDetailFragment: BindingFragment<FragmentPreferredExerciseDetailBinding>(R.layout.fragment_preferred_exercise_detail) {
    override fun getViewBinding(view: View): FragmentPreferredExerciseDetailBinding {
        return FragmentPreferredExerciseDetailBinding.bind(view)
    }

    override fun setupViews() {

    }

    override fun setupObservers() {

    }
}