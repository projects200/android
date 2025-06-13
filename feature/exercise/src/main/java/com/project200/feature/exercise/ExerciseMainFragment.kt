package com.project200.feature.exercise

import android.content.Context
import android.view.View
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseMainBinding

class ExerciseMainFragment: BindingFragment<FragmentExerciseMainBinding>(R.layout.fragment_exercise_main) {
    private var fragmentNavigator: FragmentNavigator? = null
    override fun getViewBinding(view: View): FragmentExerciseMainBinding {
        return FragmentExerciseMainBinding.bind(view)
    }

    override fun setupViews() {
        binding.root.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseMainToExerciseList()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentNavigator) {
            fragmentNavigator = context
        } else {
            throw ClassCastException("$context must implement FragmentNavigator")
        }
    }


    override fun onDetach() {
        fragmentNavigator = null
        super.onDetach()
    }
}