package com.project200.feature.exercise.form

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project200.undabang.feature.exercise.R
// strings.xml에 정의된 R을 사용하기 위해 실제 R 클래스를 import 해야 할 수 있습니다.
// 예: import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.BottomSheetExerciseMenuBinding

class ExerciseMenuBottomSheet(
    val onEditClicked: () -> Unit,
    val onDeleteClicked: () -> Unit
) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExerciseMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetExerciseMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recordEditBtn.setOnClickListener {
            onEditClicked()
            dismiss()
        }

        binding.recordDeleteBtn.setOnClickListener {
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