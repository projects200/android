package com.project200.feature.matching.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project200.feature.matching.map.cluster.FilterOptionRVAdapter
import com.project200.feature.matching.utils.FilterOptionUiModel
import com.project200.undabang.feature.matching.databinding.DialogFilterBottomSheetBinding
import com.project200.undabang.presentation.R

class FilterBottomSheetDialog (
    private val options: List<FilterOptionUiModel>,
    private val onOptionSelected: (Any) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogFilterBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogFilterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FilterOptionRVAdapter { selectedItem ->
            onOptionSelected(selectedItem)
            dismiss() // TODO: 선택 시 닫기 (다중 선택이면 닫지 않음)
        }

        binding.filterOptionsRv.adapter = adapter
        adapter.submitList(options)

        binding.closeBtn.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}