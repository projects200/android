package com.project200.feature.exercise

import android.content.Context
import android.view.View
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.navigator.FragmentNavigator
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.FragmentExerciseListBinding

class ExerciseListFragment: BindingFragment<FragmentExerciseListBinding>(R.layout.fragment_exercise_list) {
    private var fragmentNavigator: FragmentNavigator? = null

    override fun getViewBinding(view: View): FragmentExerciseListBinding {
        return FragmentExerciseListBinding.bind(view)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentNavigator) {
            fragmentNavigator = context
        } else {
            throw ClassCastException("$context must implement FragmentNavigator")
        }
    }

    override fun setupViews() = with(binding) {
        btn.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseListToExerciseDetail(1)
        }
        btn2.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseListToSetting()
        }
        btn3.setOnClickListener {
            fragmentNavigator?.navigateFromExerciseListToExerciseForm()
        }
    }
}