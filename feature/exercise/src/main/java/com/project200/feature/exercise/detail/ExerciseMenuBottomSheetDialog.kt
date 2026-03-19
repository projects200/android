package com.project200.feature.exercise.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project200.undabang.feature.exercise.databinding.BottomSheetExerciseMenuBinding

class ExerciseMenuBottomSheetDialog(
    private val onEditClicked: () -> Unit,
    private val onDeleteClicked: () -> Unit,
) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExerciseMenuBinding? = null
    val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.project200.undabang.presentation.R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetExerciseMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.editBtn.setOnClickListener {
            onEditClicked()
            dismiss()
        }

        binding.deleteBtn.setOnClickListener {
            onDeleteClicked()
            dismiss()
        }

        binding.closeBtn.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
