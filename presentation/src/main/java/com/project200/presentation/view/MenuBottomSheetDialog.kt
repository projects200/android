package com.project200.presentation.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.BottomSheetDialogMenuBinding

/** 메뉴 바텀 시트 다이얼로그
 * @param onEditClicked 수정 버튼 클릭 시 호출되는 콜백
 * @param onDeleteClicked 삭제 버튼 클릭 시 호출되는 콜백
 */
class MenuBottomSheetDialog(
    val onEditClicked: () -> Unit,
    val onDeleteClicked: () -> Unit,
) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDialogMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDialogMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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