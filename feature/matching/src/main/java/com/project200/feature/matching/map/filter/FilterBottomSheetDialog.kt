package com.project200.feature.matching.map.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project200.feature.matching.map.MatchingMapViewModel
import com.project200.feature.matching.utils.FilterUiMapper
import com.project200.feature.matching.utils.MatchingFilterType
import com.project200.undabang.feature.matching.databinding.DialogFilterBottomSheetBinding
import com.project200.undabang.presentation.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FilterBottomSheetDialog(
    private val filterType: MatchingFilterType,
    private val onOptionSelected: (Any?) -> Unit,
) : BottomSheetDialogFragment() {
    private var _binding: DialogFilterBottomSheetBinding? = null
    val binding get() = _binding!!

    private val viewModel: MatchingMapViewModel by viewModels({ requireParentFragment() })

    private val adapter by lazy {
        FilterOptionRVAdapter { selectedItem ->
            onOptionSelected(selectedItem)
            if (!filterType.isMultiSelect) dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogFilterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.filterOptionsRv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filterState.collect { state ->
                    adapter.submitList(FilterUiMapper.mapToUiModels(filterType, state))
                }
            }
        }

        binding.closeBtn.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
