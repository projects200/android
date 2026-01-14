package com.project200.presentation.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.BottomSheetDialogMenuBinding
import com.project200.undabang.presentation.databinding.BottomSheetDialogSelectBinding

/** 선택 바텀 시트 다이얼로그
 * @param items 선택 항목 리스트
 *
 */
class SelectionBottomSheetDialog(
    private val items: List<String>,
    private val selectedItem: String? = null,
    private val onItemSelected: (String) -> Unit,
) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDialogSelectBinding? = null
    val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetDialogSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // 어댑터 생성 및 연결
        val selectionAdapter = SelectionRVAdapter(items, selectedItem) { item ->
            onItemSelected(item)
            dismiss()
        }

        binding.selectItemRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = selectionAdapter
        }

        binding.closeBtn.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
