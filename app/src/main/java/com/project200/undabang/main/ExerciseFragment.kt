package com.project200.undabang.main

import android.view.View
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.R
import com.project200.undabang.databinding.FragmentExerciseBinding
import androidx.navigation.fragment.findNavController

class ExerciseFragment: BindingFragment<FragmentExerciseBinding>(R.layout.fragment_exercise) {
    override fun getViewBinding(view: View): FragmentExerciseBinding {
        return FragmentExerciseBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        btn.setOnClickListener {
            findNavController().navigate(R.id.action_exerciseFragment_to_settingFragment)
        }
    }
}